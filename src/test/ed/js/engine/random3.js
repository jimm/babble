
print( 0["a"] = 5 );
print( 0["a"] == null );

a = null;

print( (a||0)["a"] == null );

for ( a in 0 )
    print( a );
