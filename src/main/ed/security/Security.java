// Security.java

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

package ed.security;

import java.util.*;
import java.io.*;

import ed.appserver.*;
import ed.js.engine.*;
import ed.util.Config;
import ed.lang.StackTraceHolder;

public class Security {

    public final static boolean OFF = ed.util.Config.get().getBoolean( "NO-SECURITY" );
    public final static String _baseClass = Convert.cleanName( Module.getBase());

    public final static Set<String> allowedSites;
    static {
	Set<String> s = new HashSet<String>();
	s.add( "admin" );
	s.add( "www" );
	s.add( "grid" );
	s.add( "mongo" );
	allowedSites = Collections.unmodifiableSet( s );
    }

    public final static boolean isAllowedSite( String siteName ){
	return allowedSites.contains( siteName );
    }

    final static String SECURE[] = new String[]{
        Config.getDataRoot() + "corejs/" ,
        Config.getDataRoot() + "core-modules/admin/",
        Config.getDataRoot() + "core-modules/py-google/",
        Config.getDataRoot() + "core-modules/cloudsignup/",
        Config.getDataRoot() + "sites/admin/",
        Config.getDataRoot() + "sites/www/",
        Config.getDataRoot() + "sites/grid/",
        Config.getDataRoot() + "sites/modules/",
        "lastline",
        "src/main/ed/",
        "src/test/ed/",
        "/home/yellow/code_for_hudson/",
        new File( "src/test/ed/" ).getAbsolutePath(),
        new File( "include/jython/Lib" ).getAbsolutePath(),
        "./src/test/ed/lang/python/", // FIXME?
        Config.get().getProperty("ED_HOME", "/data/ed") + "/src/test/ed",
        Config.get().getProperty("ED_HOME", "/data/ed") + "/include/jython/Lib",
        "./appserver/libraries/corejs/core" // TODO - fix this - hack to deal with SDK test failures
    };

    public static final boolean inTrustedCode(){
        return isCoreJS();
    }

    public static boolean isCoreJS(){
        if ( OFF )
            return true;

        String topjs = getTopJS();
        if ( topjs == null )
            return true;
        
        for ( int i=0; i<SECURE.length; i++ )
            if ( topjs.startsWith( SECURE[i] ) )
                return true;
        
        return false;
    }

    public static String getTopJS(){
        StackTraceElement e = getTopDynamicStackFrame();
        if ( e == null )
            return null;
        return e.getFileName();
    }

    public static StackTraceElement getTopDynamicStackFrame(){
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        StackTraceHolder holder = StackTraceHolder.getInstance();

        for ( int i=0; i<st.length; i++ ){
            StackTraceElement e = st[i];
            StackTraceElement n = holder.fix( e );
            // if n == null, this was removed, which means this was internal.
            // if n is different, e was replaced, which means e was dynamic code
            // that someone knew how to handle.
            if ( n == null || n == e ) continue;

            return n;
        }

        return null;
    }

    public static StackTraceElement getTopUserStackElement(){
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        
        for ( int i=0; i<st.length; i++ ){
            StackTraceElement e = st[i];

            final String name = e.getClassName();

            if ( name.startsWith( Convert.DEFAULT_PACKAGE + "." ) )
                return e;
            
            if ( name.startsWith( "ed." ) || name.startsWith( "java." ) )
                continue;
            
            return e;
        }

        return null;
        
    }
}
