// LoadMonitor.java

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

package ed.net.lb;

import java.util.*;

import ed.net.*;
import ed.net.httpserver.*;
import ed.util.*;

public class LoadMonitor {

    LoadMonitor( Router r ){
        _router = r;
    }
    
    public void hit( HttpRequest request , HttpResponse response ){
        _all.hit( request , response );
	_router.getPool( request )._tracker.hit( request , response );
    }

    HttpLoadTracker getTracker( String name ){
	return _router.getPool( name )._tracker;
    }
    
    void _addMonitors( String name ){
	HttpServer.addGlobalHandler( new HttpMonitor( name + "-load" ){
                public void handle( JxpWriter out , HttpRequest request , HttpResponse response ){
                    _all.displayGraph( out );
                }
            }
            );
    }
    
    final Router _router;
    final HttpLoadTracker _all = new HttpLoadTracker( "all" , 20 , 60 );
}
