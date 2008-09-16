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

package ed.lang.ruby;

import java.util.*;

import org.jruby.*;
import org.jruby.internal.runtime.methods.JavaMethod;
import org.jruby.runtime.*;
import org.jruby.runtime.builtin.IRubyObject;
import static org.jruby.runtime.Visibility.PUBLIC;

import ed.js.*;
import ed.js.engine.Scope;

/**
 * RubyJSFunctionWrapper acts as a bridge between Ruby code and JavaScript
 * functions. An instance of RubyJSFunctionWrapper is a Ruby object that can
 * call a JSFunction. If we are given a function name and a class, then a
 * method with that name is added to the class.
 *
 * @see JSFunctionWrapper
 */
public class RubyJSFunctionWrapper extends RubyJSObjectWrapper {

    static Map<Ruby, RubyClass> klassDefs = new WeakHashMap<Ruby, RubyClass>();

    private final JSFunction _func;
    private final JSObject _this;
    private RubyClass _klazz;

    public static synchronized RubyClass getJSFunctionClass(Ruby runtime) {
	RubyClass jsFunctionClass = klassDefs.get(runtime);
	if (jsFunctionClass == null) {
	    jsFunctionClass = runtime.defineClass("JSFunction", RubyJSObjectWrapper.getJSObjectClass(runtime), ObjectAllocator.NOT_ALLOCATABLE_ALLOCATOR);
	    jsFunctionClass.kindOf = new RubyModule.KindOf() {
		    public boolean isKindOf(IRubyObject obj, RubyModule type) {
			return obj instanceof RubyJSFunctionWrapper;
		    }
		};
	    klassDefs.put(runtime, jsFunctionClass);
	}
	return jsFunctionClass;
    }

    RubyJSFunctionWrapper(Scope s, Ruby runtime, JSFunction obj, String name, RubyClass eigenclass) {
	this(s, runtime, obj, name, eigenclass, null);
    }

    RubyJSFunctionWrapper(Scope s, Ruby runtime, JSFunction obj, String name, RubyClass eigenclass, JSObject jsThis) {
	super(s, runtime, obj, getJSFunctionClass(runtime));
	if (RubyObjectWrapper.DEBUG)
	    System.err.println("  creating RubyJSFunctionWrapper named " + name);
	_func = obj;
	_this = jsThis;
	JavaMethod jm = _makeCallMethod(eigenclass);
	if (name != null && name.length() > 0) {
	    if (eigenclass != null)
		_addMethod(name, jm, eigenclass);
	    if (isRubyClassName(name) && runtime.getClass(name) == null)
		_createJSObjectSubclass(s, name);
	}
	else if (eigenclass != null)
	    eigenclass.addMethod("call", jm);
    }

    boolean isRubyClassName(String name) {
	return name != null && name.length() > 0 && Character.isUpperCase(name.charAt(0));
    }

    protected JavaMethod _makeCallMethod(RubyClass klazz) {
	if (klazz == null)
	    return null;
	return new JavaMethod(klazz, PUBLIC) {
	    public IRubyObject call(ThreadContext context, IRubyObject self, RubyModule clazz, String name, IRubyObject[] args, Block block) {
		Ruby runtime = context.getRuntime();
		if (RubyObjectWrapper.DEBUG)
		    System.err.println("calling method " + clazz.getName() + "#" + name + " with " + args.length + " args");
		try {
		    Object result = _func.callAndSetThis(_scope, _this, RubyObjectWrapper.toJSFunctionArgs(_scope, runtime, args, 0, block));
		    if (RubyObjectWrapper.DEBUG)
			System.err.println("func " + name + " returned " + result + ", which is " + (result == null ? "null" : ("of class " + result.getClass().getName())));
		    return toRuby(result);
		}
		catch (Exception e) {
		    self.callMethod(context, "raise", new IRubyObject[] {RubyString.newString(runtime, e.toString())}, Block.NULL_BLOCK);
		    return runtime.getNil(); // will never reach
		}
	    }
	};
    }

    /** Adds this method to <var>klazz</var>. */
    protected void _addMethod(String name, JavaMethod jm, RubyClass klazz) {
	if (RubyObjectWrapper.DEBUG)
	    System.err.println("adding method named " + name + " to eigenclass " + klazz.getName());
	final String internedName = name.intern();
	klazz.addMethod(internedName, jm);
	klazz.callMethod(getRuntime().getCurrentContext(), "method_added", getRuntime().fastNewSymbol(internedName));
    }

    /** An allocator for objects created using JSFunction constructors. */
    class JSObjectAllocator implements ObjectAllocator {
	public IRubyObject allocate(Ruby runtime, RubyClass klass) {
	    if (RubyObjectWrapper.DEBUG)
		System.err.println("allocating an instance of " + klass.name());
	    JSObject jsobj = _func.newOne();
	    RubyObject r = (RubyObject)new RubyJSObjectWrapper(_scope, runtime, jsobj, _klazz);
	    // Eigenclass has been created and used in RubyJSObjectWrapper ctor, so make that the metaclass here
	    r.makeMetaClass(r.getSingletonClass());
	    if (RubyObjectWrapper.DEBUG)
		System.err.println("  wrapped new object inside a " + r.getClass().getName());
	    return r;
	}
    }
    protected JSObjectAllocator jsObjectAllocator = new JSObjectAllocator();

    /** Creates a subclass of JSObject for that uses this method as its constructor. */
    protected void _createJSObjectSubclass(final Scope scope, final String name) {
	if (RubyObjectWrapper.DEBUG)
	    System.err.println("adding class named " + name);
	_klazz = getRuntime().defineClass(name, RubyJSObjectWrapper.getJSObjectClass(getRuntime()), jsObjectAllocator);
	_klazz.kindOf = new RubyModule.KindOf() {
		    public boolean isKindOf(IRubyObject obj, RubyModule type) {
			return obj instanceof RubyJSObjectWrapper;
		    }
	    };
	_klazz.addMethod("initialize", new JavaMethod(_klazz, PUBLIC) {
                public IRubyObject call(ThreadContext context, IRubyObject self, RubyModule clazz, String name, IRubyObject[] args, Block block) {
		    if (RubyObjectWrapper.DEBUG) {
			System.err.println("calling method " + clazz.getName() + ".initialize with " + args.length + " args:");
			for (IRubyObject iro : args) System.err.println("  " + iro.toString());
		    }
		    JSObject jsobj = ((RubyJSObjectWrapper)self).getJSObject();
		    try {
			_func.callAndSetThis(_scope, jsobj, RubyObjectWrapper.toJSFunctionArgs(scope, context.getRuntime(), args, 0, block)); // initialize it by calling _func
		    }
		    catch (Exception e) {
			self.callMethod(context, "raise", new IRubyObject[] {RubyString.newString(context.getRuntime(), e.toString())}, Block.NULL_BLOCK);
		    }
		    return self;
                }
                @Override public Arity getArity() { return Arity.OPTIONAL; }
	    });
    }
}
