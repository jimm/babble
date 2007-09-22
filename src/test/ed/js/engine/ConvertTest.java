// ConvertTest.java

package ed.js.engine;

import java.io.*;

import ed.*;
import ed.js.*;
import ed.io.*;

public class ConvertTest {

    public static class FileTest extends TestCase {
        FileTest( File f ){
            super( f.toString() );
            _file = f;
        }

        public void test()
            throws IOException {
            Convert c = new Convert( _file );
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            PrintStream out = new PrintStream( bout );
            
            JSFunction f = c.get();
            f.setSysOut( out );
            System.out.println( "START" );
            f.call();
            System.out.println( "END" );

            String outString = _clean( bout.toString() );
            
            File correct = new File( _file.toString().replaceAll( ".js$" , ".out" ) );
            if ( ! correct.exists() ){
                assertTrue( correct.exists() );
            }
            String correctOut = _clean( StreamUtil.readFully( correct ) );
            
            assertClose( correctOut , outString );
        }
        
        final File _file;
    }

    static String _clean( String s ){
        s = s.replaceAll( "tempFunc_\\d+_" , "tempFunc_" );
        return s;
    }
    
    public static void main( String args[] ){
        if ( args.length > 0 ){
            TestCase all = new TestCase();
            for ( String s : args )
                all.add( new FileTest( new File( s ) ) );
            all.runConsole();
        }
    }
}
