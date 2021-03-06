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

package ed.appserver.templates.djang10;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Pattern;

import ed.appserver.JSFileLibrary;
import ed.ext.org.mozilla.javascript.CompilerEnvirons;
import ed.ext.org.mozilla.javascript.Node;
import ed.ext.org.mozilla.javascript.Token;
import ed.js.JSArray;
import ed.js.JSException;
import ed.js.JSFunction;
import ed.js.JSMap;
import ed.js.JSNumericFunctions;
import ed.js.JSObject;
import ed.js.JSObjectBase;
import ed.js.JSObjectSize;
import ed.js.JSString;
import ed.js.engine.Scope;
import ed.js.func.JSFunctionCalls0;
import ed.js.func.JSFunctionCalls1;
import ed.lang.python.JSPyObjectWrapper;
import ed.log.Level;
import ed.log.Logger;
import ed.util.SeenPath;

public class Expression extends JSObjectBase {
    private final Logger log;

    private static final Set<Integer> SUPPORTED_TOKENS = new HashSet<Integer>(Arrays.asList(
        Token.GETELEM,
        Token.GETPROP,
        Token.CALL,
        Token.NAME,
        Token.ARRAYLIT,
        Token.STRING,
        Token.POS,
        Token.NEG,
        Token.NUMBER,
        Token.NULL,
        Token.TRUE,
        Token.FALSE
    ));

    private static final String[] JAVASCRIPT_RESERVED_WORDS = {
            //JavaScript Reserved Words
            "break",
            "case",
            "continue",
            "default",
            "delete",
            "do",
            "else",
            "export",
            "for",
            "function",
            "if",
            "in",   //TODO:implement
            "let",
            "label",
            "new",  //TODO:implement
            "return",
            "switch",
            "this",
            "typeof", //TODO: implement this
            "var",
            "void",
            "while",
            "with",
            "yield",

            //Java Keywords (Reserved by JavaScript)
            "abstract",
            "boolean",
            "byte",
            "catch",
            "char",
            "class",
            "const",
            "debugger",
            "double",
            "enum",
            "extends",
            "final",
            "finally",
            "float",
            "goto",
            "implements",
            "import",
            "instanceof",   //TODO implement
            "int",
            "interface",
            "long",
            "native",
            "package",
            "private",
            "protected",
            "public",
            "short",
            "static",
            "super",
            "synchronized",
            "throw",
            "throws",
            "transient",
            "try",
            "volatile"
    };

    private String expression;
    private final ed.appserver.templates.djang10.Parser.Token token;
    private Node parsedExpression;
    private Boolean isLiteral;

    public Expression(String expression, ed.appserver.templates.djang10.Parser.Token token, boolean useLiteralEscapes) {
        super(CONSTRUCTOR);
        this.log = Logger.getRoot().getChild("djang10").getChild("expression");
        this.token = token;
        isLiteral = null;
        this.expression = expression;

        CompilerEnvirons ce = new CompilerEnvirons();
        ed.ext.org.mozilla.javascript.Parser parser = new ed.ext.org.mozilla.javascript.Parser(ce, ce.getErrorReporter());
        Node scriptNode;

        String processedExpr = preprocess(expression, useLiteralEscapes);
        try {
            scriptNode = parser.parse(processedExpr, "foo", 0);
            scriptNode = postprocess(scriptNode);
        } catch (Exception t) {
            String msg;

            if(log.getEffectiveLevel().compareTo(Level.DEBUG) <= 0)
                msg = "Failed to parse original expression [" + expression + "] Processed expr: [" + processedExpr + "]";
            else
                msg = "Failed to parse expression: [" + expression + "]";

            throw new TemplateSyntaxError(msg, token, t);
        }

        if (scriptNode.getFirstChild() != scriptNode.getLastChild())
            throw new TemplateSyntaxError("Only one expression is allowed. ["+expression + "]", token);

        parsedExpression = scriptNode.getFirstChild();

        if (parsedExpression.getType() != ed.ext.org.mozilla.javascript.Token.EXPR_RESULT)
            throw new TemplateSyntaxError("Not an expression [" + expression + "]", token);

        //Verify the expression
        Queue<Node> workQueue = new LinkedList<Node>();
        workQueue.add(parsedExpression.getFirstChild());

        while(!workQueue.isEmpty()) {
            Node jsToken = workQueue.remove();
            if(!SUPPORTED_TOKENS.contains(jsToken.getType()))
                throw new TemplateSyntaxError("Failed to parse expression [" + expression +"] Unsupported token: " + jsToken, this.token);

            for(jsToken = jsToken.getFirstChild(); jsToken !=null; jsToken = jsToken.getNext())
                workQueue.add(jsToken);
        }

    }

    public ed.appserver.templates.djang10.Parser.Token getToken() {
        return token;
    }

    private static List<String> splitLiterals(String str) {
        List<String> bits = new ArrayList<String>();

        String quotes = "\"'";
        boolean inQuote = false, lastWasEscape = false;
        char quoteChar = '"';
        StringBuilder buffer = new StringBuilder();

        for(int i=0; i<str.length(); i++) {
            char c = str.charAt(i);

            if(inQuote) {
                buffer.append(c);

                if(!lastWasEscape) {
                    if(c == quoteChar) {
                        inQuote = false;
                        bits.add(buffer.toString());
                        buffer.setLength(0);
                    }
                    else if(c == '\\') {
                        lastWasEscape = true;
                    }
                }
                else {
                    lastWasEscape = false;
                }
            }
            else if(quotes.indexOf(c) > -1) {
                inQuote = true;
                if(buffer.length() != 0) {
                    bits.add(buffer.toString());
                    buffer.setLength(0);
                }

                buffer.append(c);
            }
            else {
                buffer.append(c);
            }
        }

        if(buffer.length() != 0)
            bits.add(buffer.toString());

        return bits;
    }

    public String preprocess(String exp, boolean useLiteralEscapes) {
        StringBuilder buffer = new StringBuilder();
        String quotes = "\"'";

        Pattern numAlphaIdentifiers = Pattern.compile("(?<!\\w)[0-9_]\\w*[A-Za-z_$]\\w*(?!\\w)"); //matches all identifiers that start with a number
        Pattern numericProp = Pattern.compile("((?:[A-Za-z]\\w*|\\]|\\))\\s*\\.\\s*)([0-9]+)(?![0-9])");    //matches variable.8

        for(String bit : splitLiterals(exp)) {
            boolean isQuoted = (quotes.indexOf(bit.charAt(0)) > -1) && (bit.charAt(0) == bit.charAt(bit.length() - 1));

            if(!isQuoted) {
                bit = numAlphaIdentifiers.matcher(bit).replaceAll("_$0");
                for(String reservedWord : JAVASCRIPT_RESERVED_WORDS)
                    bit = bit.replaceAll("(?<!\\w)"+reservedWord+"(?!\\w)", "_$0");
                bit = numericProp.matcher(bit).replaceAll("$1_$2");
            }
            else {
                //kill escapes
                if(!useLiteralEscapes)
                    bit = bit.replaceAll("\\\\(?!["+quotes+"])", "\\\\\\\\");

                if(bit.charAt(1) == '_'){
                    bit = bit.charAt(0) + "_" + bit.substring(1);
                }
            }

            buffer.append(bit);
        }

        if(!buffer.toString().equals(exp))
            log.debug("Transformed [" + exp + "] to: [" + buffer + "]. " + "(" + token.getOrigin() + ":" + token.getStartLine() + ")");


        return buffer.toString();
    }
    private Node postprocess(Node node) {
        if(node.getType() == Token.NAME || node.getType() == Token.STRING) {
            String str = node.getString();

            if(str.startsWith("_")) {
                Node newNode = Node.newString(node.getType(), str.substring(1));

                for(Node child = node.getFirstChild(); child != null; child = child.getNext())
                    newNode.addChildToBack(child);

                node = newNode;
            }
        }

        for(Node child = node.getFirstChild(); child != null; child = child.getNext()) {
            Node newChild = postprocess(child);
            if(newChild != child)
                node.replaceChild(child, newChild);
            child = newChild;
        }

        return node;
    }



    public boolean is_literal() {
        if(isLiteral == null)
            isLiteral = is_literal(this.parsedExpression);

        return isLiteral;
    }
    private static boolean is_literal(Node node) {
        if(node.getType() == Token.NAME)
            return false;

        for(node = node.getFirstChild(); node != null; node = node.getNext())
            if(!is_literal(node))
                return false;

        return true;
    }
    public Object get_literal_value() {
        if(!is_literal())
            throw new IllegalStateException("Expression is not a literal [" + expression + "]");

        return resolve(null, null);
    }


    public Object resolve(Scope scope, Context ctx) {

        Object obj = resolve(scope, ctx, parsedExpression.getFirstChild(), true);

        obj = JSNumericFunctions.fixType(obj);

        if(is_literal() && (obj instanceof JSObject))
            JSHelper.mark_safe((JSObject)obj);

        return obj;
    }
    private Object resolve(Scope scope, Context ctx, Node node, boolean autoCall) {
        Object temp;
        switch (node.getType()) {
        case Token.GETELEM:
        case Token.GETPROP:
            //get the object
            try {
                temp = resolve(scope, ctx, node.getFirstChild(), true);
            } catch(VariableDoesNotExist e) {
                e.setSameAsJsNull(false);
                throw e;
            }
            if(temp == null)
                throw new VariableDoesNotExist("Can't get a property from a null object", this, toString(node));

            if(!(temp instanceof JSObject))
                throw new VariableDoesNotExist("Can't get properties from native objects of type [" + temp.getClass().getName() + "]", this, toString(node));
            JSObject obj = (JSObject)temp;

            //get the property name
            Object prop;
            try {
                prop = resolve(scope, ctx, node.getLastChild(), true);
            } catch(VariableDoesNotExist e) {
                e.setSameAsJsNull(false);
                throw e;
            }
            if(prop == null)
                throw new VariableDoesNotExist("Can't get null property", this, toString(node));

            //get the property
            Object val = obj.get(prop);
            if(val == null && !obj.containsKey(prop.toString())) {
                VariableDoesNotExist e = new VariableDoesNotExist("Object doesn't contain the property ["+prop.toString()+"]", this, toString(node));
                e.setSameAsJsNull(true);
                throw e;
            }

            if (autoCall && (val instanceof JSFunction) && ((JSFunction)val).isCallable()) {
                try {
                    val = ((JSFunction)val).callAndSetThis(scope, obj, new Object[0]);
                }
                catch(JSException e) {
                    temp = e.getObject();
                    if(temp instanceof JSObject && isTrue( ((JSObject)temp).get("silent_variable_failure") ))
                        throw new VariableDoesNotExist("Failed to autocall the property [" + prop.toString() + "]", this, toString(node), e);

                    throw e;
                }
            }

            return val;

        case Token.CALL:
            JSObject callThisObj = null;
            JSFunction callMethodObj = null;
            Node callArgs = node.getFirstChild();

            if (callArgs.getType() == Token.GETELEM || callArgs.getType() == Token.GETPROP) {
                //get the object
                try {
                    callThisObj = (JSObject)resolve(scope, ctx, callArgs.getFirstChild(), true);
                } catch(VariableDoesNotExist e) {
                    NullPointerException npe = new NullPointerException(VariableDoesNotExist.format("Can't call methods on nonexistent objects", this, toString(node)));
                    npe.initCause(e);
                    throw npe;
                }
                if(callThisObj == null)
                    throw new NullPointerException(VariableDoesNotExist.format("Can't call methods on null objects", this, toString(node)));

                //get the method name
                Object propName;
                try {
                    propName = resolve(scope, ctx, callArgs.getLastChild(), callArgs.getType() == Token.GETELEM);
                } catch(VariableDoesNotExist e) {
                    NullPointerException npe = new NullPointerException(VariableDoesNotExist.format("Can't call methods with nonexistent names", this, toString(node)));
                    npe.initCause(e);
                    throw npe;
                }
                if(propName == null) {
                    throw new NullPointerException(VariableDoesNotExist.format("Can't call methods with null name", this, toString(node)));
                }
                temp = callThisObj.get(propName);

                if(temp == null) {
                    String msg = callThisObj.containsKey(propName.toString())? "Can't call null method" : "Can't call nonexistent method";
                    throw new NullPointerException(VariableDoesNotExist.format(msg, this, toString(node)));
                }
                if(!(temp instanceof JSFunction))
                    throw new IllegalArgumentException(VariableDoesNotExist.format("Can only call functions, got [" + temp.getClass() + "]", this, toString(node)));
                callMethodObj = (JSFunction)temp;

            } else {
                try {
                    temp = resolve(scope, ctx, callArgs, false);
                } catch(VariableDoesNotExist e) {
                    NullPointerException npe = new NullPointerException(VariableDoesNotExist.format("Can't call nonexistent functions", this, toString(node)));
                    npe.initCause(e);
                    throw npe;
                }
                if(temp == null)
                    throw new NullPointerException(VariableDoesNotExist.format("Can't call null functions", this, toString(node)));
                if(!(temp instanceof JSFunction))
                    throw new NullPointerException(VariableDoesNotExist.format("Can only call functions, got [" + temp.getClass() + "]", this, toString(node)));
                callMethodObj = (JSFunction)temp;
            }

            // arguments
            List<Object> argList = new ArrayList<Object>();
            for(callArgs = callArgs.getNext(); callArgs != null; callArgs = callArgs.getNext()) {
                try {
                    argList.add(resolve(scope, ctx, callArgs, true));
                }
                catch(VariableDoesNotExist e) {
                    if(e.isSameAsJsNull()) {
                        argList.add(null);
                    }
                    else {
                        NullPointerException npe = new NullPointerException(VariableDoesNotExist.format("Method parameter#"+argList.size() +  " can't contain undefined variables", this, toString(callArgs)));
                        npe.initCause(e);
                        throw npe;
                    }
                }
            }

            try {
                if(scope != null)
                    return callMethodObj.callAndSetThis(scope, callThisObj, argList.toArray());
                else
                    return callMethodObj.call(scope, argList.toArray());
            }
            catch(JSException e) {
                temp = e.getObject();
                if(temp instanceof JSObject && isTrue( ((JSObject)temp).get("silent_variable_failure") ))
                    throw new VariableDoesNotExist("Failed to call method", this, toString(node), e);

                throw e;
            }


        case Token.ARRAYLIT:
            JSArray arrayLit = new JSArray();

            for(Node arrElem = node.getFirstChild(); arrElem != null; arrElem = arrElem.getNext()) {
                try {
                    arrayLit.add(resolve(scope, ctx, arrElem, true));
                } catch(VariableDoesNotExist e) {
                    if(e.isSameAsJsNull()) {
                        arrayLit.add(null);
                    } else {
                        NullPointerException npe = new NullPointerException(VariableDoesNotExist.format("Array literal element#"+arrayLit.size() +  " can't contain undefined variables", this, toString(arrElem)));
                        npe.initCause(e);
                        throw npe;
                    }
                }
            }

            return arrayLit;


        case Token.NAME:
            Object lookupValue = ctx.get(node.getString());
            boolean lookupValueDoesNotExist = (lookupValue == null) && !ctx.containsKey(node.getString());

            boolean use_fallabck = true;
            if(ctx.get("__use_globals") instanceof Boolean) {
                use_fallabck = (Boolean)ctx.get("__use_globals");
            }
            else if(JSHelper.get(scope).get("ALLOW_GLOBAL_FALLBACK") instanceof Boolean) {
                use_fallabck = (Boolean)JSHelper.get(scope).get("ALLOW_GLOBAL_FALLBACK");
            }

            // XXX: fallback on scope look ups
            if (use_fallabck && lookupValueDoesNotExist) {
                lookupValue = scope.get(node.getString());
                lookupValueDoesNotExist = (lookupValue == null) && !scope.containsKey(node.getString(), true);
            }
            if(lookupValueDoesNotExist) {
                VariableDoesNotExist e = new VariableDoesNotExist("Failed to lookup variable", this, toString(node));
                e.setSameAsJsNull(true);
                throw e;
            }


            if (autoCall
                && (lookupValue instanceof JSFunction)
                && !(lookupValue instanceof JSFileLibrary)
                && (
                    !(lookupValue instanceof JSPyObjectWrapper)
                    || ((JSPyObjectWrapper)lookupValue).isCallable()
                    )
               )

                try {
                    lookupValue = ((JSFunction) lookupValue).call(scope);
                }
                catch(JSException e) {
                    temp = e.getObject();
                    if(temp instanceof JSObject && isTrue( ((JSObject)temp).get("silent_variable_failure") ))
                        throw new VariableDoesNotExist("Failed to call method", this, toString(node), e);

                    throw e;
                }
            return lookupValue;

        case Token.STRING:
            return new JSString(node.getString());

        case Token.POS:
            return resolve(scope, ctx, node.getFirstChild(), true);

        case Token.NEG:
            temp = resolve(scope, ctx, node.getFirstChild(), true);
            return (temp instanceof Double)?  -1 * ((Double)temp) :  -1 * ((Integer)temp);

        case Token.NUMBER:
            double n = node.getDouble();
            if (JSNumericFunctions.couldBeInt(n))
                return (int)n;
            else
                return n;

        case Token.NULL:
            return null;


        case Token.TRUE:
            return true;


        case Token.FALSE:
            return false;


        default:
            //Should never happen
            throw new IllegalStateException("Can't resolve the token: " + Token.name(node.getType()));
        }
    }

    public String toString() {
        return expression;
    }

    private static String toString(Node node) {
        StringBuilder buffer = new StringBuilder();
        toString(node, buffer);
        return buffer.toString();
    }
    private static void toString(Node node, StringBuilder buffer) {
        switch(node.getType()) {
        case Token.GETELEM:
            toString(node.getFirstChild(), buffer);
            buffer.append("[");
            toString(node.getLastChild(), buffer);
            buffer.append("]");
            break;

        case Token.GETPROP:
            toString(node.getFirstChild(), buffer);
            buffer.append(".");
            buffer.append(node.getLastChild().getString());
            break;

        case Token.CALL:
            toString(node.getFirstChild(), buffer);
            buffer.append("(");
            boolean isFirstCallArg = true;
            for(Node arg = node.getFirstChild().getNext(); arg != null; arg = arg.getNext()) {
                if(!isFirstCallArg)
                    buffer.append(",");
                isFirstCallArg = false;

                toString(arg, buffer);
            }
            buffer.append(")");
            break;

        case Token.NAME:
            buffer.append(node.getString());
            break;

        case Token.ARRAYLIT:
            boolean isFirstElm = true;
            buffer.append("[");
            for(Node elm = node.getFirstChild(); elm != null; elm = elm.getNext()) {
                if(!isFirstElm)
                    buffer.append(",");
                isFirstElm = false;

                toString(elm, buffer);
            }
            buffer.append("]");
            break;

        case Token.STRING:
            buffer.append("\"" + node.getString().replace("\"", "\\\"") + "\"");
            break;

        case Token.POS:
            buffer.append("+");
            toString(node.getFirstChild(), buffer);
            break;

        case Token.NEG:
            buffer.append("-");
            toString(node.getFirstChild(), buffer);
            break;

        case Token.NUMBER:
            buffer.append(JSNumericFunctions.fixType(node.getDouble()));
            break;

        case Token.NULL:
            buffer.append("null");
            break;


        case Token.TRUE:
            buffer.append("true");
            break;


        case Token.FALSE:
            buffer.append("false");
            break;


        default:
            buffer.append("UNSUPPORTED_EXPR(");
            boolean isFirstChild = true;
            for(Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                if(!isFirstChild)
                    buffer.append(",");
                isFirstElm = false;

                toString(child, buffer);
            }
            buffer.append(")");
        }
    }

    public static boolean isTrue(Object value) {
        if (value == null)
            return false;
        if((value instanceof JSString) && value.equals(""))
            return false;
        if("".equals(value))
            return false;
        if (value == Boolean.FALSE)
            return false;
        if ((value instanceof Number) && ((Number) value).doubleValue() == 0)
            return false;
        if ((value instanceof JSArray) && ((JSArray) value).size() == 0)
            return false;
        if ((value instanceof JSMap) && ((JSMap) value).keys().size() == 0)
            return false;

        return true;
    }


    public long approxSize(SeenPath seen) {
        long sum = super.approxSize( seen );


        sum += JSObjectSize.size( log, seen, this );
        sum += JSObjectSize.size( expression, seen, this );
        sum += JSObjectSize.size( token, seen, this );
        sum += JSObjectSize.size( isLiteral, seen, this );

        sum += approxSizeOfParseTree( this.parsedExpression, seen );

        return sum;
    }


    private long approxSizeOfParseTree(Node node, SeenPath seen) {
        long sum = 0;

        //type
        sum += 8;
        //next,first,last ptrs
        sum += 3*8;
        //line no
        sum += 8;

        if(node.getType() == Token.STRING)
            sum += JSObjectSize.size( node.getString(), seen, this );
        if(node.getType() == Token.NUMBER)
            sum += JSObjectSize.size( node.getDouble(), seen, this );


        //FIXME: size the node properties as well

        for(Node c = node.getFirstChild(); c != null; c = c.getNext())
            sum += approxSizeOfParseTree( c, seen );

        return sum;
    }

    //Functors
    private static class resolveFunc extends JSFunctionCalls1 {
        public Object call(Scope scope, Object contextObj, Object[] extra) {
            Expression thisObj = (Expression)scope.getThis();
            return thisObj.resolve(scope, (Context)contextObj);
        }
    }
    private static class isTrueFunc extends JSFunctionCalls1 {
        public Object call(Scope scope, Object p0, Object[] extra) {
            return isTrue(p0);
        }
    }
    private static class toStringFunc extends JSFunctionCalls0 {
        public Object call(Scope scope, Object[] extra) {
            Expression thisObj = (Expression)scope.getThis();
            return thisObj.toString();
        }
    }


    //Constructors
    public static final JSFunction CONSTRUCTOR = new JSFunctionCalls1() {
        public Object call(Scope scope, Object expressionObj, Object[] extra) {
            throw new UnsupportedOperationException();
        }
        protected void init() {
            set("is_true", new isTrueFunc());
            set("resolve", new resolveFunc());
            set("toString", new toStringFunc());
        }
    };
}
