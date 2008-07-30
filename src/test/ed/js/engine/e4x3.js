

function myprint( s ){
    if ( ! s )
        return print( s );
        
    s = s.toString();
    s = s.replace( /[\r\n\s]+/g , "" );
    print( s );
}

xml = <nutrition>
    <dv id="1" lunch="false"><name>Breakfast</name><weight>24.0</weight></dv>
    <dv id="2" lunch="true"><name>Second breakfast</name><weight>16.3</weight></dv>
    <dv id="3" lunch="true"><name>Lunch</name><weight>2000</weight></dv>
    <dv id="4" lunch="false"><name>Dinner</name><weight>13.2</weight></dv>
    <dv id="5" lunch="false"><name>Nightcap</name><weight>2</weight></dv>
    </nutrition>;

myprint( xml.dv[0].toString() )

myprint( xml.dv[0].@lunch );
myprint( xml.dv[2].@lunch );
for(var i=0; i<5; i++) {
    myprint( xml.dv[i].@id == ((i+1)+""));
}

myprint(xml.dv[1].name.toString() );
myprint(xml.dv[4].weight.toString() );

myprint(xml.dv.toString() );


xml2 = <supplies>
  <office>
    <item req="true">Laptop</item>
    <item req="false">Paper</item>
    <item req="true"><name>Foosball</name><cost>$400</cost><enjoyment>++</enjoyment></item>
  </office>
  <desert>
    <item req="true"><name>Sunscreen</name><spf uva="true" uvb="true">85</spf></item>
    <item req="false">Tiger</item>
    <item req="true">Laptop</item>
  </desert>
</supplies>

myprint(xml2.office.item[1].@req == "false");
myprint(xml2.office.item[2].enjoyment.toString() );
myprint(xml2.office.item[2].cost.toString() );
myprint(xml2.desert.item[0].spf.@uva == xml2.desert.item[0].spf.@uvb);
myprint(xml2.desert.item[0].spf.@uva );

