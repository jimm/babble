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

import java.io.*;

import org.jruby.ast.Node;
import org.jruby.runtime.builtin.IRubyObject;

import ed.appserver.AppContext;
import ed.appserver.jxp.JxpSource;
import ed.js.JSFunction;
import ed.js.engine.Scope;
import ed.net.httpserver.HttpRequest;
import ed.net.httpserver.HttpResponse;

/**
 * Handles .rb file compilation and execution.
 */
public class RubyJxpSource extends JxpSource.JxpFileSource {

    protected Node node;
    protected long lastCompile;

    public RubyJxpSource(File f) {
        super(f);
    }

    public JSFunction getFunction() throws IOException {
        final Node node = getAST();
        return new ed.js.func.JSFunctionCalls0() {
            public Object call(Scope s, Object unused[]) { return RubyObjectWrapper.toJS(s, _doCall(node, s, unused)); }
        };
    }

    protected IRubyObject _doCall(Node node, Scope s, Object unused[]) {
        HttpRequest request = (HttpRequest)s.get("request");
        HttpResponse response = (HttpResponse)s.get("response");
        RuntimeEnvironment runenv = RuntimeEnvironmentPool.getInstance((AppContext)s.get("__instance__"));
        try {
            runenv.setup(s, request == null ? null : request.getInputStream(),
                         response == null ? null : response.getOutputStream());
            return runenv.commonRun(node);
        }
        finally {
            RuntimeEnvironmentPool.releaseInstance(runenv);
        }
    }

    protected synchronized Node getAST() throws IOException {
        final long lastModified = getFile().lastModified();
        if (node == null || lastCompile < lastModified) {
            node = parseContent(getFile().getPath());
            lastCompile = lastModified;
        }
        return node;
    }

    protected Node parseContent(String filePath) throws IOException {
        return RuntimeEnvironment.parse(getContent(), filePath);
    }
}
