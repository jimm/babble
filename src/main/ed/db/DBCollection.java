// DBCollection.java

package ed.db;

import java.util.*;

import ed.js.*;
import ed.js.func.*;
import ed.js.engine.*;

public abstract class DBCollection extends JSObjectLame {
    
    public abstract JSObject save( JSObject o );
    public abstract JSObject update( JSObject q , JSObject o , boolean upsert );

    protected abstract ObjectId doapply( JSObject o );
    public abstract int remove( JSObject id );
    
    public abstract JSObject find( ObjectId id );    
    
    public abstract Iterator<JSObject> find( JSObject ref , JSObject fields );

    // ------

    public Iterator<JSObject> find( JSObject ref ){
        return find( ref , null );
    }

    public ObjectId apply( Object o ){

        if ( ! ( o instanceof JSObject ) )
            throw new RuntimeException( "can't only apply JSObject" );
        
        JSObject jo = (JSObject)o;
        jo.set( "_save" , _save );
        
        return doapply( jo );
    }

    // ------

    protected DBCollection( DBBase base , String name ){
        _base =  base;
        _name = name;

        _entries.put( "name" , _name );

        _save = new JSFunctionCalls1() {
                public Object call( Scope s , Object o , Object fooasd[] ){
                    System.out.println( "call called" );
                    if ( o == null && s.getThis() != null )
                        o = s.getThis();
                    
                    if ( ! ( o instanceof JSObject ) )
                        throw new RuntimeException( "can't only save JSObject" );
                    
                    JSObject jo = (JSObject)o;

                    
                    LinkedList<JSObject> toSearch = new LinkedList();
                    toSearch.add( jo );
                    while ( toSearch.size() > 0 ){
                        JSObject n = toSearch.remove(0);
                        for ( String name : n.keySet() ){
                            Object foo = n.get( name );
                            if ( foo == null )
                                continue;

                            System.out.println( name + "\t" + foo.getClass() );
                            

                            if ( ! ( foo instanceof JSObject ) )
                                continue;
                            
                            JSObject e = (JSObject)foo;
                            if ( e instanceof JSFileChunk ){
                                System.out.println( "found a chunk" );
                                _base.getCollection( "_chunks" ).apply( e );
                            }
                            
                            if ( e.get( "_save" ) == null ){
                                toSearch.add( e );
                                continue;
                            }
                            
                            JSFunction otherSave = (JSFunction)e.get( "_save" );
                            System.out.println( "saving embedded object : " + e.getClass() );
                            otherSave.call( s , e , null );
                            
                        }
                    }

                    if ( jo.get( "_id" ) != null ){
                        JSObject q = new JSObjectBase();
                        q.set( "_id" , jo.get( "_id" ) );
                        return update( q , jo , true );
                    }
                    
                    return save( jo );
                }
            };
        _entries.put( "save" , _save );

        _update = new JSFunctionCalls2() {
                public Object call( Scope s , Object q , Object o , Object foo[] ){
                    
                    if ( ! ( o instanceof JSObject ) )
                        throw new RuntimeException( "can't only save JSObject" );
                    
                    if ( ! ( q instanceof JSObject ) )
                        throw new RuntimeException( "can't only save JSObject" );
                    
                    return update( (JSObject)q , (JSObject)o , false );
                }
            };
        _entries.put( "update" , _update );

        _entries.put( "remove" , 
                      new JSFunctionCalls1(){
                          public Object call( Scope s , Object o , Object foo[] ){
                              
                              if ( o == null && s.getThis() != null )
                                  o = s.getThis();
                              
                              if ( ! ( o instanceof JSObject ) )
                                  throw new RuntimeException( "can't only save JSObject" );
                              
                              return remove( (JSObject)o );
                              
                          }
                      } );

                          
        
        
        _apply = new JSFunctionCalls1() {
                public Object call( Scope s , Object o , Object foo[] ){
                    return apply( o );
                }
            };
        _entries.put( "apply" , _apply );

        _find = new JSFunctionCalls2() {
                public Object call( Scope s , Object o , Object fieldsWantedO , Object foo[] ){
                    
                    if ( o == null )
                        o = new JSObjectBase();
                    
                    if ( o instanceof ObjectId )
                        return find( (ObjectId)o );

                    if ( o instanceof JSObject ){
                        Iterator<JSObject> l = find( (JSObject)o , (JSObject)fieldsWantedO );
                        if ( l == null )
                            l = (new LinkedList<JSObject>()).iterator();
                        return new DBCursor( l );
                    }
                    
                    throw new RuntimeException( "wtf : " + o.getClass() );
                }
            };
        _entries.put( "find" , _find );

        _entries.put( "findOne" , 
                      new JSFunctionCalls1() {
                          public Object call( Scope s , Object o , Object foo[] ){
                              Object res = _find.call( s , o , foo );
                              if ( res == null )
                                  return null;
                              
                              if ( res instanceof JSArray ){
                                  JSArray a = (JSArray)res;
                                  if ( a.size() == 0 )
                                      return null;
                                  return a.getInt( 0 );
                              }
                              
                              if ( res instanceof Iterator ){
                                  Iterator<JSObject> it = (Iterator<JSObject>)res;
                                  if ( ! it.hasNext() )
                                      return null;
                                  return it.next();
                              }

                              if ( res instanceof JSObject )
                                  return res;
                              
                              throw new RuntimeException( "wtf : " + res.getClass() );
                          }
                      } );

        _entries.put( "tojson" , 
                      new JSFunctionCalls0() {
                          public Object call( Scope s , Object foo[] ){
                              return "{DBCollection:" + _name + "}";
                          }
                      } );
        
    }

    public Object get( Object n ){
        if ( n == null )
            return null;
        return _entries.get( n.toString() );
    }

    final DBBase _base;
    
    final JSFunction _save;
    final JSFunction _update;
    final JSFunction _apply;
    final JSFunction _find;

    protected Map _entries = new TreeMap();
    final protected String _name;
}
