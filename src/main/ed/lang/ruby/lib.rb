
=begin
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
=end

class StandardError < Exception
end

class LoadError < Exception
end

def raiseLoadError(msg)
  raise LoadError , msg
end

=begin

 Module defining error codes.
 ENOENT = 1

=end
module Errno
  ENOENT = 1
end
