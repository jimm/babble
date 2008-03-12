// JSFileLibrary.java

package ed.appserver;

import java.io.*;
import java.util.*;

import ed.js.*;
import ed.js.func.*;
import ed.js.engine.*;
import ed.appserver.jxp.*;

public class JSFileLibrary extends JSObjectBase {

    static final boolean D = false;
    
    public JSFileLibrary( File base , String uriBase , AppContext context ){
        this( base , uriBase , context , null , false );
    }
    
    public JSFileLibrary( File base , String uriBase , Scope scope ){
        this( base , uriBase , null , scope , false );
    }
    
    protected JSFileLibrary( File base , String uriBase , AppContext context , Scope scope , boolean doInit ){

        if ( uriBase.equals( "core" ) && ! doInit )
            throw new RuntimeException( "you are stupid" );

        _base = base;
        _uriBase = uriBase;
        _context = context;
        _scope = scope;
        _doInit = doInit;
    }
    
    private synchronized void _init(){

        if ( D ) System.out.println( "\t " + _base + " _init" );

        if ( ! _doInit )
            return;
        
        if ( _inInit )
            return;

        boolean somethingChanged = false;

        Object init = get( "_init" , false );
        if ( init != _initFunction )
            somethingChanged = true;
        else {
            for ( JxpSource source : _initSources ){
                if ( source.lastUpdated() > _lastInit ){
                    somethingChanged = true;
                    break;
                }
            }
        }
        
        if ( D ) System.out.println( "\t\t somethingChanged : " + somethingChanged + " init : " + init + " _initFunction : " + _initFunction );
        
        if ( ! somethingChanged )
            return;
       
        try {
            _inInit = true;
            
            for ( String s : new LinkedList<String>( keySet() ) ){
                if ( s.equals( "_init" ) )
                    continue;
                
                Object thing = super.get( s );
                
                if ( thing instanceof JxpSource || 
                     thing instanceof JSFileLibrary  )
                    removeField( s );
            }
            
            for ( File f : new LinkedList<File>( _sources.keySet() ) ){
                if ( f.toString().endsWith( "/_init.js" ) )
                    continue;
                _sources.remove( f );
            }
            
            if ( init instanceof JSFunction ){
                Scope s = null;
                if ( _context != null )
                    s = _context.getScope();
                else if ( _scope != null )
                    s = _scope;
                else 
                    throw new RuntimeException( "no scope :(" );
                
                _initFunction = (JSFunction)init;
                
                Scope pref = s.getTLPreferred();
                s.setTLPreferred( null );
                try {
                    _initFunction.call( s );
                }
                catch ( RuntimeException re ){
                    set( "_init" , false ); // we need to re-ren
                    throw re;
                }
                s.setTLPreferred( pref );
            }

            _lastInit = System.currentTimeMillis();
        }
        finally {
            _inInit = false;
        }
        
    }

    public Object get( final Object n ){
        return get( n , true );
    }
    
    public Object get( final Object n , final boolean doInit ){
        if ( doInit )
            _init();

        Object foo = _get( n );
        if ( foo instanceof JxpSource ){
            JxpSource source = (JxpSource)foo;
            if ( _inInit )
                _initSources.add( source );

            try {
                JSFunction func = source.getFunction();
                func.setName( _uriBase + "." + n.toString() );
                foo = func;
            }
            catch ( IOException ioe ){
                throw new RuntimeException( ioe );
            }
        }
        return foo;
    }
    
    public boolean isIn( File f ){
        // TODO make less slow
        return f.toString().startsWith( _base.toString() );
    }

    JxpSource getSource( File f )
        throws IOException {
        return getSource( f , true );
    }

    private JxpSource getSource( File f , boolean doInit )
        throws IOException {
        
        if ( D ) System.out.println( "getSource.  base : " + _base + " file : " + f  + " doInit : " + doInit );
        
        String parentString = f.getParent();
        String rootString = _base.toString();
        if ( ! parentString.equals( rootString ) ){

            if ( ! parentString.startsWith( rootString ) )
                throw new RuntimeException( "[" + f.getParent() + "] not a subdir if [" + _base + "]" );
            
            String follow = parentString.substring( rootString.length() );
            while ( follow.startsWith( "/" ) )
                follow = follow.substring( 1 );

            int idx = follow.indexOf( "/" );            
            String dir = idx < 0 ? follow : follow.substring( 0 , idx );

            JSFileLibrary next = (JSFileLibrary)get( dir );
            return next.getSource( f );
        }

        if ( doInit ) _init();
        
        if ( _context != null )
            _context.loadedFile( f );

        JxpSource source = _sources.get( f );
        if ( source == null ){
            source = JxpSource.getSource( f );
            _sources.put( f , source );
        }
        return source;

    }
    
    Object _get( final Object n ){
        Object v = super.get( n );
        if ( v != null )
            return v;
        
        if ( ! ( n instanceof JSString ) && 
             ! ( n instanceof String ) )
            return null;
        
        File dir = new File( _base , n.toString() );
        File f = null;
        for ( int i=0; i<_srcExtensions.length; i++ ){
            File temp = new File( _base , n + _srcExtensions[i] );

            if ( ! temp.exists() )
                continue;
            
            if ( dir.exists() || f != null )
                throw new RuntimeException( "file collision on : " + dir + " " + _base + " " + n  );

            f = temp;
        }
        
        if ( dir.exists() )
            return set( n , new JSFileLibrary( dir , _uriBase + "." + n.toString() , _context , _scope , _doInit ) );
        
        if ( f == null )
            return null;

        try {
            return set( n , getSource( f , false ) );
        }
        catch ( IOException ioe ){
            throw new RuntimeException( ioe );
        }
        
    }
    static String _srcExtensions[] = new String[] { ".js" , ".jxp" , ".html" };
    
    public void fix( Throwable t ){
        for ( JxpSource s : _sources.values() )
            s.fix( t );

        for ( String s : keySet() ){
            Object foo = get( s );
            if ( foo instanceof JSFileLibrary )
                ((JSFileLibrary)foo).fix( t );
        }
    }
    
    final File _base;
    final String _uriBase;
    final AppContext _context;
    final Scope _scope;
    final boolean _doInit;
    
    private final Map<File,JxpSource> _sources = new HashMap<File,JxpSource>();

    private JSFunction _initFunction;
    private boolean _inInit = false;
    private long _lastInit = 0;
    private final Set<JxpSource> _initSources = new HashSet<JxpSource>();
    
    
}
