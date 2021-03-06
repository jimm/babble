// PostData.java

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

package ed.net.httpserver;

import java.io.*;
import java.nio.*;
import java.util.*;
import java.util.regex.*;
import javax.servlet.*;

import ed.io.*;
import ed.js.*;
import ed.util.*;

public abstract class PostData {
    
    static final boolean DEBUG = false;

    static PostData create( HttpRequest req ){
        final int cl = req.getIntHeader( "Content-Length" , 0 );
        final String ct = req.getHeader( "Content-Type" );
        
        if ( cl <= 0 )
            return null;
        
        boolean multipart = ct != null && ct.toLowerCase().trim().startsWith( "multipart/form-data" );
        
        if ( ! multipart ){
            if ( cl > PostDataInMemory.MAX )
                throw new RuntimeException( "can't have regular post greater than 10 mb" );
            return new PostDataInMemory( cl , multipart , ct );
        }
        
        if ( cl > PostDataInMemory.MAX )
            throw new RuntimeException( "can't handle huge stuff yet" );
        
        return new PostDataInMemory( cl , multipart , ct );
    }


    PostData( int len , boolean multipart , String contentType ){
        _len = len;
        _multipart = multipart;
        _contentType = contentType;
        _boundary = _parseBoundary( _contentType );
    }

    public int length(){
        return _len;
    }

    public Set<String> filenames(){
        return _files.keySet();
    }


    abstract int position();
    abstract byte get( int pos );
    abstract void put( byte b );
    
    abstract String string( int start , int len );
    abstract void fillIn( ByteBuffer buf , int start , int end );

    public abstract JSFile getAsFile();

    public void writeTo( JSLocalFile f )
        throws IOException {
        writeTo( f.getRealFile() );
    }

    public abstract void writeTo( File f )
        throws IOException ;

    public abstract void write( OutputStream out , int start , int end )
        throws IOException;

    public ServletInputStream getInputStream(){
        // TODO: make this faster
        final int max = _len;
        return new ServletInputStream(){
            public int read(){
                if ( _pos >= max )
                    return -1;

                /*
                 *  Java bytes are signed, so force to the int, and then clean up any sign bits that got
                 *  promoted
                 */
                return ((int) get( _pos++ )) & 0xFF;
                    
            }
            
            int _pos;
        };
    }

    public ByteStream getByteStream(){
	final int max = _len;
	return new ByteStream(){
	    
	    public boolean hasMore(){
		return _pos < max;
	    }

	    public int write( ByteBuffer bb ){
		final int toTransfer = Math.min( bb.remaining() , max - _pos );

		fillIn( bb , _pos , _pos + toTransfer );

		_pos += toTransfer;
		return toTransfer;
	    }

	    private int _pos = 0;
	};
    }
    

    int indexOf( byte b[] , int start ){
        
        outer:
        for ( int i=start; i<_len-b.length; i++ ){
            for ( int j=0; j<b.length; j++ )
                if ( b[j] != get( i + j ) )
                    continue outer;

            return i;
        }
        return -1;
    }
    
    boolean done(){
        final int pos = position();
        if ( pos > _len )
            throw new RuntimeException( "something is wrong" );
        return pos == _len;
    }

    int gotSoFar(){
	return position();
    }

    void go( HttpRequest req ){
        if ( ! _multipart ){
            addRegularParams( req );
            return;
        }
        
        parseMultiPart( req );
    }

    private void parseMultiPart( HttpRequest req ){
        if ( DEBUG ) System.out.println( "starting to parse multi-part" );

        int start = indexOf( _boundary , 0 );
        if ( start < 0 )
            return;
        
        start += _boundary.length + 1;

        while ( start < _len ){
            
            if ( get( start ) == '\n' )
                start++;
            
            int end = indexOf( _boundary , start );
            if ( end < 0 ){
                if ( get( start ) == '-' )
                    break;
                end = position();
            }

            
            Map<String,String> headers = new StringMap<String>();
            Map<String,String> mainPieces = new StringMap<String>();

            String type = null;
            while ( true ){
                int eol = start;
                for ( ; eol < _len; eol++ )
                    if ( get( eol ) == '\n' )
                        break;

                String line = string( start , eol - start ).trim();
                start = eol + 1;
                if ( line.length() == 0 )
                    break;
                
                if ( DEBUG ) System.out.println( "\t got post header [" + line + "]" );

                int col = line.indexOf( ":" );
                if ( col < 0 )
                    continue;
                
                final String name = line.substring( 0 , col ).trim();
                final String value = line.substring( col + 1 ).trim();

                if ( name.equalsIgnoreCase( "Content-Disposition" ) ){
                    Matcher temp = Pattern.compile( "; (\\w+)=\"(.*?)\"" ).matcher( value );
                    while ( temp.find() )
                        mainPieces.put( temp.group(1) , temp.group(2) );
                }
                
                if ( name.equalsIgnoreCase( "content-type" ) )
                    type = value;
                
                headers.put( name , value );
            }
            
            if ( DEBUG ) System.out.println( "\t multipart headers " + headers + " mainPieces " + mainPieces );

            if ( mainPieces.get( "filename" ) != null ){
                String fn = mainPieces.get( "filename" ).trim();
                if ( type == null )
                    type = ed.appserver.MimeTypes.get( fn );
                if ( DEBUG ) System.out.println( "got filename [" + fn + "] of type [" + type + "]" );

                /*
                 *  1 byte to back off of the start of boundary, and 1 byte to back off of the terminating \n of the
                 *  data
                 */
                int lastByte = end - 2;
                
                UploadFile uf = new UploadFile( fn , type , this , start, lastByte);
                _files.put( mainPieces.get( "name" ) , uf );
            }
            else if ( type == null ){
                req._addParm( mainPieces.get( "name" ) , string( start , end - start ).trim() , true );
            }
            else {
                throw new RuntimeException( "can't handle : " + type + " " + headers );
            }
            
            start = end + _boundary.length + 1;
        }
        
    }

    private void addRegularParams( HttpRequest req ){
        for ( int i=0; i<_len; i++ ){
            int start = i;
            for ( ; i<_len; i++ )
                if ( get(i) == '=' ||
                     get(i) == '\n' ||
                     get(i) == '&' )
                    break;

            if ( i == _len ){
                req._addParm( string( start , _len - start ) , null , true );
                break;
            }
            
            if ( get(i) == '\n' ||
                 get(i) == '&' ){
                req._addParm( string( start , i - start ) , null , true );
                continue;
            }

            int eq = i;
            
            for ( ; i<_len; i++ )
                if ( get(i) == '\n' ||
                     get(i) == '&' )
                    break;
            
            req._addParm( string( start , eq - start ) ,
                          string( eq + 1 , i - ( eq + 1 ) ) , 
                          true );
                
        }
    }

    static byte[] _parseBoundary( String ct ){
        if ( ct == null )
            return null;

        final String thing = "boundary=";

        int start = ct.indexOf( thing );
        if ( start <= 0 )
            return null;
        
        int end = ct.indexOf( ";" , start );
        if ( end < 0 )
            end = ct.length();
        
        return ( "--" + ct.substring( start + thing.length() , end ) ).getBytes();
    }


    String status(){
        return "{" + position() + "/" + _len + " mp:" + _multipart + "}";
    }

    final int _len;
    final boolean _multipart;
    final String _contentType;
    final byte[] _boundary;
    final Map<String,UploadFile> _files = new TreeMap<String,UploadFile>();
    

}
