// MANIPULATING CHILDREN

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

xml = <actors>
  <actor attr="rand">
    <name>Jeff</name>
    <born>1930</born>
    <film>Old Yeller</film>
    <film>Zorro</film>
  </actor>
</actors>

//appendChild
xml.appendChild(<singer>Joni Mitchell</singer>);
print( xml.actor.name.appendChild(" York") );

//insertChildAfter
//xml.actor.insertChildAfter(xml.singer, <artist>Renoir</artist>);

//insertChildBefore
//xml.actor.born.insertChildBefore(xml.singer, "lose");
//xml.actor.film[0].insertChildBefore(xml.singer, <some><really><nested>thing</nested></really></some>);

//prependChild
xml.actor.prependChild(<name>me</name>);

//setChildren
xml.actor.film[1].setChildren(<awesome>Zorro</awesome>);

print( xml );

//childIndex
print( xml.singer.childIndex() );
print( xml.actor.born.childIndex() );

//child
print( xml.child("singer") );

//children
print( xml.actor.film[1].children() );
print( xml.actor.children() );
