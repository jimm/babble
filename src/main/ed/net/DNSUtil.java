// DNSUtil.java

package ed.net;

import java.io.*;
import java.net.*;
import java.util.*;

public class DNSUtil {

    public static String getSubdomain( String host ){
        String domain = getDomain( host );
        if ( host.length() == domain.length() )
            return "www";
        String sub = host.substring( 0 , host.length() - domain.length() );
        while ( sub.endsWith( "." ) )
            sub = sub.substring( 0 , sub.length() - 1 );
        sub = sub.trim();
        if ( sub.length() == 0 )
            return "www";
        return sub;
    }

    public static String getDomain( String host ){
        String tld = getTLD( host );
        int idx = host.lastIndexOf( "." , host.length() - ( tld.length() + 2 ) );
        if ( idx < 0 )
            return host;
        return host.substring( idx + 1 );
    }
    
    public static String getTLD( String host ){
        
        host = host.toLowerCase();

        // efficiency hack
        if ( host.endsWith( ".com" ) )
            return "com";
        
        int idx = host.lastIndexOf( "." );

        if ( idx < 0 )
            throw new RuntimeException( "not a host:" + host );

        if ( idx == host.length() - 1 )
            idx = host.lastIndexOf( "." , host.length() - 2 );
        
        if ( idx < 0 )
            throw new RuntimeException( "not a host:" + host );

        String tld = host.substring( idx + 1 );
        
        if ( TLDS.contains( tld ) )
            return tld;
        
        int idx2 = host.lastIndexOf( "." , idx - 1 );
        String moreTLD = idx2 < 0 ? host : host.substring( idx2 + 1 );

        if ( TLDS.contains( moreTLD ) )
            return moreTLD;

        
        if ( tld.length() >= 3 )
            return tld;

        if ( idx - idx2 > 3 )
            return tld;

        return moreTLD;
    }

    public static List<InetAddress> getPublicAddresses()
        throws IOException {

        List<InetAddress> lst = new ArrayList<InetAddress>();
        
        for ( NetworkInterface nic : getNetworkInterfaces() ){
            Enumeration<InetAddress> e = nic.getInetAddresses();
            while ( e.hasMoreElements() ){
                InetAddress ia = e.nextElement();

                if ( ! ( ia instanceof Inet4Address ) )
                    continue;

                String ip = ia.getHostAddress();
                
                if ( ip.startsWith( "127.0.0.1" ) )
                    continue;
                if ( ip.startsWith( "10." ) )
                    continue;
                if ( ip.startsWith( "192.168." ) )
                    continue;
                
                lst.add( ia );
            }
        }
        
        return lst;
    }

    public static List<NetworkInterface> getNetworkInterfaces()
        throws java.io.IOException {
        List<NetworkInterface> lst = new ArrayList<NetworkInterface>();
        
        Enumeration<NetworkInterface> e = NetworkInterface.getNetworkInterfaces();
        while ( e.hasMoreElements() )
            lst.add( e.nextElement() );

        return lst;
    }

    final static Set<String> TLDS = new HashSet<String>();
    static {
        for ( String s : new ed.io.LineReader( ClassLoader.getSystemClassLoader().getResourceAsStream( "tlds.txt" ) ) ){
            s = s.toLowerCase().trim();
            if ( s.length() == 0 )
                continue;
            s = s.replaceAll( "\\-\\-.*" , "" );
            TLDS.add( s );
        }
    }

    public static void main( String args[] )
        throws Exception {
        
        System.out.println( getPublicAddresses() );
    }
    
}
