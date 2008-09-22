// SiteSystemState.java

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

import java.util.*;

import org.python.core.*;

import ed.appserver.*;
import ed.log.*;

public class SiteSystemState extends PySystemState {
    SiteSystemState( AppContext ac , PyObject newGlobals ){
        super();
        globals = newGlobals;
        setupModules();
    }
    public void setupModules(){
        if( ! ( modules instanceof PythonModuleTracker ) ){
            if( modules instanceof PyStringMap)
                modules = new PythonModuleTracker( (PyStringMap)modules );
            else {
                // You can comment out this exception, it shouldn't
                // break anything beyond reloading python modules
                throw new RuntimeException("couldn't intercept modules " + modules.getClass());
            }
        }
    }

    public void flushOld(){
        if( ! ( modules instanceof PythonModuleTracker ) ){
            throw new RuntimeException( "i'm not sufficiently set up yet" );
        }

        ((PythonModuleTracker)modules).flushOld();
    }

    public void setOutput( AppRequest ar ){
        PyObject out = stdout;
        if ( ! ( out instanceof MyStdoutFile ) || ((MyStdoutFile)out)._request != ar ){
            stdout = new MyStdoutFile( ar );
        }
    }

    static class MyStdoutFile extends PyFile {
        MyStdoutFile(AppRequest request){
            _request = request;
        }
        public void flush(){}

        public void write( String s ){
            if( _request == null )
                // Log
                _log.info( s );
            else
                _request.print( s );
        }
        AppRequest _request;
    }

    final static Logger _log = Logger.getLogger( "python" );
    final public PyObject globals;
}
