// PyJSObjectWrapper.java

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

package ed.lang.python;

import org.python.core.*;

import ed.js.*;
import ed.js.engine.*;
import static ed.lang.python.Python.*;


public class PyJSObjectWrapper extends PyDictionary {
    
    public PyJSObjectWrapper( JSObject jsObject ){
        this( jsObject , true );
    }

    public PyJSObjectWrapper( JSObject jsObject , boolean returnPyNone ){
        super( );
        _js = jsObject;
        _returnPyNone = returnPyNone;
        if ( _js == null )
            throw new NullPointerException( "don't think you should create a PyJSObjectWrapper for null" );
    }
    
    public PyObject __findattr__(String name) {

        if ( D ) System.out.println( "__findattr__ on [" + name + "]" );

        // FIXME: more graceful fail-through etc
        try{
            PyObject p = super.__findattr__( name );
            if( p != null )
                return p;
        }
        catch(PyException e){
        }
        
        Object res = _js.get( name );
        if ( res == null )
            res = NativeBridge.getNativeFunc( _js , name );
        
        return _fixReturn( res );
    }    

    public PyObject __finditem__(PyObject key){

        if ( D ) System.out.println( "__finditem__ on [" + key + "]" );

        // FIXME: more graceful fail-through etc
        PyObject p = super.__finditem__(key);
        if( p != null )
            return p;
        return _fixReturn( _js.get( toJS( key ) ) );
    }

    public PyObject __dir__(){
        PyList list = new PyList();
        for( String s : _js.keySet() ){
            list.append( Py.newString( s ) );
        }
        return list;
    }
    
    private PyObject _fixReturn( Object o ){
        if ( o == null && ! _returnPyNone )
            return null;
        
        return toPython( o );
    }

    public void __setitem__(PyObject key, PyObject value) {
        super.__setitem__(key, value);
        this.handleSet( toJS( key ) , toJS( value ) );
    }

    public void __setattr__( String key , PyObject value ){
        super.__setitem__(key, value);
        this.handleSet( toJS( key ) , toJS( value ) );
    }

    public void handleSet( Object key , Object value ){
        _js.set( toJS( key ) , toJS( value ) );
    }

    public void __delattr__( String key ){
        try {
            super.__delitem__( key );
        }
        catch( PyException e ){
            // meh
        }
        _js.removeField( key );
    }

    public String toString(){
        return _js.toString();
    }

    final JSObject _js;
    final boolean _returnPyNone;
}
