/**
*      Copyright (C) 2008 10gen Inc.
*  
*    Licensed under the Apache License, Version 2.0 (the "License");
*    you may not use this file except in compliance with the License.
*    You may obtain a copy of the License at
*  
*       http://www.apache.org/licenses/LICENSE-2.0
*  
*    Unless required by applicable law or agreed to in writing, software
*    distributed under the License is distributed on an "AS IS" BASIS,
*    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*    See the License for the specific language governing permissions and
*    limitations under the License.
*/

package ed.js;

import java.util.Iterator;

public class JSObjectValueIterator implements Iterator<Object> {

    public JSObjectValueIterator(JSObject obj) {
        this._object = obj;
        this._keyIter = obj.keySet().iterator();
    }
    public boolean hasNext() {
        return _keyIter.hasNext();
    }

    public Object next() {
        return _object.get( _keyIter.next() );
    }

    public void remove() {
        _keyIter.remove();
    }

    private final JSObject _object;
    private final Iterator<String> _keyIter;
}
