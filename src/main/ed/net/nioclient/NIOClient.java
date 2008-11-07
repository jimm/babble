// NIOClient.java

/**
*    Copyright (C) 2008 10gen Inc.
*  
*    This program is free software: you can redistribute it and/or  modify
*    it under the terms of the GNU Affero General Public License, version 3,
*    as published by the Free Software Foundation.
*  
*    This program is distributed in the hope that it will be useful,
*    but WITHOUT ANY WARRANTY; without even the implied warranty of
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*    GNU Affero General Public License for more details.
*  
*    You should have received a copy of the GNU Affero General Public License
*    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package ed.net.nioclient;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;

import ed.io.*;
import ed.log.*;
import ed.util.*;
import ed.net.*;
import ed.net.httpserver.*;
import static ed.net.HttpExceptions.*;

public abstract class NIOClient extends Thread {

    public enum ServerErrorType { WEIRD , INVALID , CONNECT , SOCK_TIMEOUT };

    protected enum WhatToDo { CONTINUE , PAUSE , DONE_AND_CLOSE , DONE_AND_CONTINUE , ERROR , CLIENT_ERROR };

    public static final SimpleDateFormat SHORT_TIME = new SimpleDateFormat( "MM/dd HH:mm:ss.S" );
    static final long AFTER_SHUTDOWN_WAIT = 1000 * 60;
    static final long CONNECT_TIMEOUT = 1000 * 30; // timeout for opening a socket to a server
    static final long CLIENT_CONNECT_WAIT_TIMEOUT = 1000 * 15;
    static final long CONN_TIMEOUT = 1000 * 60 * 4; // timeout for idle connections to server

    public NIOClient( String name , int connectionsPerHost , int verboseLevel ){
        super( "NIOClient: " + name );
        _name = name;
        _connectionsPerHost = connectionsPerHost;
        
        _logger = Logger.getLogger( "nioclient-" + name );
	_logger.setLevel( Level.forDebugId( verboseLevel ) );

        _loggerOpen = _logger.getChild( "open" );
        _loggerDrop = _logger.getChild( "drop" );
        _loggerLostConnection = _logger.getChild( "lost-connection" );
        
        try {
            _selector = Selector.open();
        }
        catch ( IOException ioe ){
            throw new RuntimeException( "can't open selector" , ioe );
        }

        _addMonitors();
    }
    
    protected abstract void serverError( InetSocketAddress addr , ServerErrorType type , Exception why );

    protected void shutdown(){
        _shutdown = true;
        _shutdownTime = System.currentTimeMillis();
        _logger.error( "SHUTDOWN RECEIVED" );
    }
    
    public void run(){
        while ( true ){
            try {
                _run();
            }
            catch ( Exception e ){
                _logger.error( "error in run loop" , e );
            }

            if ( _shutdown && ( System.currentTimeMillis() - _shutdownTime ) > AFTER_SHUTDOWN_WAIT )
                break;
            
        }
    }
    
    public boolean add( Call c ){
        return _newRequests.offer( c );
    }
    
    private void _run(){
        _doNewRequests();
        _doOldStuff();
        _checkForTimedOutStuff();
    }
    
    private void _doOldStuff(){
        int numKeys = 0;
        try {
            numKeys = _selector.select( 10 );                
        }
        catch ( IOException ioe ){
            _logger.error( "can't select" , ioe );
        }
        
        if ( numKeys <= 0 )
            return;
        
        final Iterator<SelectionKey> i = _selector.selectedKeys().iterator();
        while ( i.hasNext() ){
            SelectionKey key = i.next();
            i.remove();
            
            if ( ! key.isValid() )
                continue;
            
            Connection c = (Connection)key.attachment();
            
            if ( c == null ){
                _logger.error( "attachment was null " );
                continue;
            }                

            if ( key.isConnectable() )
                c.handleConnect();
            else if ( key.isReadable() )
                c.handleRead();
            else if ( key.isWritable() )
                c.handleWrite();
        }
        
    }
    
    private void _doNewRequests(){
        long now = System.currentTimeMillis();
        
        List<Call> pushBach = new LinkedList<Call>();
        
        for ( int i=0; i<20; i++ ){ // don't want to just handle new requests

            Call c = _newRequests.poll();
            if ( c == null )
                break;

            if ( now - c._started > CLIENT_CONNECT_WAIT_TIMEOUT ){
                c.error( ServerErrorType.CONNECT , new IOException( "request timed out waiting for a connection (lb 51)" ) );
                continue;
            }

            if ( c.isCancelled() ){
		pushBach.add( c );
                continue;
	    }
            
            if ( c.isPaused() ){
		pushBach.add( c );
                continue;
	    }

            InetSocketAddress addr = null;
            try {
                addr = c.where();
		_logger.debug( 2 , "address" , c , addr );

                if ( addr == null ){
		    pushBach.add( c );
                    continue;
		}
                
                final ConnectionPool pool = getConnectionPool( addr );
                
                Connection conn = pool.get( 0 );
                if ( conn == null ){
                    pushBach.add( c );
                    continue;
                }
                
                if ( ! conn.ready() ){
                    pushBach.add( c );
                    pool.done( conn );
                    continue;
                }

                conn.start( c );

            }
            catch ( CantOpen co ){
                _logger.error( "couldn't open" , co );
                c.error( ServerErrorType.CONNECT , co );
                if ( addr != null )
                    serverError( addr , ServerErrorType.CONNECT , co.getIOException() );
            }
            catch ( RuntimeException re ){
                _logger.error( "runtime exception in _doNewRequests" , re );
                c.error( ServerErrorType.WEIRD , re );
            }
                
        }
        
        for ( Call c : pushBach ){
            if ( ! _newRequests.offer( c ) ){
                _loggerDrop.error( "couldn't push something back on to queue." );
            }
        }
    }
    
    private void _checkForTimedOutStuff(){
        List<ConnectionPool> pools = new LinkedList<ConnectionPool>( _connectionPools.values() );
        
        for ( ConnectionPool pool : pools )
            for ( Iterator<Connection> i = pool.getAll() ; i.hasNext(); )
                i.next().checkForTimeOut();
        
    }
    
    public boolean isShutDown(){
        return _shutdown;
    }

    public ConnectionPool getConnectionPool( InetSocketAddress addr ){
        ConnectionPool p = _connectionPools.get( addr );
        if ( p != null )
            return p;

        p = new ConnectionPool( addr );
        _connectionPools.put( addr , p );
        
        return p;
    }

    public List<InetSocketAddress> getAllConnections(){
        return new LinkedList<InetSocketAddress>( _connectionPools.keySet() );
    }

    protected class Connection {
        
        Connection( ConnectionPool pool , InetSocketAddress addr ){
            _pool = pool;
            _addr = addr;
            _myLostConnectionLogger = _loggerLostConnection.getChild( _addr.toString() );
            try {
                _sock = SocketChannel.open();
                _sock.configureBlocking( false );
                _sock.connect( _addr );
                _key = _sock.register( _selector , SelectionKey.OP_CONNECT , this );
                
                _loggerOpen.debug( "opening connection to [" + addr + "]" );
            }
            catch ( UnresolvedAddressException e ){
                _error = new UnknownHostException( addr.toString() );
                throw new CantOpen( addr , _error );
            }
            catch ( IOException ioe ){
                _error = ioe;
                throw new CantOpen( addr , ioe );
            }
        }
        
        void handleConnect(){
            _event();
            IOException err = null;
            try {
                if ( ! _sock.finishConnect() ){
                    err = new IOException( "finishConnect faild silently" );
                    err.fillInStackTrace();
                }
            }
            catch ( IOException ioe ){
                err = ioe;
            }
            catch ( Exception e ){
                err = new IOException( "weird error on finish connect : " + e );
            }
            
            if ( err == null ){
                _loggerOpen.debug( "done opening connection to [" + _addr + "]" );
                _ready = true;
                return;
            }

            _error = err;            
            serverError( _addr , ServerErrorType.CONNECT , err );
            _loggerOpen.error( "error opening connection to [" + _addr + "] (" + this.hashCode() + ")" , _error );            
        }
        
        boolean ready(){
            return _ready;
        }
        
        boolean ok(){
            if ( _error != null ){
                _myLostConnectionLogger.info( "error" );
                return false;
            }

            if ( _closed ){
                _myLostConnectionLogger.debug( "closed " );
                return false;
            }
            
            if ( ! _ready && System.currentTimeMillis() - _opened > CONNECT_TIMEOUT ){
                _myLostConnectionLogger.info( "connect timeout" );
                return false;
            }
            
            if ( _current != null && _current.isDone() ){
                _myLostConnectionLogger.info( "have call but its done" );
                return false;
            }
            
            return true;
        }
        
        public int doRead( boolean errorOnEOF ){
            _fromServer.position( 0 );
            _fromServer.limit( _fromServer.capacity() );
            
            int read = 0;

            try {
                read = _sock.read( _fromServer );
                if ( read > 0 )
                    _event();
            }
            catch ( IOException ioe ){
                _error( ServerErrorType.SOCK_TIMEOUT , ioe );
                return -1;
            }
            
            if ( read < 0 ){
                if ( errorOnEOF )
                    _error( ServerErrorType.SOCK_TIMEOUT ,new IOException( "socket dead" ) );
                done( true );
                return -1;
            }
            
            if ( read != _fromServer.position() )
                throw new RuntimeException( "i'm confused  says i read [" + read + "] but at position [" + _fromServer.position() + "]" );
            
            _fromServer.flip();

            return read;
        }

        void handleRead(){
            // read data from wire
            // pass data to Call
            // response could be
            //   - continue
            //   - pause - turn off selector
            //   - done - add yourself back to the pool
            //   - error, close connection
            
            if ( doRead( _current != null ) < 0 )
                return;
            
            WhatToDo next = null;
            if ( _current == null ){
                _logger.error( " _current is null in handleRead, should never happen" );
                next = WhatToDo.DONE_AND_CLOSE;
            }
            else {
                next = _current.handleRead( _fromServer , this );
            }
            
            switch ( next ){
            case CONTINUE: 
                _key.interestOps( _key.OP_READ );
                return;
            case PAUSE: 
                _key.interestOps( 0 );
                return;
            case ERROR:
                _userError( "Call.handleRead returned ERROR" );
                return;
            case CLIENT_ERROR:
                _error = new IOException( "downstream error so closing" );
                done( true );
                return;
            case DONE_AND_CLOSE:
                done( true );
                return;
            case DONE_AND_CONTINUE:
                done( false );
                return;
            }
        }
        
        public void done( boolean close ){

            _current = null;

            if ( close )
                _close( true );
            else 
                _putBackInPool();
        }

        void handleWrite(){
            int wrote = 0;
            try {
                wrote = _sock.write( _toServer );
                if ( wrote > 0 )
                    _event();
            }
            catch ( IOException ioe ){
                _error( ServerErrorType.SOCK_TIMEOUT , ioe );
                _key.interestOps( 0 );
            }
            
            if ( _toServer.position() == _toServer.limit() ){
		
		if ( _extraDataToServer != null && _extraDataToServer.hasMore() ){
		    _toServer.position(0);
		    _toServer.limit( _toServer.capacity() );
		    _extraDataToServer.write( _toServer );
		    _toServer.flip();
		    handleWrite();
		    return;
		}

		_extraDataToServer = null;
                _key.interestOps( _key.OP_READ );
                _logger.debug( 3 , "finished writing" );
                return;
            }
            
            if ( wrote < 0 ){
                _error( ServerErrorType.SOCK_TIMEOUT , new IOException( "wrote 0 bytes" ) );
                return;
            }

            // need to write more
            _key.interestOps( _key.OP_WRITE );
        }

        void start( Call c ){
            if ( c == null ){
                _userError( "shouldn't call start with a null Call" );
                return;
            }
            
            if ( _current != null ){
                _userError( "trying to start a Call but already have one" );
                return;
            }
            
            _current = c;
            _event();

            _toServer.position( 0 );
            _toServer.limit( _toServer.capacity() );
            
            _extraDataToServer = _current.fillInRequest( _toServer );
            if ( _toServer.position() == 0 ){
                _userError( "fillInRequest didn't give me any data" );
                return;
            }
            
            _toServer.flip();
            
            handleWrite();
        }
        
        public void userError( String msg ){
            _error( ServerErrorType.WEIRD , new ClientError( msg ) );
        }

        private void _userError( String msg ){
            _userError( msg , true );
        }
        
        private void _userError( String msg , boolean shouldThrow ){
            _error( ServerErrorType.WEIRD , new IOException( "User Error : " + msg ) );
            if ( shouldThrow )
                throw new RuntimeException( msg );
        }

        private void _error( ServerErrorType type , IOException e ){
            _error = e;
            if ( _current != null )
                _current.error( type , e );

            if ( _ready )
                _close( true );

        }

        public String toString(){
            return _addr.toString();
        }

        public String statusString(){
            StringBuilder buf = new StringBuilder();
            buf.append( "ready:" ).append( _ready ).append( " " );
            buf.append( "error:" ).append( _error ).append( " " );
            buf.append( "closed:" ).append( _closed ).append( " " );
            buf.append( "has call:").append( _current != null ).append( " " );
            
            if ( _current != null ){
                int ops = _key.interestOps();
                buf.append( "waiting for read:" ).append( ( ops & _key.OP_READ ) > 0 ).append( " " );
                buf.append( "waiting for write:" ).append( ( ops & _key.OP_WRITE ) > 0 ).append( " " );
            }
            
            return buf.toString();
        }
        
        void _putBackInPool(){
            _pool.done( this );
            _logger.debug( 2 , "putting connection back in pool" );
        }

        void _close( boolean putBackInBool ){

            if ( _closed )
                return;
            
            _closed = true;
            
            if ( putBackInBool )
                _putBackInPool();

            try {
                _key.interestOps( 0 );
                _key.attach( null );
                _key.cancel();
                _sock.close();
            }
            catch ( IOException ioe ){
                // don't care
            }
        }

        void _event(){
            _lastEvent = System.currentTimeMillis();
        }
        
        void checkForTimeOut(){
            if ( _closed )
                return;
                    
            if ( System.currentTimeMillis() - _lastEvent < CONN_TIMEOUT )
                return;

            _close( true );
        }

        final ConnectionPool _pool;
        final InetSocketAddress _addr;
        final long _opened = System.currentTimeMillis();
        final Logger _myLostConnectionLogger ;

        final ByteBuffer _toServer = ByteBuffer.allocateDirect( 1024 * 32 );
        final ByteBuffer _fromServer = ByteBuffer.allocateDirect( 1024 * 32 );
	private ByteStream _extraDataToServer = null;

        private final SocketChannel _sock;
        private final SelectionKey _key;  
        
        private boolean _ready = false;
        private IOException _error = null;
        private boolean _closed = false;
        
        private Call _current = null;
        
        private long _lastEvent = _opened;
    } // end of Connection
    
    class ConnectionPool extends SimplePool<Connection> {
        ConnectionPool( InetSocketAddress addr ){
            super( "ConnectionPool : " + addr , _connectionsPerHost , _connectionsPerHost / 3 );
            _addr = addr;
        }

        protected Connection createNew(){
            return new Connection( this , _addr );
        }
        
        public boolean ok( Connection c ){
            return c.ok();
        }
        
        final InetSocketAddress _addr;
    }
    
    
    protected abstract class MyMonitor extends HttpMonitor {
        protected MyMonitor( String name ){
            super( _name + "-" + name );
        }
        
    }

    void _addMonitors(){
        
        for ( MyMonitor m : _previousMonitors )
            HttpServer.removeGlobalHandler( m );
        _previousMonitors.clear();

        MyMonitor m = new MyMonitor( "serverConnPools" ){
                public void handle( MonitorRequest mr ){
		    
		    JxpWriter out = mr.getWriter();
                    
                    out.print( "<ul>" );
                    
                    for ( InetSocketAddress addr : getAllConnections() ){
                        out.print( "<li>" );
                        
                        out.print( "<b>"  );
                        out.print( addr.toString() );
                        out.print( "</b>   " );

                        ConnectionPool pool = getConnectionPool( addr );

                        out.print( "total: " );
                        out.print( pool.total() );
                        out.print( "   " );
                        
                        out.print( "inUse: " );
                        out.print( pool.inUse() );
                        out.print( "   " );
                        
                        out.print( "everCreated: " );
                        out.print( pool.everCreated() );
                        out.print( "   " );
                        
                        if ( mr.getRequest().getBoolean( "detail" , false ) ){
                            out.print( "<ul>" );
                            for ( Iterator<Connection> i = pool.getAll() ; i.hasNext(); ){
                                Connection c = i.next();
                                out.print( "<li>" );                                
                                out.print( c.statusString() );
                                out.print( "</li>" );                                
                            }
                            out.print( "</ul>" );
                        }
                        
                        out.print( "</li>" );
                    }
                    
                    out.print( "</ul>" );
                }
            };
        
        HttpServer.addGlobalHandler( m );
        _previousMonitors.add( m );
    }

    final protected String _name;
    final protected int _connectionsPerHost;
    private boolean _shutdown = false;
    private long _shutdownTime = 0;

    final Logger _logger;
    final Logger _loggerOpen;
    final Logger _loggerDrop;
    final Logger _loggerLostConnection;

    private Selector _selector;
    private final BlockingQueue<Call> _newRequests = new ArrayBlockingQueue<Call>( 1000 );
    private final Map<InetSocketAddress,ConnectionPool> _connectionPools = new HashMap<InetSocketAddress,ConnectionPool>();
    
    private static final List<MyMonitor> _previousMonitors = new ArrayList<MyMonitor>();
}
