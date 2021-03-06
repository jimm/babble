// set tests

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

var french = <france><food>fromage</food><food>haricots verts</food><language>francais</language></france>;

french.capital = "paris";

french.food[0] = "pain";
french.food[20] = "pomme";

french.language.accent = "parisian";

print(french);

french.food = "fromage"

french.places = <books><usr>livre</usr><bin>elles</bin></books>

print(french);

xml = <x>y</x>;
xml.foo.@bar = "lalala";
xml.fooy.bar.blah[0] = "fooy!";
xml.bar.foo.bar.foo[0] = <such>a pita</such>;
print( xml );

// Make sure it's not copied if it's XML
x = <a><b>two</b></a>;
y = XML(x);
x.b = "three";
print(y);

x = XML("4");
print( x + " " + (x == 4) );

x = new XML(<foo><bar>hi</bar></foo>);
y = new XML("<foo><bar>hi</bar></foo>");
print( x == y );

x = <hello a='\"' />;
print( x.toXMLString() );

x = <alpha>
    <bravo>one</bravo>
    <charlie>two</charlie>
</alpha>;
x.insertChildAfter(x.bravo[0], <delta>three</delta>);
x.insertChildAfter(null, <delta>three</delta>);
print( x );

x = <alpha>
    <bravo>one</bravo>
    <charlie>two</charlie>
</alpha>;
x.insertChildBefore(x.bravo[0], <delta>three</delta>)
x.insertChildBefore(null, <delta>three</delta>);
print( x );

x = new XML();
print( x.nodeKind());
print( XML.prototype.nodeKind());
