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

s = _10gen.log.level
error = _10gen.log.LEVEL.ERROR
_10gen.assert.eq(str(error), "ERROR")
_10gen.log.level = _10gen.log.LEVEL.ERROR

_10gen.assert.eq(_10gen.log.level, _10gen.log.LEVEL.ERROR)
