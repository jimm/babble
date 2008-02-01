// JSFileLibrary.java

package ed.appserver;

import java.io.*;
import java.util.*;

import ed.js.*;
import ed.js.func.*;
import ed.js.engine.*;
import ed.appserver.jxp.*;

public class JSFileLibrary extends JSObjectBase {
    
    public JSFileLibrary( File base , String uriBase , AppContext context ){
        this( base , uriBase , context , null , uriBase.equals( "core" ) );
    }

    public JSFileLibrary( File base , String uriBase , Scope scope ){
        this( base , uriBase , null , scope , uriBase.equals( "core" ) );
    }

    public JSFileLibrary( File base , String uriBase , AppContext context , Scope scope , boolean doInit ){
        _base = base;
        _uriBase = uriBase;
        _context = context;
        _scope = scope;
        _didInit = doInit;
        if ( _didInit ){
            Object foo = get( "_init" );
            if ( foo instanceof JSFunction ){
                Scope s = null;
                if ( context != null )
                    s = context.getScope();
                else if ( scope != null )
                    s = scope;
                else 
                    throw new RuntimeException( "no scope :(" );
                ((JSFunction)foo).call( s );
            }
        }
    }
    
    public Object get( final Object n ){
        
        Object foo = _get( n );
        if ( foo instanceof JxpSource ){
            try {
                JSFunction func = ((JxpSource)foo).getFunction();
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
            return set( n , new JSFileLibrary( dir , _uriBase + "." + n.toString() , _context , _scope , _didInit ) );
        
        if ( f == null )
            return null;

        try {
            return set( n , getSource( f ) );
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
    final boolean _didInit;
    private final Map<File,JxpSource> _sources = new HashMap<File,JxpSource>();
    
}
