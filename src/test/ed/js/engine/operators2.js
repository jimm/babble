var a = 5;
a |= 11;
print( a );

a = 5;
a |= 11;
print( a );

a = 5;
a |= "11";
print( a );

a = 5;
a |= "eleven";
print( a );

a = "five";
a |= 11;
print( a );

a = "five";
a |= "eleven";
print( a );

print( 5 | 11 );
print( 5 | "11" );
print( 5 | "a11" );

foo = Object();
foo.a = 5;
foo.a |= 11;
print( foo.a );

a = 4;
b = 1;
print( a >> b );

a = 4;
b = 1;
print( a << b );

print( 11 % 5 );
print( "a" % 5 );
print( 11 % "a" );
