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

package ed.net.httpclient;

import java.net.URL;
import java.net.MalformedURLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.HashSet;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;

import javax.servlet.http.Cookie;

import org.testng.annotations.Test;

import ed.net.httpserver.HttpServer;
import ed.io.StreamUtil;
import ed.TestCase;

public class HttpClientTest extends TestCase {
    private static class DummyHttpResponseHandler extends HttpResponseHandlerBase {
        byte[] _postData;

        public DummyHttpResponseHandler(byte[] postData) {
            _postData = postData;
        }

        public byte[] getPostDataToSend () {
            return _postData;
        };

        public int read (InputStream is) throws IOException {
            ByteArrayOutputStream bout = new ByteArrayOutputStream();
            StreamUtil.pipe(is , bout);
            byte[] data = bout.toByteArray();
            return data.length;
        }
    }

    @Test(groups = {"basic"})
    public static void testPostData() {
        HttpServer server;
        try {
            server = new HttpServer(8086);
            server.start();
        } catch (IOException e) {
            throw new RuntimeException("couldn't start the server");
        }

        URL url;
        try {
            url = new URL("http://localhost:8086");
        } catch (MalformedURLException e) {
            throw new RuntimeException("this should never happen");
        }

        byte[] postData = "foo=bar".getBytes();
        DummyHttpResponseHandler postHandler = new DummyHttpResponseHandler(postData);
        DummyHttpResponseHandler emptyHandler = new DummyHttpResponseHandler(null);
        int redirects = 0;
        Set<String> headers = new HashSet<String>();

        HttpConnection connection1;
        HttpConnection connection2;

        // Make a connection with some post data
        try {
            connection1 = HttpClient.setUpConnection(url, postHandler, redirects, headers);
        } catch (IOException e) {
            throw new RuntimeException("connection failed");
        }

        // connection.postData ought to be postData
        assertEquals(connection1.getPostData(), postData);

        try {
            connection1.done();
        } catch (IOException e) {
            throw new RuntimeException("this should never happen");
        }

        // Now make one with an empty handler
        try {
            connection2 = HttpClient.setUpConnection(url, emptyHandler, redirects, headers);
        } catch (IOException e) {
            throw new RuntimeException("connection failed");
        }

        // Make sure that we are using the same connections both times
        assertEquals(connection1, connection2);

        // connection.postData ought to be null
        assertNull(connection2.getPostData());

        try {
            connection2.done();
        } catch (IOException e) {
            throw new RuntimeException("this should never happen");
        }

        server.stopServer();
    }

    @Test(groups = {"basic"}, enabled = false)
    public static void testCookieHeaderParsing() {
        SimpleDateFormat fmt = new SimpleDateFormat( "EEE, dd-MMM-yyyy HH:mm:ss z" , Locale.US);
        
        long diffms = 1000 * 60 * 60;
        long maxAgeMarginSecs = 10;
        
        //Test full header
        Date now = new Date(System.currentTimeMillis() + diffms );
        String nowStr = fmt.format( now );
        
        Cookie c =  HttpClient.parseCookie( "10gen.com" , null , false , "MOO=37; expires="+nowStr+"; path=/;secure" );
        long maxAgeErrorMargin = (diffms/1000) - (c.getMaxAge());
        
        assertEquals( "MOO" , c.getName() );
        assertEquals( "37" , c.getValue() );
        assertLess( maxAgeErrorMargin , maxAgeMarginSecs );
        assertLess( 0 , maxAgeErrorMargin );
        assertTrue( c.getSecure() );
        
        
        //Test unsecure
        now = new Date(System.currentTimeMillis() + diffms );
        nowStr = fmt.format( now );
        
        c =  HttpClient.parseCookie( "10gen.com" , null , false , "MOO=37; expires="+nowStr+"; path=/" );
        maxAgeErrorMargin = (diffms/1000) - (c.getMaxAge());
        
        assertEquals( "MOO" , c.getName() );
        assertEquals( "37" , c.getValue() );
        assertLess( maxAgeErrorMargin , maxAgeMarginSecs );
        assertLess( 0 , maxAgeErrorMargin );
        assertFalse( c.getSecure() );
        
        //Test nonpersistent
        c =  HttpClient.parseCookie( "10gen.com" , null , false , "MOO=37; path=/; secure" );
        
        assertEquals( "MOO" , c.getName() );
        assertEquals( "37" , c.getValue() );
        assertEquals( -1 , c.getMaxAge() );
        assertTrue( c.getSecure() );
    }
    
    public static void main( String args[] ){
        (new HttpClientTest()).runConsole();
    }
}
