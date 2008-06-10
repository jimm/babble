package ed.appserver.templates.djang10.tagHandlers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ed.appserver.templates.djang10.Context;
import ed.appserver.templates.djang10.Expression;
import ed.appserver.templates.djang10.FilterExpression;
import ed.appserver.templates.djang10.JSHelper;
import ed.appserver.templates.djang10.Node;
import ed.appserver.templates.djang10.Parser;
import ed.appserver.templates.djang10.TemplateException;
import ed.appserver.templates.djang10.Node.TagNode;
import ed.appserver.templates.djang10.Parser.Token;
import ed.appserver.templates.djang10.generator.JSWriter;
import ed.js.JSFunction;
import ed.js.JSObject;
import ed.js.JSObjectBase;
import ed.js.engine.Scope;
import ed.js.func.JSFunctionCalls2;

public class ForTagHandler implements TagHandler {

    public TagNode compile(Parser parser, String command, Token token) throws TemplateException {
        String[] params = token.contents.split("\\s");
        String itemName = params[1];
        if (!"in".equals(params[2]))
            throw new TemplateException("expected in operator");
        FilterExpression list = new FilterExpression(parser, params[3]);
        boolean isReversed = params.length > 4 ? Boolean.parseBoolean(params[4]) : false;

        List<Node> bodyNodes = parser.parse("end" + command);
        parser.nextToken();

        return new ForNode(token, itemName, list, isReversed, bodyNodes);
    }

    public Map<String, JSFunction> getHelpers() {
        Map<String, JSFunction> helpers = new HashMap<String, JSFunction>();

        helpers.put("newForLoopObjFn", new JSFunctionCalls2() {
            @Override
            public Object call(Scope scope, Object array, Object isReversedObj, Object[] extra) {

                Context contextStack = (Context) scope.get(JSWriter.CONTEXT_STACK_VAR);
                Object parentForLoop = contextStack.get("forloop");

                int length = (Integer) (((JSObject) array).get("length"));
                boolean isReversed = isReversedObj instanceof Boolean ? ((Boolean) isReversedObj) : false;

                ForLoopObj forLoopObj = new ForLoopObj(parentForLoop, length, isReversed);
                contextStack.push();
                contextStack.set("forloop", forLoopObj);

                return forLoopObj;
            }
        });

        return helpers;
    }

    private static class ForLoopObj extends JSObjectBase {
        private int i, length;
        private boolean isReversed;

        public ForLoopObj(Object parent, int length, boolean isReversed) {
            this.length = length;
            this.isReversed = isReversed;

            i = isReversed ? length : -1;
            set("parent", parent);
            moveNext();
        }

        public void moveNext() {
            i += isReversed ? -1 : 1;

            int counter0 = isReversed ? length - i - 1 : i;
            int revcounter0 = isReversed ? i : length - i - 1;

            set("i", i);
            set("counter0", counter0);
            set("counter", counter0 + 1);
            set("revcounter0", revcounter0);
            set("revcounter", revcounter0 + 1);
            set("first", counter0 == 0);
            set("last", revcounter0 == 0);
        }
    }

    private static class ForNode extends TagNode {
        private final String itemName;
        private final FilterExpression list;
        private final boolean isReversed;
        private List<Node> bodyNodes;

        public ForNode(Token token, String itemName, FilterExpression list, boolean isReversed, List<Node> bodyNodes) {
            super(token);

            this.itemName = itemName;
            this.list = list;
            this.isReversed = isReversed;
            this.bodyNodes = bodyNodes;
        }

        public void getRenderJSFn(JSWriter preamble, JSWriter buffer) throws TemplateException {
            String compiledList = JSHelper.NS + "." + Expression.DEFAULT_VALUE + "(" + list.toJavascript() + ", [])";
            Expression forloop = new Expression("forloop");
            Expression revcounter = new Expression("forloop.revcounter");
            Expression i = new Expression("forloop.i");

            buffer.appendHelper(startLine, "newForLoopObjFn(" + compiledList + ", " + isReversed + ");\n");

            buffer.append(startLine, "while(" + revcounter.toJavascript() + " > 0) {\n");

            buffer.append(JSWriter.CONTEXT_STACK_VAR + ".set(" + q(itemName) + ", " + compiledList + "[" + i.toJavascript()
                    + "]);\n");

            for (Node node : bodyNodes)
                node.getRenderJSFn(preamble, buffer);

            buffer.append(startLine, forloop.toJavascript() + ".moveNext();\n");

            buffer.append(startLine, "}\n");
            buffer.appendPopContext(startLine);

        }

        private static String q(String str) {
            return "\"" + str + "\"";
        }
    }
}
