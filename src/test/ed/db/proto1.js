
db = connect( "test" )



db.getCollectionPrototype().doSomething = function(){
    lastName = this.getName();
    full = this.getFullName();
    return 7;
}

assert( 7 == db.foo.doSomething() );
assert( "foo" == lastName );
assert( "test.foo" == full );

assert( 7 == db.a.foo.doSomething() );
assert( "a.foo" == lastName );
assert( "test.a.foo" == full );

assert( db.nutty.getClass().toString().match( /Collection/ ) );
db.nutty = 123;
assert( 123 == db.nutty );
