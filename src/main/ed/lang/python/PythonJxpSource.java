// PythonJxpSource.java

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

import java.io.*;
import java.util.*;

import org.python.core.*;
import org.python.expose.*;
import org.python.Version;
import org.python.expose.generate.*;

import ed.js.*;
import ed.log.*;
import ed.js.engine.*;
import ed.lang.*;
import ed.util.*;
import ed.appserver.*;
import ed.appserver.jxp.*;

public class PythonJxpSource extends JxpSource {

    static {
        System.setProperty( "python.cachedir", ed.io.WorkingFiles.TMP_DIR + "/jython-cache/" + Version.PY_VERSION );
    }

    public PythonJxpSource( File f , JSFileLibrary lib ){
        _file = f;
        _lib = lib;
    }
    
    protected String getContent(){
        throw new RuntimeException( "you can't do this" );
    }
       
    protected InputStream getInputStream(){
        throw new RuntimeException( "you can't do this" );
    }
    
    public long lastUpdated(Set<Dependency> visitedDeps){
        return _file.lastModified();
    }
    
    public String getName(){
        return _file.toString();
    }

    public File getFile(){
        return _file;
    }

    public synchronized JSFunction getFunction()
        throws IOException {
        
        final PyCode code = _getCode();
        
        return new ed.js.func.JSFunctionCalls0(){
            public Object call( Scope s , Object extra[] ){

                PyObject args[] = new PyObject[ extra == null ? 0 : extra.length ];
                for ( int i=0; i<args.length; i++ )
                    args[i] = Python.toPython( extra[i] );
                
                final AppRequest ar = AppRequest.getThreadLocal();
                final AppContext ac = getAppContext();

                SiteSystemState ss = Python.getSiteSystemState( ac , s );
                PySystemState pyOld = Py.getSystemState();

                ensureMetaPathHook( ss.state , s );

                ss.flushOld();

                ss.setOutput( ar );

                addPath( ss.state , _lib.getRoot().toString() );
                addPath( ss.state , _lib.getTopParent().getRoot().toString() );

                PyObject globals = ss.globals;
                PyObject builtins = ss.state.builtins;

                PyObject pyImport = builtins.__finditem__( "__import__" );
                if( ! ( pyImport instanceof TrackImport ) || ((TrackImport)pyImport)._moduleDict != ss.state.modules )
                    builtins.__setitem__( "__import__" , new TrackImport( pyImport , (PythonModuleTracker)ss.state.modules ) );

                try {
                    Py.setSystemState( ss.state );

                
                    PyModule xgenMod = imp.addModule("_10gen");
                    // I know this is appalling but they don't expose this any other
                    // way
                    xgenMod.__dict__ = globals;

                    //Py.initClassExceptions( globals );
                    globals.__setitem__( "__file__", Py.newString( _file.toString() ) );
                    PyModule module = new PyModule( "__main__" , globals );

                    PyObject locals = module.__dict__;
                    return Py.runCode( code, locals, globals );
                }
                finally {
                    Py.setSystemState( pyOld );
                }
            }

            private void ensureMetaPathHook( PySystemState ss , Scope scope ){
                boolean foundMetaPath = false;
                for( Object m : ss.meta_path ){
                    if( ! ( m instanceof PyObject ) ) continue; // ??
                    PyObject p = (PyObject)m;
                    if( p instanceof ModuleFinder )
                        return;
                }

                ss.meta_path.append( new ModuleFinder( scope ) );
            }
        };
    }

    static void addPath( PySystemState ss , String myPath ){

        for ( Object o : ss.path )
            if ( o.toString().equals( myPath ) )
                return;
        
        ss.path.append( Py.newString( myPath ) );
    }

    private PyCode _getCode()
        throws IOException {
        PyCode c = _code;
	final long lastModified = _file.lastModified();
        if ( c == null || _lastCompile < lastModified ){
            c = Python.compile( _file );
            _code = c;
            _lastCompile = lastModified;
        }
        return c;
    }

    final File _file;
    final JSFileLibrary _lib;

    private PyCode _code;
    private long _lastCompile;
    
    void addDependency( String to ){
        super.addDependency( new FileDependency( new File( to ) ) );
    }

    class TrackImport extends PyObject {
        PyObject _import;
        PythonModuleTracker _moduleDict;
        TrackImport( PyObject importF , PythonModuleTracker sys_modules ){
            _import = importF;
            _moduleDict = sys_modules;
        }

        public PyObject __call__( PyObject args[] , String keywords[] ){
            int argc = args.length;
            // Second argument is the dict of globals. Mostly this is helpful
            // for getting context -- file or module *doing* the import.
            PyObject globals = ( argc > 1 ) ? args[1] : null;

            //System.out.println("Overrode import importing. import " + args[0] + " in file " + globals.__finditem__( "__file__" ) );

            PyObject m = _import.__call__( args, keywords );

            if( globals == null ){
                // Only happens (AFAICT) from within Java code.
                // For example, Jython's codecs.java calls
                // __builtin__.__import__("encodings");
                return m;
            }


            // gets the module name -- __file__ is the file
            PyObject importer = globals.__finditem__( "__name__".intern() );

            PyObject to = m.__findattr__( "__name__".intern() );
            // no __file__: builtin or something -- don't bother adding
            // dependency
            if( to == null ) return m;

            // Add a plain old JXP dependency on the file that was imported
            // Not sure if this is helpful or not
            addDependency( to.toString() );

            // Add a module dependency -- module being imported was imported by
            // the importing module
            _moduleDict.addDependency( to , importer );
            return m;

            //PythonJxpSource foo = PythonJxpSource.this;
        }
    }

    /**
     * sys.meta_path hook to deal with core/core-modules and local/local-modules
     * imports.
     *
     * Python meta_path hooks are one of many ways a program can
     * customize how/where modules are loaded. They have two parts,
     * finders and loaders. This is the finder class, whose API
     * consists of one method, find_module.
     *
     * For more details on the meta_path hooks, check PEP 302.
     */
    @ExposedType(name="_10gen_module_finder")
    public class ModuleFinder extends PyObject {
        Scope _scope;
        JSLibrary _coreModules;
        JSLibrary _core;
        JSLibrary _local;
        JSLibrary _localModules;
        ModuleFinder( Scope s ){
            _scope = s;
            Object core = s.get( "core" );
            if( core instanceof JSLibrary )
                _core = (JSLibrary)core;
            if( core instanceof JSObject ){
                Object coreModules = ((JSObject)core).get( "modules" );
                if( coreModules instanceof JSLibrary )
                    _coreModules = (JSLibrary)coreModules;
            }

            Object local = s.get( "local" );
            if( local instanceof JSLibrary )
                _local = (JSLibrary)local;
            if( local instanceof JSObject ){
                Object localModules = ((JSObject)local).get( "modules" );
                if( localModules instanceof JSLibrary )
                    _localModules = (JSLibrary)localModules;
            }
        }

        /**
         * The sole interface to a finder. We create virtual modules
         * for "core" and "core.modules", and any relative import
         * within a core module has to be handled specially.
         * Specifically, an import for baz from within
         * core.modules.foo.bar comes out as an import for
         * core.modules.foo.bar.baz (with __path__ =
         * ['/data/core-modules/foo/bar']) and if we can't find it, we
         * try core.modules.foo.baz (simulating core.modules.foo being
         * on the module search path).
         *
         * Alternately, we could just add core.modules.foo to sys.path
         * when it gets imported, but Geir says we should make it like
         * JS, which means ugly and painful.
         *
         * find_module returns a "loader", as specified by PEP 302.
         *
         * @param fullname {PyString} name of the module to find
         * @param path {PyList} optional; the __path__ of the module
         */
        @ExposedMethod(names={"find_module"})
        public PyObject find_module( PyObject args[] , String keywords[] ){
            int argc = args.length;
            assert argc >= 1;
            assert args[0] instanceof PyString;
            String modName = args[0].toString();
            if( modName.equals("core.modules") ){
                return new LibraryModuleLoader( _coreModules );
            }

            if( modName.startsWith("core.modules.") ){
                // look for core.modules.foo.bar...baz
                // and try core.modules.foo.baz
                // Should confirm that this is from within core.modules.foo.bar... using __path__
                int period = modName.indexOf('.') + 1; // core.
                period = modName.indexOf( '.' , period ) + 1; // modules.
                int next = modName.indexOf( '.' , period ); // foo
                if( next != -1 && modName.indexOf( '.' , next + 1 ) != -1 ){
                    String foo = modName.substring( period , next );
                    File fooF = new File( _coreModules.getRoot() , foo );
                    String baz = modName.substring( modName.lastIndexOf( '.' ) + 1 );
                    File bazF = new File( fooF , baz );
                    File bazPyF = new File( fooF , baz + ".py" );
                    if( bazF.exists() || bazPyF.exists() ){
                        return new RewriteModuleLoader( modName.substring( 0 , next ) + "." + baz );
                    }
                }
            }

            if( modName.equals("core") ){
                return new LibraryModuleLoader( _core );
            }

            return Py.None;
        }
    }

    /**
     * A module loader for core, core.modules, etc.
     *
     * Basically this wraps a JSLibrary in such a way that when the
     * module is loaded, sub-modules can be found by the default
     * Python search.  (Specifically we set the __path__ to the root
     * of the library.) This obviates the need for putting __init__.py
     * files throughout corejs and core-modules.
     */
    @ExposedType(name="_10gen_module_library_loader")
    public class LibraryModuleLoader extends PyObject {
        JSLibrary _root;
        LibraryModuleLoader( Object start ){
            assert start instanceof JSLibrary;
            _root = (JSLibrary)start;
        }

        public JSLibrary getRoot(){
            return _root;
        }


        /**
         * The load_module method specified in PEP 302.
         *
         * @param fullname {PyString} the full name of the module
         * @return PyModule
         */
        @ExposedMethod(names={"load_module"})
        public PyModule load_module( String name ){
            PyModule mod = imp.addModule( name );
            PyObject __path__ = mod.__findattr__( "__path__".intern() );
            if( __path__ != null ) return mod; // previously imported

            mod.__setattr__( "__file__".intern() , new PyString( "<10gen_virtual>" ) );
            mod.__setattr__( "__loader__".intern() , this );
            PyList pathL = new PyList( PyString.TYPE );
            pathL.append( new PyString( _root.getRoot().toString() ) );
            mod.__setattr__( "__path__".intern() , pathL );

            return mod;
        }
    }

    /**
     * Module loader that loads a module different than specified.
     * We use this when a core-module imports a file that exists at the top
     * of the core-module. This way, core-modules can pretend they're on
     * sys.path, but without actually being sys.path. (Don't want life to be
     * too easy for people on our platform.)
     */
    @ExposedType(name="_10gen_module_rewrite_loader")
    public class RewriteModuleLoader extends PyObject {
        String _realName;
        RewriteModuleLoader( String real ){
            _realName = real;
        }

        /**
         * The load_module method specified in PEP 302.
         *
         * @param fullname {PyString} the full name of the module
         * @return PyModule
         */
        @ExposedMethod(names={"load_module"})
        public PyObject load_module( String name ){
            PyObject m = __builtin__.__import__(_realName);
            String components = _realName.substring( _realName.indexOf('.') + 1 );
            while( components.indexOf('.') != -1 ){
                String component = components.substring( 0 , components.indexOf('.') );
                m = m.__findattr__( component.intern() );
                components = components.substring( components.indexOf('.') + 1 );
            }
            m = m.__findattr__( components.intern() );
            return m;
        }
    }

    // static b/c it has to use ThreadLocal anyway
    final static Logger _log = Logger.getLogger( "python" );
}
