'''
    Copyright (C) 2008 10gen Inc.
  
    This program is free software: you can redistribute it and/or  modify
    it under the terms of the GNU Affero General Public License, version 3,
    as published by the Free Software Foundation.
  
    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.
  
    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
'''

import _10gen
from _10gen import jsMap
assert jsMap.has_key('a')
assert jsMap.has_key('b')
assert jsMap.has_key('n')
assert not jsMap.has_key('c')

assert 'a' in jsMap
assert 'b' in jsMap
assert 'n' in jsMap
assert 'c' not in jsMap

d = jsMap.copy()
_10gen.assert.eq(len(jsMap.keys()), len(d.keys()))
_10gen.assert.eq(len(jsMap), len(d))

_10gen.assert.eq(d['a'], jsMap.a)
_10gen.assert.eq(d['a'], jsMap['a'])
_10gen.assert.eq(d['n'], jsMap.n)
_10gen.assert.eq(d['n'], jsMap['n'])

def compare_list_methods_by_sorted(d, j, meth):
    d_result = list(getattr(d, meth)())
    j_result = list(getattr(j, meth)())
    d_result.sort()
    j_result.sort()
    _10gen.assert.eq(d_result, j_result)

compare_list_methods_by_sorted(d, jsMap, 'iteritems')
compare_list_methods_by_sorted(d, jsMap, 'keys')
compare_list_methods_by_sorted(d, jsMap, 'values')
compare_list_methods_by_sorted(d, jsMap, 'iterkeys')

_10gen.assert.eq(jsMap.get('a'), jsMap['a'])
_10gen.assert.eq(jsMap.get('a', 'default'), jsMap['a'])
_10gen.assert.eq(jsMap.get('zzz', 'default'), 'default')
_10gen.assert.eq(jsMap.get(dict(), 'default'), 'default')


# not implemented yet
#compare_list_methods_by_sorted(d, jsMap, 'items')
_10gen.assert.throws(lambda: 
                     compare_list_methods_by_sorted(d, jsMap, 'items'))
#compare_list_methods_by_sorted(d, jsMap, 'itervalues')
_10gen.assert.throws(lambda: 
                     compare_list_methods_by_sorted(d, jsMap, 'itervalues'))

# not implemented yet
_10gen.assert.throws(lambda: jsMap.setdefault('a', 9))
#_10gen.assert.eq(jsMap.setdefault('a', 9), jsMap['a'])
#_10gen.assert.eq(jsMap.setdefault('d', 9), 9)
#_10gen.assert.eq(jsMap.setdefault('d', 10), jsMap['d'])
#_10gen.assert.throws(lambda: jsMap.setdefault([], 9))  # can't store non-strings

_10gen.assert.throws(lambda: jsMap.popitem())

_10gen.assert.throws(lambda: jsMap.pop('d', 'default'))
#_10gen.assert.eq(jsMap.pop('zzz', 'default'), 'default')
#_10gen.assert.eq(jsMap.pop('d', 'zig'), 9)
#_10gen.assert.eq(jsMap.pop('d', 'zig'), 'zig')

jsMap.update({'g': 91})
_10gen.jsCheck(jsMap, 'g', 91)

jsMap.update(h=99)
_10gen.jsCheck(jsMap, 'h', 99)

d = jsMap.copy()
oldSize = len(jsMap)
# not implemented yet
#jsMap.clear()
_10gen.assert.throws(lambda: jsMap.clear())
#_10gen.assert.eq(len(jsMap.keys()), 0)
#_10gen.assert.eq(len(jsMap), 0)
_10gen.assert.eq(oldSize, len(d))

# I can't think of a use case for this, so that it's broken is fine
_10gen.assert.throws(lambda: jsMap.fromkeys(['a', 'b', 'c'], 24))

