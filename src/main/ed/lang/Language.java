// Language.java

/**
*      Copyright (C) 2008 10gen Inc.
*  
*    Licensed under the Apache License, Version 2.0 (the "License");
*    you may not use this file except in compliance with the License.
*    You may obtain a copy of the License at
*  
*       http://www.apache.org/licenses/LICENSE-2.0
*  
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS,
*    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*    See the License for the specific language governing permissions and
*    limitations under the License.
*/

package ed.lang;

import javax.script.*;

import ed.js.*;
import ed.js.engine.*;
import ed.appserver.AppContext;
import ed.appserver.JSFileLibrary;
import ed.appserver.adapter.AdapterType;
import ed.appserver.jxp.JxpSource;

import java.io.File;

public abstract class Language {
    
    protected Language( String name ){
        _name = name;
    }

    /**
     *  Returns the adapter (10gen, CGI, etc) for the given file and the current request.  The adapter
     *  type is passed in,  which is usually determined from the appcontext, but the appcontext
     *  is provided for further information.
     *
     *  If nothing else, this should return a basic JxpSource
     *
     * @param type adapter type requested
     * @param fileName  application file (e.g. foo.jxp) to be processed by adapter. T
     * @param context   application context
     * @param lib  applications file library
     * @return JxpSource object for the file.  Could be CGI, WSGI, Rack, etc
     */
    public abstract JxpSource getAdapter(AdapterType type, File fileName, AppContext context , JSFileLibrary lib);

    public boolean isScriptable(){
        return false;
    }

    public ObjectConvertor getObjectConvertor(){
        throw new UnsupportedOperationException();
    }

    public ScriptEngine getScriptEngine(){
        throw new UnsupportedOperationException();
    }

    public JSFunction compileLambda( String source ){
        throw new UnsupportedOperationException();
    }

    public Object eval( Scope scope , String code , boolean[] hasReturn ){
        JSFunction func = compileLambda( code );
        Object ret = func.call( scope );
        if ( hasReturn != null && hasReturn.length > 0 )
            hasReturn[0] = true;
        return ret;
    }

    public boolean isComplete( String code ){
        return true;
    }

    public String toString(){
        return _name;
    }

    final String _name;
    
    public static Language find( String file ){
        return find( file , false );
    }

    public static Language find( String file , boolean errorOnNoMatch ){
        
        final int idx = file.lastIndexOf( "." );

        final String extension;
        if ( idx >= 0 )
            extension = file.substring( idx + 1 );
        else
            extension = file;
        
        if ( extension.equals( "js" )
             || extension.equals( "jxp" ) )
            return JS();

        if ( extension.equals( "rb" )
             || extension.equals( "erb" )
             || extension.equals( "rhtml" )
	     || extension.equals( "ruby" ) ) // only so "--ruby" will work
            return RUBY();

        if ( extension.equals( "php" ) ) 
            return PHP();
        
        if ( extension.equals( "py" ) || extension.equals( "python" ) )
            return PYTHON();
        
        if ( errorOnNoMatch )
            throw new RuntimeException( "no language for [" + extension + "]" );

        return JS();
    }

    public static Language JS(){
        return _js;
    }
    public static Language RUBY(){
        if ( _ruby == null )
            _ruby = new ed.lang.ruby.RubyLanguage();
        return _ruby;
    }
    public static Language PYTHON(){
        if ( _python == null )
            _python = new ed.lang.python.Python();
        return _python;
    }
    public static Language PHP(){
        if ( _php == null )
            _php = new ed.lang.php.PHP();
        return _php;
    }
    
    private static final Language _js = new ed.js.JS();
    private static Language _ruby;// = new ed.lang.ruby.RubyLanguage();
    private static Language _python; // = new ed.lang.python.Python();
    private static Language _php;


        
}
