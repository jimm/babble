// Replay.java

package ed.net.httpserver;

import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;
import java.util.regex.*;
import java.util.concurrent.*;

import ed.log.*;

public class Replay {

    static Logger _log = Logger.getLogger( "ed.net.httpserver.Replay" );
    static {
        _log.setLevel( Level.INFO );
    }

    public Replay( String server ){
        this( server , 80 );
    }

    public Replay( String server , int port ){
        this( server , port , server );
    }

    public Replay( String server , int port , String hostname ){
        _server = server;
        _port = port;
        _hostname = hostname;
        _hostHeader = "Host: " + _hostname + "\n";

        _addr = new InetSocketAddress( _server , _port );
        _toSend = new LinkedBlockingQueue<HttpRequest>( 100 );
        
        _connectionMatcher = Pattern.compile( "connection:[^\n]+\n" , Pattern.CASE_INSENSITIVE ).matcher( "" );
        _hostMatcher = Pattern.compile( "host:[^\n]+\n" , Pattern.CASE_INSENSITIVE ).matcher( "" );

        _runner = new Runner();
        _runner.start();
        
        _log.debug( "created replay server to : " + _addr );
    }
    
    public void send( HttpRequest request ){
        _log.debug( "adding request" );
        boolean added = _toSend.offer( request );
        if ( ! added && Math.random() > .99 )
            _log.info( "dropping request" );
    }
    
    String getHeaders( HttpRequest request ){
        return fixHeaders( request.getRawHeader() );
    }

    String fixHeaders( String headers ){

        headers = headers.trim() + "\n";

        headers = _connectionMatcher.reset( headers ).replaceAll( "Connection: close\n" );
        headers = _hostMatcher.reset( headers ).replaceAll( _hostHeader );
        
        if ( ! headers.contains( "Connection: " ) )
            headers += "Connection: close\n";
        
        if ( ! headers.contains( "Host: " ) )
            headers += _hostHeader;

        headers += "X-Replay: y\n";

        return headers + "\n";
    }

    private String _send( HttpRequest request )
        throws IOException {
        
        if ( request.getHeader( "X-Replay" ) != null ){
            _log.info( "replay loop" );
            return null;
        }

        _log.debug( "going to send request" );
        
        final String headerString = getHeaders( request );
        
        byte headers[] = headerString.getBytes();
        PostData pd = request.getPostData();
        
        final int length = headers.length + ( pd == null ? 0 : pd.length() ) + 100; // 100 is for padding
        final boolean useBuffer = length < _smallBuffer.capacity();
        
        ByteBuffer bb = null;
        if ( useBuffer ){
            bb = _smallBuffer;
            bb.position( 0 );
            bb.limit( bb.capacity() );
        }
        else {
            bb = ByteBuffer.allocateDirect( length );
        }
        
        bb.put( headers );

        bb.flip();
        
        SocketChannel sock = SocketChannel.open();
        sock.connect( _addr );
        int written = sock.write( bb );

        String firstChunk = null;

        while ( true ){
            _readBuffer.position( 0 );
            _readBuffer.limit( _readBuffer.capacity() );
            
            if ( sock.read( _readBuffer ) <= 0 )
                break;

            if ( firstChunk == null ){
                _readBuffer.flip();
                byte buf[] = new byte[ Math.min( 100 , _readBuffer.limit() ) ];
                _readBuffer.get( buf );
                firstChunk = new String( buf );
            }
        }
        
        return firstChunk;
    }

    class Runner extends Thread {
        Runner(){
            super( "Replay-Runner : " + _addr );
            setDaemon( true );
        }
        
        public void run(){
            _log.debug( "starting replay server" );
            while ( true ){
                try {
                    HttpRequest request = _toSend.take();
                    if ( request == null )
                        continue;

                    _log.debug( "got request from queue" );
                    
                    String firstChunk = _send( request );
		    if ( firstChunk == null )
			continue;
                    if ( firstChunk.startsWith( "5" ) )
                        _log.info( "error on : " + request.getURL() + "\n" + firstChunk );
                    _log.debug( firstChunk );
                }
                catch ( Throwable t ){
                    _log.info( "run error" , t );
                }
            }
        }
        
    }

    final String _server;
    final int _port;
    final String _hostname;
    final String _hostHeader;

    final InetSocketAddress _addr;
    final BlockingQueue<HttpRequest> _toSend;

    final Matcher _connectionMatcher;
    final Matcher _hostMatcher;

    final ByteBuffer _smallBuffer = ByteBuffer.allocateDirect( 1024 * 16 );
    final ByteBuffer _readBuffer = ByteBuffer.allocateDirect( 1024 * 4 );

    final Thread _runner;
}
