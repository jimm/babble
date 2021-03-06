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

package ed.lang.ruby;

import org.jruby.*;
import org.jruby.runtime.*;
import org.jruby.runtime.builtin.IRubyObject;

import ed.js.JSArray;
import ed.js.engine.Scope;
import static ed.lang.ruby.RubyObjectWrapper.toJS;
import static ed.lang.ruby.RubyObjectWrapper.toRuby;

/**
 * RubyJSArrayWrapper acts as a bridge between Ruby arrays and JSArrays. An
 * instance of RubyJSArrayWrapper is a Ruby object that turns reads and writes
 * of Ruby array contents into reads and writes of the underlying JSArray's
 * instance variables.
 */
@SuppressWarnings("serial")
public class RubyJSArrayWrapper extends RubyArray {

    private Scope _scope;
    private JSArray _jsarray;

    public static synchronized RubyClass getJSArrayClass(Ruby runtime) {
        RubyClass klazz = runtime.getClass("JSArray");
        if (klazz == null) {
            klazz = runtime.defineClass("JSArray", runtime.getArray(), ObjectAllocator.NOT_ALLOCATABLE_ALLOCATOR);
            klazz.kindOf = new RubyModule.KindOf() {
                    public boolean isKindOf(IRubyObject obj, RubyModule type) {
                        return obj instanceof RubyJSArrayWrapper;
                    }
                };
        }
        return klazz;
    }

    RubyJSArrayWrapper(Scope s, Ruby runtime, JSArray obj) {
        super(runtime, getJSArrayClass(runtime));
        if (RubyObjectWrapper.DEBUG_CREATE)
            System.err.println("  creating RubyJSArrayWrapper");
        _scope = s;
        _jsarray = obj;
        js2ruby();
    }

    public JSArray getJSArray() { return _jsarray; }

    public IRubyObject initialize(ThreadContext context, Block block) {
        IRubyObject o = super.initialize(context, block);
        ruby2js();
        return o;
    }

    public IRubyObject initialize(ThreadContext context, IRubyObject arg0, Block block) {
        IRubyObject o = super.initialize(context, arg0, block);
        ruby2js();
        return o;
   }

    public IRubyObject initialize(ThreadContext context, IRubyObject arg0, IRubyObject arg1, Block block) {
        IRubyObject o = super.initialize(context, arg0, arg1, block);
        ruby2js();
        return o;
 }

    public IRubyObject replace(IRubyObject orig) {
        IRubyObject o = super.replace(orig);
        ruby2js();
        return o;
    }

    public IRubyObject insert(IRubyObject arg1, IRubyObject arg2) {
        IRubyObject o = super.insert(arg1, arg2);
        ruby2js();
        return o;
    }

    public IRubyObject insert(IRubyObject[] args) {
        IRubyObject o = super.insert(args);
        ruby2js();
        return o;
    }

    public RubyArray append(IRubyObject item) {
        RubyArray o = super.append(item);
        _jsarray.add(toJS(_scope, item));
        return o;
    }

    public RubyArray push_m(IRubyObject[] items) {
        RubyArray o = super.push_m(items);
        ruby2js();
        return o;
    }

    public IRubyObject pop(ThreadContext context) {
        IRubyObject o = super.pop(context);
        _jsarray.remove(_jsarray.size() - 1);
        return o;
    }

    public IRubyObject pop19(ThreadContext context, IRubyObject num) {
        IRubyObject o = super.pop19(context, num);
        int n = (int)((RubyNumeric)num).getLongValue();
        for (int i = 0; i < n; ++i)
            _jsarray.remove(_jsarray.size() - 1);
        return o;
    }

    public IRubyObject shift(ThreadContext context) {
        IRubyObject o = super.shift(context);
        _jsarray.remove(0);
        return o;
    }

    public IRubyObject shift19(ThreadContext context, IRubyObject num) {
        IRubyObject o = super.shift19(context, num);
        int n = (int)((RubyNumeric)num).getLongValue();
        for (int i = 0; i < n; ++i)
            _jsarray.remove(0);
        return o;
    }

    public RubyArray unshift_m(IRubyObject[] items) {
        RubyArray o = super.unshift_m(items);
        ruby2js();
        return o;
    }

    public IRubyObject aset(IRubyObject arg0, IRubyObject arg1) {
        IRubyObject o = super.aset(arg0, arg1);
        ruby2js();
        return o;
    }

    public IRubyObject aset(IRubyObject arg0, IRubyObject arg1, IRubyObject arg2) {
        IRubyObject o = super.aset(arg0, arg1, arg2);
        ruby2js();
        return o;
    }

    public RubyArray concat(IRubyObject obj) {
        RubyArray o = super.concat(obj);
        ruby2js();
        return o;
    }

    public IRubyObject compact_bang() {
        IRubyObject o = super.compact_bang();
        ruby2js();
        return o;
    }

    public IRubyObject rb_clear() {
        IRubyObject o = super.rb_clear();
        _jsarray.clear();
        return o;
    }

    public IRubyObject fill(ThreadContext context, Block block) {
        IRubyObject o = super.fill(context, block);
        ruby2js();
        return o;
    }


    public IRubyObject fill(ThreadContext context, IRubyObject arg, Block block) {
        IRubyObject o = super.fill(context, arg, block);
        ruby2js();
        return o;
    }


    public IRubyObject fill(ThreadContext context, IRubyObject arg1, IRubyObject arg2, Block block) {
        IRubyObject o = super.fill(context, arg1, arg2, block);
        ruby2js();
        return o;
    }

    public IRubyObject fill(ThreadContext context, IRubyObject arg1, IRubyObject arg2, IRubyObject arg3, Block block) {
        IRubyObject o = super.fill(context, arg1, arg2, arg3, block);
        ruby2js();
        return o;
    }

    public IRubyObject reverse_bang() {
        IRubyObject o = super.reverse_bang();
        ruby2js();
        return o;
    }

    public RubyArray collect_bang(ThreadContext context, Block block) {
        RubyArray o = super.collect_bang(context, block);
        ruby2js();
        return o;
    }

    public IRubyObject delete(ThreadContext context, IRubyObject item, Block block) {
        IRubyObject o = super.delete(context, item, block);
        ruby2js();
        return o;
    }

    public IRubyObject delete_at(IRubyObject obj) {
        IRubyObject o = super.delete_at(obj);
        ruby2js();
        return o;
    }

    public IRubyObject reject_bang(ThreadContext context, Block block) {
        IRubyObject o = super.reject_bang(context, block);
        ruby2js();
        return o;
    }

    public IRubyObject slice_bang(IRubyObject arg0) {
        IRubyObject o = super.slice_bang(arg0);
        ruby2js();
        return o;
    }

    public IRubyObject slice_bang(IRubyObject arg0, IRubyObject arg1) {
        IRubyObject o = super.slice_bang(arg0, arg1);
        ruby2js();
        return o;
    }

    public IRubyObject flatten_bang(ThreadContext context) {
        IRubyObject o = super.flatten_bang(context);
        ruby2js();
        return o;
    }

    public IRubyObject uniq_bang() {
        IRubyObject o = super.uniq_bang();
        ruby2js();
        return o;
    }

    public RubyArray sort_bang(Block block) {
        RubyArray o = super.sort_bang(block);
        ruby2js();
        return o;
    }

    /** Writes contents of JSArray into RubyArray. */
    private void js2ruby() {
        int len = _jsarray.size();
        IRubyObject[] a = new IRubyObject[len];
        for (int i = 0; i < len; ++i)
            a[i] = toRuby(_scope, getRuntime(), _jsarray.get(i));
        replace(RubyArray.newArray(getRuntime(), a));
    }

    /** Writes contents of RubyArrray into JSArray. */
    private void ruby2js() {
        _jsarray.clear();
        int len = size();
        for (int i = 0; i < len; ++i)
            _jsarray.add(toJS(_scope, entry(i)));
    }
}
