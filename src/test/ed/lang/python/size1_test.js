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

local.src.test.ed.lang.python.size1_helper();

/** assert that a <= b <= a + epsilon */
var within = function( a , b , epsilon ){
    assert( a <= b );
  assert( b <= a + epsilon , 'size grew by ' + (b-a));
};

// These get converted to immutable objects.

//print("boolean " + pyBoolean.approxSize());
//print("int " + pyInt.approxSize());

//print("short string " + pyString.approxSize());
//print("longer string " + pyString2.approxSize());

var start = pyDict2.approxSize();

var before = pyDict2.approxSize(); // str1: str2
assert.eq( before , pyDict2.approxSize() );
assert.eq( before , pyDict2.approxSize() );
within( start , before , 200 );


pyModifyDict2(); // str1: str2
assert.eq(pyDict2.approxSize() , before);

pyModifyDict3(); // str1: str2, str2: str2
assert(pyDict2.approxSize() > before);

var extraNode = pyDict2.approxSize();

var delta = extraNode - before; // cost of adding an entry to a hash

pyModifyDict4(); // str1: str2, str2: str1

assert.eq(extraNode, pyDict2.approxSize());


var start = pyList1.approxSize();
var before = pyList1.approxSize();
assert.eq( before , pyList1.approxSize() );
within( start , before , 200 );

var diff = before - pyList2.approxSize();
assert(diff > 0); // same object reused [1,1]

assert.eq(before , pyList3.approxSize());

var before = pyList1.approxSize();

pyList1.push(1);
pyList2.push(1);
var delta = pyList1.approxSize() - before; // cost of an extra node
assert( delta > 0 );
assert.eq(pyList1.approxSize() - pyList2.approxSize() , diff); // added to both

var before = pyList1.approxSize();
pyList1.push(3);
pyList2.push(3);
var deltaNew = pyList1.approxSize() - before; // cost of an extra node, plus another integer

assert( deltaNew > delta );
assert.eq( pyList1.approxSize() - pyList2.approxSize() , diff ); // added to both

var jxp = local.src.test.ed.lang.python.size1_helper;
var jxp2 = local.src.test.ed.lang.python.date1_helper;

var start = jxp.approxSize();
var before = jxp.approxSize();

within(start, before, 200);
//assert.eq( before , jxp.approxSize() ); // FIXME: scope grows after within()?

// jxp and jxp2 should share some stuff, so together they shouldn't be as
// big as twice each one. Add the size of [] because Array is pretty big by
// itself.
assert( 2 * jxp.approxSize() + [].approxSize() > [ jxp , jxp2 ].approxSize() );
