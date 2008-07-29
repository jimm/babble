// JxpServlet.java

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

package ed.appserver.jxp;

import java.io.*;
import java.util.regex.*;

import ed.js.*;
import ed.util.*;
import ed.js.engine.*;
import ed.js.func.*;
import ed.lang.*;

import ed.appserver.*;
import ed.net.httpserver.*;

public class JxpServlet {
    
    public static final int MAX_WRITTEN_LENGTH = 1024 * 1024 * 15;
    public static final boolean NOCDN = Config.get().getBoolean( "NO-CDN" );

    JxpServlet( AppContext context , JxpSource source , JSFunction func ){
        _context = context;
        _source = source;
        _theFunction = func;
    }

    public void handle( HttpRequest request , HttpResponse response , AppRequest ar ){
        
        final Scope scope = ar.getScope();

        if ( scope.get( "request" ) == null )
            scope.put( "request" , request , true );
        if ( scope.get( "response" ) == null )
            scope.put( "response" , response , true );

        Object cdnFromScope = scope.get( "CDN" );
             
        final String cdnPrefix = cdnFromScope != null ? cdnFromScope.toString() : getStaticPrefix( request , ar );
        scope.put( "CDN" , cdnPrefix , true );
        
        final String cdnSuffix = getStaticSuffix( request , ar , cdnPrefix );

        MyWriter writer = new MyWriter( response.getWriter() , cdnPrefix , cdnSuffix , ar.getContext() , ar);
        scope.put( "print" , writer  , true );
        
        try {
            _theFunction.call( scope );
            
            if ( writer._writer.hasSpot() ){
                writer._writer.backToSpot();
                
                if ( ar.getContext() != null )
                    for ( Object foo : ar.getContext().getGlobalHead() ) {
                        writer.print( foo.toString() );
                        writer.print( "\n" );
                    }
                
                if ( ar != null )
                    for ( Object foo : ar.getHeadToPrint() ) {
                        writer.print( foo.toString() );
                        writer.print( "\n" );
                    }
                writer._writer.backToEnd();
            }
            else {
                if ( ( ar.getContext() != null && ar.getContext().getGlobalHead().size() > 0 ) || 
                     ( ar != null && ar.getHeadToPrint().size() > 0  ) ){
		    // this is interesting
		    // maybe i should do it only for html files
		    // so i have to know that
                    //throw new RuntimeException( "have head to print but no </head>" );
		}
            }
        }
        catch ( RuntimeException re ){
            if ( re instanceof JSException ){
                if ( re.getCause() != null && re.getCause() instanceof RuntimeException )
                    re = (RuntimeException)re.getCause();
            }
            StackTraceHolder.getInstance().fix( re );
            throw re;
        }
        
    }
    
    String getStaticPrefix( HttpRequest request , AppRequest ar ){
        
        if ( NOCDN )
            return "";
        
        String host = request.getHost();
        
        if ( host == null )
            return "";

        if ( host.indexOf( "." ) < 0 )
            return "";
        
        if ( request.getPort() > 0 )
            return "";

        if ( request.getHeader( "X-SSL" ) != null )
            return "";

        String prefix= "http://static";

        if ( host.indexOf( "local." ) >= 0 )
            prefix += "-local";
        
        prefix += ".10gen.com/" + host;
        return prefix;
    }

    String getStaticSuffix( HttpRequest request , AppRequest ar , String cdnPrefix ){
        final AppContext ctxt = ar.getContext();
        return "ctxt=" + ctxt.getEnvironmentName() + "" + ctxt.getGitBranch() ;
    }
    
    public static class MyWriter extends JSFunctionCalls1 {

        public MyWriter( JxpWriter writer , String cdnPrefix , String cdnSuffix , AppContext context , AppRequest ar ){
            _writer = writer;
            _cdnPrefix = cdnPrefix;
            _cdnSuffix = cdnSuffix;
            _context = context;
            _request = ar;
            
            if ( _writer == null )
                throw new NullPointerException( "writer can't be null" );

            set( "setFormObject" , new JSFunctionCalls1(){ 
                    public Object call( Scope scope , Object o , Object extra[] ){
                        if ( o == null ){
                            _formInput = null;
                            return null;
                        }
                        
                        if ( ! ( o instanceof JSObject ) )
                            throw new RuntimeException( "must be a JSObject" );
                        
                        _formInput = (JSObject)o;
                        _formInputPrefix = null;
                        
                        if ( extra != null && extra.length > 0 )
                            _formInputPrefix = extra[0].toString();
                        
                        return o;
                    }
                } );
        }

        public Object get( Object n ){
            if ( "cdnPrefix".equals( n ) )
                return _cdnPrefix;
            if ( "cdnSuffix".equals( n ) )
                return _cdnSuffix;
            return super.get( n );
        }

        public Object set( Object n , Object v ){
            if ( "cdnPrefix".equals( n ) ){
                _cdnPrefix = v.toString();
                return _cdnPrefix;
            }
            if ( "cdnSuffix".equals( n ) ){
                _cdnSuffix = v.toString();
                return _cdnSuffix;
            }
            return super.set( n  , v );
        }
        
        public Object call( Scope scope , Object o , Object extra[] ){
            if ( o == null )
                print( "null" );
            else
                print( JSInternalFunctions.JS_toString( o ) );
            
            return null;
        }
        
        public void print( String s ){
            
            if ( ( _writtenLength += s.length() ) > MAX_WRITTEN_LENGTH )
                throw new RuntimeException( "trying to write a dynamic page more than " + MAX_WRITTEN_LENGTH + " chars long" );

            if ( _writer.closed() )
                throw new RuntimeException( "output closed.  are you using an old print function" );
            
            while ( s.length() > 0 ){
                
                if ( _extra.length() > 0 ){
                    _extra.append( s );
                    s = _extra.toString();
                    _extra.setLength( 0 );
                }
                
                _matcher.reset( s );
                if ( ! _matcher.find() ){
                    _writer.print( s );
                    return;
                }
                
                _writer.print( s.substring( 0 , _matcher.start() ) );
                
                s = s.substring( _matcher.start() );
                int end = endOfTag( s );
                if ( end == -1 ){
                    _extra.append( s );
                    return;
                }
                
                String wholeTag = s.substring( 0 , end + 1 );
                
                if ( ! printTag( _matcher.group(1) , wholeTag ) )
                    _writer.print( wholeTag );
                
                s = s.substring( end + 1 );
            }
            
        }

        /**
         * @return true if i printed tag so you should not
         */
        boolean printTag( String tag , String s ){

            if ( tag == null )
                throw new NullPointerException( "tag can't be null" );
            if ( s == null )
                throw new NullPointerException( "show tag can't be null" );

            if ( tag.equalsIgnoreCase( "/head" ) && ! _writer.hasSpot() ){
                _writer.saveSpot();
                return false;
            }

            { // CDN stuff
                String srcName = null;
                if ( tag.equalsIgnoreCase( "img" ) ||
                     tag.equalsIgnoreCase( "script" ) )
                    srcName = "src";
                else if ( tag.equalsIgnoreCase( "link" ) )
                    srcName = "href";
                
                if ( srcName != null ){
                    
                    s = s.substring( 2 + tag.length() );
                    
                    // TODO: cache pattern or something
                    Matcher m = Pattern.compile( srcName + " *= *['\"](.+?)['\"]" , Pattern.CASE_INSENSITIVE ).matcher( s );
                    if ( ! m.find() )
                        return false;
                    
                    _writer.print( "<" );
                    _writer.print( tag );
                    _writer.print( " " );
                    
                    _writer.print( s.substring( 0 , m.start(1) ) );
                    String src = m.group(1);
                    
                    printSRC( src );
                    
                    _writer.print( s.substring( m.end(1) ) );

                    return true;
                }
                
            }
            
            if ( _formInput != null && tag.equalsIgnoreCase( "input" ) ){
                Matcher m = Pattern.compile( "\\bname *= *['\"](.+?)[\"']" ).matcher( s );

                if ( ! m.find() )
                    return false;
                
                String name = m.group(1);
                if ( name.length() == 0 )
                    return false;
                
                if ( _formInputPrefix != null )
                    name = name.substring( _formInputPrefix.length() );
                
                Object val = _formInput.get( name );
                if ( val == null )
                    return false;
                
		if ( s.toString().matches( "value *=" ) )
		    return false;

                _writer.print( s.substring( 0 , s.length() - 1 ) );
                _writer.print( " value=\"" );
                _writer.print( HtmlEscape.escape( val.toString() ) );
                _writer.print( "\" >" );
                
                return true;
            }

            return false;
        }

        /**
	 * takes the actual src of the asset and fixes and prints
	 * i.e. /foo -> static.com/foo
	*/
        void printSRC( String src ){

            if ( src == null || src.length() == 0 )
                return;
	    
	    // parse out options

	    boolean nocdn = false;
	    boolean forcecdn = false;
	    
            if ( src.startsWith( "NOCDN/" ) ){
		nocdn = true;
		src = src.substring( 5 );
            }
            else if ( src.startsWith( "CDN/" ) ){
		forcecdn = true;
		src = src.substring( 3 );
            }
	    
            boolean doVersioning = true;

	    // weird special case
            if ( ! src.startsWith( "/" ) ){ // i'm not smart enough to handle local file case yet
                nocdn = true;
                doVersioning = false;
            }
            
            if ( src.startsWith( "//" ) ){ // this is the special //www.slashdot.org/foo.jpg syntax
                nocdn = true;
                doVersioning = false;
            }
            
	    // setup 

            String uri = src;
            int questionIndex = src.indexOf( "?" );
            if ( questionIndex >= 0 )
                uri = uri.substring( 0 , questionIndex );
            
	    String cdnTags = null;
	    if ( uri.equals( "/~f" ) || uri.equals( "/~~/f" ) ){
		cdnTags = ""; // TODO: should i put a version or timestamp here?
	    }
	    else {
                cdnTags = _cdnSuffix;
                if ( cdnTags == null )
                    cdnTags = "";
                else if ( cdnTags.length() > 0 )
                    cdnTags += "&";
                
		if ( doVersioning && _context != null ){
                    File f = _context.getFileSafe( uri );
                    if ( f != null && f.exists() ){
                        cdnTags += "lm=" + f.lastModified();
                    }
                }
	    }
	    
	    // print
            
	    if ( forcecdn || ( ! nocdn && cdnTags != null ) )
		_writer.print( _cdnPrefix );

	    _writer.print( src );
                
	    if ( cdnTags != null && cdnTags.length() > 0 ){
		if ( questionIndex < 0 )
		    _writer.print( "?" );
		else
		    _writer.print( "&" );
		_writer.print( cdnTags );
	    }
	    
	}

        int endOfTag( String s ){
            for ( int i=0; i<s.length(); i++ ){
                char c = s.charAt( i );
                if ( c == '>' )
                    return i;
                
                if ( c == '"' || c == '\'' ){
                    for ( ; i<s.length(); i++)
                        if ( c == s.charAt( i ) )
                            break;
                }
            }
            return -1;
        }
        
        static final Pattern _tagPattern = Pattern.compile( "<(/?\\w+)[ >]" , Pattern.CASE_INSENSITIVE );
        final Matcher _matcher = _tagPattern.matcher("");
        final StringBuilder _extra = new StringBuilder();

        final JxpWriter _writer;
        final AppContext _context;
        final AppRequest _request;
        
        String _cdnPrefix;
        String _cdnSuffix;
        JSObject _formInput = null;
        String _formInputPrefix = null;
        
        int _writtenLength = 0;
    }
    
    final AppContext _context;
    final JxpSource _source;
    final JSFunction _theFunction;
}
