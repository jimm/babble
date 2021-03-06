// JSNumber.java

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

package ed.js;

import java.util.*;
import java.text.DecimalFormat;

import ed.util.*;
import ed.js.func.*;
import ed.js.engine.*;

/** @expose
    i'm not really sure what to do with this
    right now its just holder for weird stuff
*/
public class JSNumber extends Number implements JSObject {

    public JSNumber( int i ){
	_val = i;
    }

    public JSNumber( double d ){
	_val = d;
    }

    public JSNumber( Number val ){
        _val = val;
    }

    public Number get(){
        return _val;
    }

    public byte byteValue(){
	return _val.byteValue();
    }

    public double doubleValue(){
	return _val.doubleValue();
    }
    
    public float floatValue(){
	return _val.floatValue();
    }
    
    public int intValue(){
	return _val.intValue();
    }
    
    public long longValue(){
	return _val.longValue();
    }
    
    public short shortValue(){
	return _val.shortValue();
    }

    public String toString(){
        return _val.toString();
    }

    public Object set( Object n , Object v ){
	// this seems to be a no-op
	return v;
    }

    public Object setInt( int n , Object v ){
	// this seems to be a no-op
	return v; 
    }

    public Object get( Object n ){
	return null;
    }

    public Object getInt( int n ){
	return null;
    }

    public JSFunction getFunction( String name ){
	return getFunctions().getFunction( name );
    }
    
    public Object removeField( Object n ){
	return null;
    }

    public boolean containsKey( String s ){
	return false;
    }

    public boolean containsKey( String s , boolean includePrototype ){
        return false;
    }
    
    public Set<String> keySet(){
	return new HashSet<String>();
    }

    public Set<String> keySet( boolean includePrototype ){
	return new HashSet<String>();
    }

    public JSFunction getConstructor(){
	return getCons();
    }

    public JSObject getSuper(){
	return null;
    }

    private Number _val;

    // -----------
    // STATIC STUFF BELOW
    // -----------
    
    /** Returns a given double.
     * @param A number.
     * @return <tt>x</tt>
     */
    public final static double self( double x ){
	return x;
    }

    /** Returns a given int.
     * @param A number.
     * @return <tt>x</tt>
     */
    public final static int self( int x ){
        return x;
    }

    /** Function to parse a number using a given base.  */
    public static class Cons extends JSFunctionCalls1{
            
        public JSObject newOne(){
            return new JSNumber( 0 );
        }

        public Object call( Scope scope , Object a , Object extra[] ){
            Object def = 0;
            if( extra != null && extra.length > 0 )
                def = extra[0];            

            if ( scope.getThis() instanceof JSNumber ){
                JSNumber n = (JSNumber)(scope.getThis());
                n._val = _parse( a , def );
                return n._val;
            }
            
            return _parse( a , def );
        }
        
        Number _parse( Object a , Object b ){
            return JSNumber.getNumber( a == null ? b : a );
        }
        
        protected void init(){
            _prototype.addAll( _functions );

            set( "NaN" , Double.NaN );
            set( "POSITIVE_INFINITY" , Double.POSITIVE_INFINITY );
            set( "NEGATIVE_INFINITY" , Double.NEGATIVE_INFINITY );
            set( "MAX_VALUE" , Double.MAX_VALUE );
            set( "MIN_VALUE" , Double.MIN_VALUE );
            setProperties( "NaN" , JSObjectBase.LOCK );
            setProperties( "POSITIVE_INFINITY" , JSObjectBase.LOCK );
            setProperties( "NEGATIVE_INFINITY",  JSObjectBase.LOCK );
            setProperties( "MAX_VALUE" , JSObjectBase.LOCK );
            setProperties( "MIN_VALUE" , JSObjectBase.LOCK );
            dontEnumExisting();
        }
        
    }

    public static Cons getCons(){
        return (Cons)(Scope.getThreadLocalFunction( "Number" , new Cons() ) );
    }

    public static JSObjectBase getFunctions(){
        return getCons()._prototype;
    }

    private static JSObjectBase _functions = new JSObjectBase();

    /** Function that turns a number into string, truncating any fractional part
     */
    public static JSFunction toFixed = new JSFunctionCalls1(){
            public Object call( Scope sc , Object pointsInt , Object fo00000o[] ){
                Number n = (Number)(sc.getThis());
                int num = 0;
                if ( pointsInt != null && pointsInt instanceof Number )
                    num = ((Number)pointsInt).intValue();

                double mult = Math.pow( 10 , num );

                long foo = Math.round( n.doubleValue() * mult );
                double d = foo;
                d = d / mult ;

                String s = String.valueOf( d );
                int idx = s.indexOf( "." );

                if ( idx < 0 ){
                    if( num > 0 ){
                        s += ".";
                        while( num > 0 ){
                            s += "0";
                            num--;
                        }
                    }
                    return new JSString( s );
                }

                if ( s.length() - idx <= num ){ // need more
                    int toAdd = ( num + 1 ) - ( s.length() - idx );
                    for ( int i=0; i<toAdd; i++ )
                        s += "0";
                    return new JSString( s );
                }

                if ( num == 0 )
                    return s.substring( 0 , idx  );
                return new JSString( s.substring( 0 , idx + 1 + num  ) );
            }
        };

    public static class Conversion extends JSFunctionCalls0{

        Conversion( double conversion ){
            _conversion = conversion;
        }

        public Object call( Scope s , Object foo[] ){
            Number n = (Number)s.getThis();
            return n.doubleValue() * _conversion;
        }

        final double _conversion;
    }

    /** @unexpose */
    static Object _loop( final Scope s , final JSFunction f , final int start , final int end ){
        Object blah = s.getParent().getThis();
        s.setThis( blah );

        Boolean old = f.setUsePassedInScopeTL( true );

        for ( int i=start; i<end; i++ )
            f.call( s , i );

        f.setUsePassedInScopeTL( old );

        s.clearThisNormal( null );

        return null;
    }

    static {
        _functions.set( "toFixed" , toFixed );

        _functions.set( "to_f" , new JSFunctionCalls0(){
                public Object call( Scope s , Object foo[] ){
                    return ((Number)s.getThis()).doubleValue();
                }
            } );

        _functions.set( "round" , new JSFunctionCalls0(){
                public Object call( Scope s , Object foo[] ){
                    return (int)( ((Number)s.getThis()).doubleValue() + .5 );
                }
            } );

        _functions.set( "times" , new JSFunctionCalls1(){
                public Object call( Scope s , Object func , Object foo[] ){
                    final int t = ((Number)s.getThis()).intValue();
                    final JSFunction f = (JSFunction)func;

                    return _loop( s , f , 0 , t );
                }
            } );

        _functions.set( "upto" , new JSFunctionCalls2(){
                public Object call( Scope s , Object num , Object func , Object foo[] ){
                    final int start = ((Number)s.getThis()).intValue();
                    final int end = ((Number)num).intValue() + 1;
                    final JSFunction f = (JSFunction)func;

                    return _loop( s , f , start , end );
                }
            } );

        _functions.set( "chr" , new JSFunctionCalls0(){
                public Object call( Scope s , Object foo[] ){
                    return (char)( ((Number)s.getThis()).intValue() );
                }
            } );

        _functions.set( "zero_q_" , new JSFunctionCalls0(){
                public Object call( Scope s , Object foo[] ){
                    return ((Number)s.getThis()).doubleValue() == 0;
                }
            } );
        _functions.set( "valueOf" , new JSFunctionCalls0(){
                public Object call( Scope s , Object foo[] ){
                    Object o = s.getThis();
                    if( o instanceof JSObjectBase && 
                        ((JSObjectBase)o).getConstructor() instanceof Cons )
                        return ((Cons)((JSObjectBase)o).getConstructor())._parse( null, null );
                    
                    if( o instanceof Number )
                        return o;

                    if( !(o instanceof JSNumber ) )
                        throw new JSException( "TypeError: valueOf must be called on a Number" );
                    
                    return ((JSNumber)o)._val;
                }
            } );
        _functions.set( "toString" , new JSFunctionCalls0() {
                public Object call( Scope s, Object foo[] ) {
                    return new JSString( s.getThis().toString() );
                }
            } );

        _functions.set( "kilobytes" , new Conversion( 1024 ) );
        _functions.set( "megabytes" , new Conversion( 1024 * 1024 ) );

        _functions.set( "seconds" , new Conversion( 1000 ) );
        _functions.set( "minutes" , new Conversion( 1000 * 60 ) );
        _functions.set( "hours" , new Conversion( 1000 * 60 * 60 ) );
        _functions.set( "days" , new Conversion( 1000 * 60 * 60 * 24 ) );
        _functions.set( "weeks" , new Conversion( 1000 * 60 * 60 * 24 * 7 ) );
        _functions.set( "years" , new Conversion( 1000 * 60 * 60 * 24 * 365.25 ) );

    }

    // matches negative, infinity, int, double, scientific notation, hex
    public static String POSSIBLE_NUM = "\\s*((\\+|-)?((Infinity)|(\\d*(\\.\\d+)?([eE]-?\\d+)?)|(0[xX][\\da-fA-F]+)))?\\s*";

    public static double getDouble( Object a ) {
        return getNumber( a ).doubleValue();
    }

    public static Number getNumber( Object a ) {
        if ( a == null ) {
            return 0;
        }
        else if( a instanceof Number ) {
            return ((Number)a);
        }
        else if( a instanceof Boolean || 
                 a instanceof JSBoolean ) {
            return JSBoolean.booleanValue( a ) ? 1 : 0;
        }
        else if( a instanceof JSDate ) {
            return ((JSDate)a).getTime();
        }
        else if( ( a instanceof String || a instanceof JSString ) 
                 && a.toString().matches( POSSIBLE_NUM ) ) {
            return StringParseUtil.parseStrict(a.toString().trim());
        }
        else {
            return Double.NaN;
        }
    }

    public static long toUintX( Object o, int size ) {
        double d = getDouble( o );
        if( d == 0 ||
            Double.isNaN( d ) ||
            Double.isInfinite( d ) ) 
            return 0;

        int sign = o.toString().startsWith( "-" ) ? -1 : 1;
        return ( sign * (long)Math.floor( Math.abs( d ) ) ) % (long)Math.pow(2, size);
    }

    public static int toInt32( final Object o ) {
        long preint = toUintX( o , 32 );
        if( preint < (long)Math.pow(2,31) ) 
            return (int)preint;
        return (int)(preint - (long)Math.pow(2,32));
    }

    public static long toInt64( final Object o ) {
        long preint = toUintX( o , 64 );
        if( preint < (long)Math.pow(2,63) ) 
            return preint;
        return preint - (long)Math.pow(2,64);
    }

    public static int toUint16( final Object o ) {
        return (int)toUintX( o , 16 );
    }

    public static long toUint32( final Object o ) {
        return toUintX( o , 32 );
    }

    public static long toUint64( final Object o ) {
        return toUintX( o , 64 );
    }

    public static String formatNumber( Number o ) {
        String s = o + "";
        if( s.toUpperCase().indexOf( 'E' ) > 0 ) {
            int exp = Integer.parseInt( s.substring( s.toUpperCase().indexOf( 'E' ) + 1 ) );
            DecimalFormat df = new DecimalFormat( "#0.#" );
            df.setDecimalSeparatorAlwaysShown( false );
            df.setMaximumFractionDigits( 20 );
            df.setNegativePrefix( "-" );
            if( -7 < exp && exp < 21 ) {
                s = df.format( o );
            }
            else {
                df.applyPattern( "0.#E0" );
                df.setDecimalSeparatorAlwaysShown( false );
                df.setMaximumFractionDigits( 20 );
                df.setNegativePrefix( "-" );
                s = df.format( o );
                s = s.substring( 0, s.indexOf( "E" ) ) + "e" + ( exp > 0 ? "+" : "" ) + exp;
            }
        }
        else if( ((Number)o).doubleValue() == 0 || s.endsWith( ".0" ) ) {
            return ((Number)o).intValue() + "";
        }
        return s;
    }
}
