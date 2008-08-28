// E4X.java

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

package ed.js;

import java.util.*;
import java.util.regex.*;
import java.io.*;

import org.w3c.dom.*;
import org.xml.sax.*;

import ed.js.func.*;
import ed.js.engine.*;
import ed.util.*;

public class E4X {

    public static JSFunction _cons = new Cons();
    public static JSFunction _ns = new NamespaceCons();

    public static class NamespaceCons extends JSFunctionCalls0 {

        public JSObject newOne(){
            return new Namespace();
        }

        public Object call( Scope scope , Object [] args){
            Object blah = scope.getThis();

            Namespace e;
            if ( blah instanceof Namespace) {
                e = (Namespace)blah;
             }
            else {
                e = new Namespace();
            }

            if( args.length == 1 ) {
                e.init( args[0].toString() );
            }
            if( args.length == 2 ) {
                e.init( args[0].toString(), args[1].toString() );
            }
           return e;
        }
    }

    public static class Cons extends JSFunctionCalls1 {

        public JSObject newOne(){
            return new ENode( this, defaultNamespace );
        }

        public Object call( Scope scope , Object str , Object [] args){
            Object blah = scope.getThis();

            ENode e;
            if ( blah instanceof ENode)
                e = (ENode)blah;
            else
                e = new ENode( this, defaultNamespace );
            e.init( str.toString() );
            return e;
        }

        public JSObject settings() {
            JSObjectBase sets = new JSObjectBase();
            sets.set("ignoreComments", ignoreComments);
            sets.set("ignoreProcessingInstructions", ignoreProcessingInstructions);
            sets.set("ignoreWhitespace", ignoreWhitespace);
            sets.set("prettyPrinting", prettyPrinting);
            sets.set("prettyIndent", prettyIndent);
            return sets;
        }

        public void setSettings() {
            setSettings(null);
        }

        public void setSettings( JSObject settings ) {
            if( settings == null ) {
                ignoreComments = true;
                ignoreProcessingInstructions = true;
                ignoreWhitespace = true;
                prettyPrinting = true;
                prettyIndent = 2;
                return;
            }

            Object setting = settings.get("ignoreComments");
            if(setting != null && setting instanceof Boolean)
                ignoreComments = ((Boolean)setting).booleanValue();
            setting = settings.get("ignoreProcessingInstructions");
            if(setting != null && setting instanceof Boolean)
                ignoreProcessingInstructions = ((Boolean)setting).booleanValue();
            setting = settings.get("ignoreWhitespace");
            if(setting != null && setting instanceof Boolean)
                ignoreWhitespace = ((Boolean)setting).booleanValue();
            setting = settings.get("prettyPrinting");
            if(setting != null && setting instanceof Boolean)
                prettyPrinting = ((Boolean)setting).booleanValue();
            setting = settings.get("prettyIndent");
            if(setting != null && setting instanceof Integer)
                prettyIndent = ((Integer)setting).intValue();
        }

        public JSObject defaultSettings() {
            JSObjectBase sets = new JSObjectBase();
            sets.set("ignoreComments", true);
            sets.set("ignoreProcessingInstructions", true);
            sets.set("ignoreWhitespace", true);
            sets.set("prettyPrinting", true);
            sets.set("prettyIndent", 2);
            return sets;
        }

        public boolean ignoreComments = true;
        public boolean ignoreProcessingInstructions = true;
        public boolean ignoreWhitespace = true;
        public boolean prettyPrinting = true;
        public int prettyIndent = 2;

        public Namespace defaultNamespace = new Namespace();
        
        public Object get( Object n ) {
            String s = n.toString();
            if( s.equals( "ignoreComments" ) )
                return ignoreComments;
            if( s.equals( "ignoreProcessingInstructions " ) )
                return ignoreProcessingInstructions;
            if( s.equals( "ignoreWhitespace" ) )
                return ignoreWhitespace;
            if( s.equals( "prettyPrinting" ) )
                return prettyPrinting;
            if( s.equals( "prettyIndent" ) )
                return prettyIndent;
            return null;
        }

        public Object set( Object k, Object v ) {
            String s = k.toString();
            String val = v.toString();
            if( s.equals( "ignoreComments" ) )
                ignoreComments = Boolean.parseBoolean(val);
            if( s.equals( "ignoreProcessingInstructions" ) ) 
                ignoreProcessingInstructions = Boolean.parseBoolean(val);
            if( s.equals( "ignoreWhitespace" ) )
                ignoreWhitespace = Boolean.parseBoolean(val);
            if( s.equals( "prettyPrinting" ) )
                prettyPrinting = Boolean.parseBoolean(val);
            if( s.equals( "prettyIndent" ) )
                prettyIndent = Integer.parseInt(val);
            return v;
        }

        public Namespace getDefaultNamespace() {
            return defaultNamespace;
        }

        public Namespace setAndGetDefaultNamespace(Object o) {
            defaultNamespace = new Namespace( "", o );
            return defaultNamespace;
        }
    }

    static class ENode extends JSObjectBase {
        private E4X.Cons XML;

        private ENode(){
            nodeSetup( null );
        }

        private ENode( E4X.Cons c, Namespace ns ) {
            XML = c;
            defaultNamespace = ns;
            nodeSetup( null );
        }

        private ENode( Node n ) {
            this( n, null );
        }

        private ENode( XMLList n ) {
            this( null, null, n );
        }

        private ENode( Node n, ENode parent ) {
            this( n, parent, null );
        }

        private ENode( Node n, ENode parent, XMLList children ) {
            if( n != null &&
                children == null &&
                n.getNodeType() != Node.TEXT_NODE &&
                n.getNodeType() != Node.ATTRIBUTE_NODE ) 
                this.children = new XMLList();
            else if( children != null ) {
                this.children = children;
            }
            this.node = n;
            nodeSetup(parent);
        }

        // creates a copy of an existing ENode
        private ENode( ENode n ) {
            this.XML = n.XML;
            this.name = n.name;
            this.parent = n.parent;

            this.node = n.node.cloneNode( false );
            this.inScopeNamespaces = (ArrayList<Namespace>)n.inScopeNamespaces.clone();
            if( n.children != null ) {
                this.children = new XMLList();
                for( ENode child : n.children ) {
                    ENode temp = child.copy();
                    this.children.add( temp );
                }
            }
        }

        /** Creates an empty node with a given parent and tag name.
         * This is to create "fake" nodes that are not attached to a parent.
         * @example 
         * xml = &lt;x/&gt;
         * xml.foo.bar;        // legal, so a "fake" node must be created 
         *                     // for foo so that bar doesn't throw an exception
         * xml.foo.bar = "hi"; // now the set method attaches the "fake" nodes to the parent
         */
        private ENode( ENode parent, Object o ) {
            if( parent instanceof XMLList && ((XMLList)parent).get(0) != null ) {
                parent = ((XMLList)parent).get(0);
            }
            if(parent != null && parent.node != null)
                node = parent.node.getOwnerDocument().createElement(o.toString());
            this.children = new XMLList();
            this._dummy = true;
            nodeSetup(parent);
        }

        /** Sets this node's parent, points it to the XML constructor, gets
         * the namespace, attributes, and initializes functions for it.
         */
        void nodeSetup( ENode parent ) {
            this.parent = parent;
            if( this.parent != null ) {
                this.XML = this.parent.XML;
            }
            getNamespace();
            addAttributes();
            addNativeFunctions();
        }

        /** Get attributes */
        void addAttributes() {
            if( this.node == null || isSimpleTypeNode() )
                return;

            if( this.children == null ) {
                this.children = new XMLList();
            }

            NamedNodeMap attr = this.node.getAttributes();
            for( int i=0; attr != null && i< attr.getLength(); i++) {
                String nodeName = attr.item( i ).getNodeName();
                if( nodeName.equals( "xmlns" ) || nodeName.startsWith( "xmlns:") )
                    continue;
                this.children.add( new ENode(attr.item(i), this ) );
            }
        }

        /** finds and sets the qname and namespace for a node.
         */
        void getNamespace() {
            this.inScopeNamespaces = new ArrayList<Namespace>();

            if( this.node == null ) {
                return;
            }
            else if( this.node.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE ) {
                this.name = new QName( new Namespace(), this.node.getNodeName() );
                return;
            }
            else if( this.node.getNodeType() == Node.ATTRIBUTE_NODE ) {
                String temp = this.node.getNodeName();
                if( temp.indexOf( ':' ) > 0) {
                    String prefix = temp.substring( 0, temp.indexOf( ':' ) );
                    String localName = temp.substring( temp.indexOf( ':' ) + 1 );
                    this.name = new QName( new Namespace( prefix, this.getNamespaceURI( prefix ) ), localName );
                }
                else {
                    this.name = new QName( XML.defaultNamespace, this.node.getNodeName() );
                }
                return;
            }

            boolean seenDefaultNamespace = false;
            NamedNodeMap attr = this.node.getAttributes();
            Pattern xmlns = Pattern.compile("xmlns(\\:(\\w+))?");
            for( int i=0; attr != null && i< attr.getLength(); i++) {
                Matcher m = xmlns.matcher( attr.item(i).getNodeName() );
                if( m.matches() ) {
                    String nsName =  m.group(1) == null ? "" : m.group(2);
                    if( m.group(1) == null ) {
                        seenDefaultNamespace = true;
                    }
                    Namespace ns = new Namespace( nsName, attr.item(i).getNodeValue() );
                    this.addInScopeNamespace( ns );
                }
            }
            if( !seenDefaultNamespace ) {
                this.addInScopeNamespace( XML.defaultNamespace );
                this.defaultNamespace = XML.defaultNamespace;
            }

            // get qualified name
            Pattern qname = Pattern.compile("((\\w+):)?(\\w+)");
            Matcher name = qname.matcher( this.node.getNodeName() );
            if( name.matches() ) {
                String prefix = "";
                if( name.group(1) != null ) {
                    prefix = name.group(2);
                }
                this.name = new QName( new Namespace( prefix, this.getNamespaceURI( prefix ) ), name.group( 3 ) );
            }
        }

        /** Initializes the functions that can be called on a node. 
         * XML nodes can have the same names as the functions one can call on a node.
         * Thus, if one calls xml.get( "copy" ), the get function doesn't know whether
         * to return the copy function or a node called "copy".  Thus, it returns both
         * in the form of a combination function and XML node object.  These are 
         * initialized below.
         */
        void addNativeFunctions() {
            nativeFuncs.put("addNamespace", new addNamespace());
            nativeFuncs.put("appendChild", new appendChild());
            nativeFuncs.put("attribute", new attribute());
            nativeFuncs.put("attributes", new attributes());
            nativeFuncs.put("child", new child());
            nativeFuncs.put("childIndex", new childIndex());
            nativeFuncs.put("children", new children());
            nativeFuncs.put("comments", new comments());
            nativeFuncs.put("contains", new contains());
            nativeFuncs.put("copy", new copy());
            nativeFuncs.put("descendants", new descendants());
            nativeFuncs.put("elements", new elements());
            nativeFuncs.put("hasOwnProperty", new hasOwnProperty());
            nativeFuncs.put("hasComplexContent", new hasComplexContent());
            nativeFuncs.put("hasSimpleContent", new hasSimpleContent());
            nativeFuncs.put("inScopeNamespaces", new inScopeNamespaces());
            nativeFuncs.put("insertChildAfter", new insertChildAfter());
            nativeFuncs.put("insertChildBefore", new insertChildBefore());
            nativeFuncs.put("length", new length());
            nativeFuncs.put("localName", new localName());
            nativeFuncs.put("name", new name());
            nativeFuncs.put("namespace", new namespace());
            nativeFuncs.put("namespaceDeclarations", new namespaceDeclarations());
            nativeFuncs.put("nodeKind", new nodeKind());
            nativeFuncs.put("normalize", new normalize());
            nativeFuncs.put("parent", new parent());
            nativeFuncs.put("processingInstructions", new processingInstructions());
            nativeFuncs.put("prependChild", new prependChild());
            nativeFuncs.put("propertyIsEnumerable", new propertyIsEnumerable());
            nativeFuncs.put("removeNamespace", new removeNamespace());
            nativeFuncs.put("replace", new replace());
            nativeFuncs.put("setChildren", new setChildren());
            nativeFuncs.put("setLocalName", new setLocalName());
            nativeFuncs.put("setName", new setName());
            nativeFuncs.put("setNamespace", new setNamespace());
            nativeFuncs.put("text", new text());
            nativeFuncs.put("toString", new toString());
            nativeFuncs.put("toXMLString", new toXMLString());
            nativeFuncs.put("valueOf", new valueOf());
        }

        /** Transforms the Java DOM into the E4X DOM.
         */
        void buildENodeDom(ENode parent) {
            NodeList kids = parent.node.getChildNodes();
            for( int i=0; i<kids.getLength(); i++) {
                if( ( kids.item(i).getNodeType() == Node.COMMENT_NODE && parent.XML.ignoreComments ) ||
                    ( kids.item(i).getNodeType() == Node.PROCESSING_INSTRUCTION_NODE && parent.XML.ignoreProcessingInstructions ) )
                    continue;
                ENode n = new ENode(kids.item(i), parent);
                buildENodeDom(n);
                parent.children.add(n);
            }
        }

        /** Turns a string into a DOM.
         */
        void init( String s ){
            // get rid of newlines and spaces if ignoreWhitespace is set (default)
            // otherwise each block of whitespace will become a text node... blech
            if( XML.ignoreWhitespace ) {
                Pattern p = Pattern.compile("\\>\\s+\\<");
                Matcher m = p.matcher(s);
                s = m.replaceAll("><");
            }
            try {
                node = XMLUtil.parse( s ).getDocumentElement();
            }
            catch ( Exception e ){
                throw new RuntimeException( "can't parse : " + e );
            }
            nodeSetup( null );
            buildENodeDom( this );
        }

        Hashtable<String, ENodeFunction> nativeFuncs = new Hashtable<String, ENodeFunction>();

        /** @getter
         */
        public Object get( Object n ) {
            if ( n == null )
                return null;

            Pattern num = Pattern.compile("\\d+");
            Matcher m = num.matcher( n.toString() );
            if( m.matches() || n instanceof Number )
                return child( n );

            if ( n instanceof String || n instanceof JSString ){
                String s = n.toString();
                if( s.equals("tojson") ) return null;

                // first check if this is a combo node/function
                if( nativeFuncs.containsKey( s ) )
                    return nativeFuncs.get( s );

                // if this is a simple node, we could be trying to get a string function
                if( this.hasSimpleContent() ) {
                    Object o = (new JSString( this.toString()) ).get( n );
                    if( o != null ) {
                        return o;
                    }
                }

                // otherwise, do the normal get
                Object o = _nodeGet( this, s );
                return ( o == null && E4X.isXMLName(s) ) ? new ENode( this, s ) : o;
            }

            if ( n instanceof Query ) {
		Query q = (Query)n;
                XMLList searchNode = ( this instanceof XMLList ) ? (XMLList)this : this.children;
		List<ENode> matching = new ArrayList<ENode>();
                for ( ENode theNode : searchNode ){
                    if ( q.match( theNode ) ) {
                        matching.add( theNode );
                    }
                }
		return _handleListReturn( matching );
            }

            throw new RuntimeException( "can't handle : " + n.getClass() );
        }

        /** @setter
         */
        public Object set( Object k, Object v ) {
            if( v == null ) 
                v = "null";
            if(this.children == null ) 
                this.children = new XMLList();

            if( k.toString().startsWith("@") )
                return setAttribute(k.toString(), v.toString());

            // attach any dummy ancestors to the tree
            if( this._dummy ) {
                ENode topParent = this;
                this._dummy = false;
                while( topParent.parent._dummy ) {
                    topParent = topParent.parent;
                    topParent._dummy = false;
                }
                topParent.parent.children.add(topParent);
            }

            // if v is an XML list, add each element
            if( v instanceof XMLList && !k.equals( "*" ) ) {
                int index = this.children.size();
                for( ENode target : (XMLList)v ) {
                    if ( ((List)this.children).contains( target ) ) {
                        index = this.children.indexOf( target ) + 1;
                    }
                    else {
                        this.children.add( index, target );
                        index++;
                    }
                }
                return v;
            }
            // if v is already XML and it's not an XML attribute, just add v to this enode's children
            if( v instanceof ENode ) {
                if( k.toString().equals("*") ) {
                    // in the unusual situation where we have x.set("*", x), we have
                    // to copy x before resetting its children
                    ENode vcopy = ((ENode)v).copy();
                    // replace children
                    if( v instanceof XMLList ) {
                        children = (XMLList)vcopy;
                        return this;
                    }
                    this.children = new XMLList();
                    this.children.add( vcopy );
                }
                else {
                    this.children.add((ENode)v);
                }
                return v;
            }

            // find out if this k/v pair exists
            ENode n;
            Object obj = get(k);
            if( obj instanceof ENode )
                n = ( ENode )obj;
            else {
                n = (( ENodeFunction )obj).cnode;
                if( n == null ) {
                    n = new ENode();
                }
            }

            Pattern num = Pattern.compile("-?\\d+");
            Matcher m = num.matcher(k.toString());

            // k is a number
            if( m.matches() ) {
                int index;
                // the index must be greater than 0
                if( ( index = Integer.parseInt(k.toString()) ) < 0)
                    return v;

                int numChildren = this instanceof XMLList ? ((XMLList)this).size() : this.children.size();
                // this index is greater than the number of elements existing
                if( index >= numChildren ) {
                    // if there is a list of future siblings, get the last one
                    // if this isn't a fake node, we've gone one too far and we need to get its parent
                    ENode rep = this instanceof XMLList ? ((XMLList)this).get( ((XMLList)this).size() - 1 ) : ( n._dummy ? this : this.parent );

                    // if k/v doesn't really exist, "get" returns a dummy node, an emtpy node with nodeName = key
                    if( n._dummy ) {
                        n._dummy = false;
                    }
                    // otherwise, we need to reset n so we don't replace an existing node
                    else {
                        n = new ENode();
                        n.children = new XMLList();
                    }

                    ENode attachee = rep.parent;
                    n.node = rep.node.getOwnerDocument().createElement( rep.localName() );
                    Node content = rep.node.getOwnerDocument().createTextNode(v.toString());
                    n.children.add( new ENode( content, n ) );
                    n.parent = attachee;
                    // get the last sibling's position & insert this new one there
                    attachee.children.add( attachee.children.indexOf(rep)+1, n );
                }
                // replace an existing element
                else {
                    // FIXME!  why are we using this.node?!
                    // reset the child list
                    n.children = new XMLList();
                    NodeList kids = n.node.getChildNodes();
                    for( int i=0; kids != null && i<kids.getLength(); i++) {
                        n.node.removeChild(kids.item(i));
                    }
                    Node content = n.node.getOwnerDocument().createTextNode(v.toString());
                    appendChild(content, n);
                }
            }
            // k must be a string
            else {
                int index = this.children.size();

                if( n.node != null && n.node.getNodeType() != Node.ATTRIBUTE_NODE) {
                    index = this.children.indexOf( n );
                    this.children.remove( n );
                }
                // if there are a list of children, delete them all and replace with the new k/v
                else if ( n instanceof XMLList ) {
                    XMLList list = (XMLList)n;
                    for( int i=0; n != null && i < list.size(); i++) {
                        if( list.get(i).node.getNodeType() == Node.ATTRIBUTE_NODE ) 
                            continue;
                        // find the index of this node in the tree
                        index = this.children.indexOf( list.get(i) );
                        // remove it from the tree
                        this.children.remove( list.get(i) ) ;
                    }
                }

                n = new ENode(this.node.getOwnerDocument().createElement(k.toString()), this);
                Node content = this.node.getOwnerDocument().createTextNode(v.toString());
                n.children.add( new ENode( content, n ) );
                if( !((List)this.children).contains( n ) )
                    if( index >= 0 )
                        this.children.add( index, n );
                    else
                        this.children.add( n );
            }
            return v;
        }

        private Object setAttribute( String k, String v ) {
            if( !k.startsWith("@") )
                return v;

            Object obj = get(k);
            k = k.substring(1);

            // create a new attribute
            if( obj == null ) {
                Attr newNode = node.getOwnerDocument().createAttribute(k);
                newNode.setValue( v );
                this.children.add( new ENode(newNode, this) );
            }
            // change an existing attribute
            else {
                List<ENode> list = this.getAttributes();
                for( ENode n : list ) {
                    if( ((Attr)n.node).getName().equals( k ) )
                        ((Attr)n.node).setValue( v );
                }
            }
            return v;
        }

        /**
         * Called for delete xml.prop
         */
        public Object removeField(Object o) {
            ENode n = (ENode)get(o);

            if( ! (n instanceof XMLList) ) {
                return n.parent.children.remove(n);
            }

            for( ENode e : (XMLList)n ) {
                this.children.remove( e );
            }

            return true;
        }

        /** 
         * Adds a namespace declaration to the scope namespace.
         * If this scope namespace already contains a namespace <pre>x</pre> with the
         * same prefix, the prefix of <pre>x</pre> is set to <pre>null</pre>.
         */
        public ENode addNamespace( Object ns ) {
            this.addInScopeNamespace( new Namespace( ns ) );
            return this;
        }

        public class addNamespace extends ENodeFunction {
            public Object call( Scope s, Object foo[] ) {
                return getENode( s ).addNamespace( getOneArg( foo ) );
            }
        }

        private ENode appendChild(Node child, ENode parent) {
            if(parent.children == null)
                parent.children = new XMLList();

            ENode echild = new ENode(child, parent);
            buildENodeDom(echild);
            parent.children.add(echild);
            return this;
        }

        /**
         * Appends a given child to this element's properties.
         */
        public ENode appendChild(ENode child) {
            return appendChild(child.node, this);
        }

        public class appendChild extends ENodeFunction {
            public Object call( Scope s, Object foo[] ) {
                ENode parent = getENode( s );
                ENode child = toXML( getOneArg( foo ) );
                return child == null ? parent : parent.appendChild(child);
            }
        }

        /**
         * Returns a list of zero or one attributes whose name matches 
         * the given property name.
         */
        public XMLList attribute( String prop ) {
            return new XMLList( (ENode)this.get("@"+prop) );
        }

        public class attribute extends ENodeFunction {
            public Object call( Scope s, Object foo[] ) {
                return getENode( s ).attribute( getOneArg( foo ).toString() );
            }
        }

        /**
         * Returns this node's attributes.
         */
        public XMLList attributes() {
            return new XMLList( (ENode)this.get( "@*" ) );
        }

        public class attributes extends ENodeFunction {
            public Object call( Scope s, Object foo[] ) {
                return getENode( s ).attributes();
            }
        }

        /**
         * Returns children matching a given name or index.
         */
        public ENode child( Object propertyName ) {
            XMLList nodeList = ( this instanceof XMLList ) ? (XMLList)this : this.children;
            Pattern num = Pattern.compile("\\d+(\\.\\d+)?");
            Matcher m = num.matcher(propertyName.toString());
            if( m.matches() ) {
                int i = Integer.parseInt( propertyName.toString() );

                if( i < nodeList.size() ) 
                    return nodeList.get(i);
                else if ( nodeList.size() >= 1 ) 
                    return new ENode( this, this instanceof XMLList ? nodeList.get(0).name.localName : this.name.localName );
                else
                    return new ENode();
            }
            else {
                Object obj = this.get(propertyName);
                return ( obj instanceof ENode ) ? (ENode)obj : ((ENodeFunction)obj).cnode;
            }
        }

        public class child extends ENodeFunction {
            public Object call( Scope s,  Object foo[]) {
                return getENode( s ).child( getOneArg( foo ).toString() );
            }
        }

        /**
         * Returns a number representing the position of this element within its parent.
         */
        public int childIndex() {
            if( parent == null || 
                parent.node.getNodeType() == Node.ATTRIBUTE_NODE || 
                this.node.getNodeType() == Node.ATTRIBUTE_NODE )
                return -1;

            XMLList sibs = parent.children();
            for( int i=0; i<sibs.size(); i++ ) {
                if(sibs.get(i).equals(this))
                    return i;
            }
            return -1;
        }

        public class childIndex extends ENodeFunction {
            public Object call (Scope s, Object foo[] ) {
                return getENode( s ).childIndex();
            }
        }

        /**
         * Returns this node's children.
         */
        public XMLList children() {
            XMLList child = new XMLList();
            for( ENode n : this.children ) {
                if( n.node.getNodeType() != Node.ATTRIBUTE_NODE )
                    child.add( n );
            }
            return child;
        }

        public class children extends ENodeFunction {
            public Object call( Scope s, Object foo[] ) {
                return getENode( s ).children();
            }
        }

        /**
         * Returns a list of comments, assuming XML.ignoreComments
         * was set to false when the list was created.
         */
        public XMLList comments() {
            XMLList comments = new XMLList();

            for( ENode child : this.children ) {
                if( child.node.getNodeType() == Node.COMMENT_NODE )
                    comments.add( child );
            }
            return comments;
        }

        public class comments extends ENodeFunction {
            public Object call( Scope s, Object foo[] ) {
                return getENode( s ).comments();
            }
        }

        /** FIXME
         * Compares this with another XML object.
         */
        public boolean contains( ENode o ) {
            if( this instanceof XMLList && o instanceof XMLList ) {
                XMLList x = (XMLList)this;
                XMLList x2 = (XMLList)o;
                if( x.size() != x2.size() )
                    return false;
                for( int i=0; i < x.size(); i++ ) {
                    if( !x.get(i).contains( x2.get(i) ) ) {
                        return false;
                    }
                }
                return true;
            }
            else if( !(this instanceof XMLList) && !(o instanceof XMLList) ) {
                if( !this.name.equals( o.name ) ||
                    !this.node.isEqualNode( o.node ) ) 
                    //                    !this.inScopeNamespaces.equals( o.inScopeNamespaces ) )
                    return false;

                if( ( this.children == null && o.children != null ) ||
                    ( this.children != null && o.children == null ) )
                    return false;

                if( this.children != null ) {
                    if( this.children.size() != o.children.size() )
                        return false;
                    for( int i=0; i<this.children.size(); i++ ) {
                        if( !this.children.get( i ).contains( o.children.get( i ) ) ) {
                            return false;
                        }
                    }
                }
                return true;
            }
            return false;
        }

        public class contains extends ENodeFunction {
            public Object call( Scope s, Object foo[] ) {
                return getENode( s ).contains( (ENode)getOneArg( foo ) );
            }
        }

        /**
         * Creates a deep copy of this node.
         */
        public ENode copy() {
            if( this instanceof XMLList ) {
                return new XMLList( this );
            }
            return new ENode( this );
        }

        public class copy extends ENodeFunction {
            public Object call( Scope s, Object foo[] ) {
                return getENode( s ).copy();
            }
        }

        /** 
         * Returns all descendants with a given name, or all decendants
         * if a name is not provided.
         */
        public ENode descendants( String name ) {
            List kids = new LinkedList<ENode>();

            ENode childs = (ENode)this.get(name);
            for( int i=0; i<childs.children.size(); i++) {
                kids.add(childs.children.get(i));
                ENode el = ((ENode)childs.children.get(i)).descendants(name);
                for( int j=0; j<el.children.size(); j++) {
                    kids.add(el.children.get(j));
                }
            }
            return new XMLList(kids);
        }

        public ENode descendants() {
            return this.descendants( "*" );
        }

        public class descendants extends ENodeFunction {
            public Object call( Scope s, Object foo[] ) {
                String name = ( foo.length == 0) ? "*" : foo[0].toString();
                return getENode( s ).descendants( name );
            }
        }

        public XMLList elements( String name ) {
            if( this.children == null || this.children.size() == 0)
                return null;
            
            if(name == null || name == "") {
                name = "*";
            }

            XMLList list = new XMLList();
            for( ENode n : this.children ) {
                if( n.node != null && n.node.getNodeType() == Node.ELEMENT_NODE && (name.equals( "*" ) || n.localName().equals(name)) )
                    list.add( n );
            }
            return list;
        }

        public class elements extends ENodeFunction {
            public Object call( Scope s, Object foo[] ) {
                String name = (foo.length == 0) ? "*" : foo[0].toString();
                return getENode( s ).elements( name );
            }
        }

        private ArrayList<Namespace> getNamespaces( Object o ) {
            o = o == null ? "" : o;
            ArrayList<Namespace> list = new ArrayList<Namespace>();
            for( Namespace ns : this.inScopeNamespaces ) {
                if( o instanceof Namespace ) {
                    if( ns.equals( o ) )
                        list.add(ns);
                }
                else if( o instanceof String ) {
                    if( ns.prefix == null )
                        continue;

                    if( ns.prefix.equals(o.toString()) )
                        list.add(ns);
                    else if( ns.uri.equals( o.toString() ) )
                        list.add(ns);
                }
            }
            return list;
        }

        private String getNamespacePrefix( String uri ) {
            for( Namespace n : this.inScopeNamespaces ) {
                if( n.uri != null && n.uri.equals( uri ) ) 
                    return n.prefix;
            }
            return null;
        }

        private String getNamespaceURI( String prefix ) {
            ENode temp = this;
            while( temp != null ) {
                for( Namespace n : temp.inScopeNamespaces ) {
                    if( n.prefix != null && n.prefix.equals( prefix ) ) 
                        return n.uri;
                }
                temp = temp.parent;
            }
            return null;
        }

        public boolean hasOwnProperty( String prop ) {
            for( ENode n : this.children ) {
                if( n.node != null && n.localName().equals(prop) )
                    return true;
            }
            return false;
        }

        public class hasOwnProperty extends ENodeFunction {
            public Object call(Scope s, Object foo[] ) {
                return getENode( s ).hasOwnProperty( getOneArg( foo ).toString() );
            }
        }

        private boolean isSimpleTypeNode( ) {
            if( this.node == null )
                return true;
            short type = this.node.getNodeType();
            if( type == Node.ATTRIBUTE_NODE ||
                type == Node.PROCESSING_INSTRUCTION_NODE ||
                type == Node.COMMENT_NODE ||
                type == Node.TEXT_NODE )
                return true;
            return false;
        }

        /**
         * Returns if this node contains complex content.  That is, if this node has child nodes that are element-type nodes.
         */
        public boolean hasComplexContent() {
            if( !(this instanceof XMLList) && this.isSimpleTypeNode() )
                return false;

            XMLList list = this instanceof XMLList ? (XMLList)this : this.children;
            for( ENode n : list ) {
                if( n.node.getNodeType() == Node.ELEMENT_NODE )
                    return true;
            }
            return false;
        }

        public class hasComplexContent extends ENodeFunction {
            public Object call(Scope s, Object foo[]) {
                return getENode( s ).hasComplexContent();
            }
        }

        /**
         * Returns if this node contains simple content.  An XML node is considered to have 
         * simple content if it represents a text or attribute node or an XML element with no child elements.
         */
        public boolean hasSimpleContent() {
            if( this.node != null ) {
                short type = this.node.getNodeType();
                if( type == Node.PROCESSING_INSTRUCTION_NODE ||
                    type == Node.COMMENT_NODE )
                    return false;
            }

            XMLList list = this instanceof XMLList ? (XMLList)this : this.children;
            for( ENode n : list ) {
                if( n.node.getNodeType() == Node.ELEMENT_NODE )
                    return false;
            }
            return true;
        }

        public class hasSimpleContent extends ENodeFunction {
            public Object call(Scope s, Object foo[]) {
                return getENode( s ).hasSimpleContent();
            }
        }

        public ArrayList<Namespace> inScopeNamespaces() {
            ArrayList<Namespace> isn = new ArrayList<Namespace>();
            ENode temp = this;
            while( temp != null ) {
                for( Namespace ns : temp.inScopeNamespaces ) {
                    if( ! ns.containsPrefix( isn ) )
                        isn.add( ns );
                }
                temp = temp.parent;
            }
            return isn;
        }

        public class inScopeNamespaces extends ENodeFunction {
            public Object call(Scope s, Object foo[]) {
                return new JSArray( getENode( s ).inScopeNamespaces().toArray() );
            }
        }

        public ENode insertChildAfter(Object child1, ENode child2) {
            return _insertChild(child1, child2, 1);
        }

        public ENode insertChildBefore(Object child1, ENode child2) {
            return _insertChild(child1, child2, 0);
        }

        public class insertChildAfter extends ENodeFunction {
            public Object call(Scope s, Object foo[]) {
                foo = getTwoArgs( foo );
                return getENode( s ).insertChildAfter(foo[0], (ENode)foo[1]);
            }
        }

        public class insertChildBefore extends ENodeFunction {
            public Object call(Scope s, Object foo[]) {
                foo = getTwoArgs( foo );
                return getENode( s ).insertChildBefore(foo[0], (ENode)foo[1]);
            }
        }

        private ENode _insertChild( Object child1, ENode child2, int j ) {
            if( this.isSimpleTypeNode() ) return null;
            if( child1 == null ) {
                this.children.add( 0, child2 );
                return this;
            }
            else if ( child1 instanceof ENode ) {
                for( int i=0; i<children.size(); i++) {
                    if( children.get(i) == child1 ) {
                        children.add(i+j, child2);
                        return this;
                    }
                }
            }
            return null;
        }

        public class length extends ENodeFunction {
            public Object call(Scope s, Object foo[]) {
                ENode enode = getENode( s );
                return enode instanceof XMLList ? ((XMLList)enode).size() : ( enode.node != null ? 1 : enode.children.size() );
            }
        }

        public String localName() {
            // comments and text nodes don't have local names
            if( this.name == null ) 
                return null;
            return this.name.localName;
        }

        public class localName extends ENodeFunction {
            public Object call(Scope s, Object foo[]) {
                return getENode( s ).localName();
            }
        }

        public QName name() {
            return this.name;
        }

        public class name extends ENodeFunction {
            public Object call(Scope s, Object foo[]) {
                return getENode( s ).name();
            }
        }

        public Namespace namespace() {
            return namespace( null );
        }

        public Namespace namespace( String prefix ) {
            if( prefix == null ) {
                return this.name.getNamespace( this.inScopeNamespaces );
            }
            ENode n = this;
            while( n != null ) {
                String uri = n.getNamespaceURI( prefix );
                if( uri != null ) 
                    return new Namespace( prefix, uri );
                n = n.parent;
            }
            return null;
        }

        public class namespace extends ENodeFunction {
            public Object call(Scope s, Object foo[]) {
                String prefix = (foo.length > 0) ? foo[0].toString() : null;
                return getENode( s ).namespace( prefix );
            }
        }


        private ArrayList<Namespace> getAncestors() {
            ArrayList<Namespace> ancestors = new ArrayList<Namespace>();

            ENode temp = this.parent;
            while( temp != null ) {
                for( Namespace ns : temp.inScopeNamespaces ) {
                    if( ! ns.containsPrefix( ancestors ) ) {
                        ancestors.add( ns );
                    }
                }
                temp = temp.parent;
            }
            return ancestors;
        }

        public ArrayList<Namespace> namespaceDeclarations() {
            ArrayList<Namespace> a = new ArrayList<Namespace>();
            if( this instanceof XMLList || this.isSimpleTypeNode( ) )
                return a;

            ArrayList<Namespace> ancestors = this.getAncestors();
            if( this.defaultNamespace != null ) {
                ancestors.add( this.defaultNamespace );
            }

            for( Namespace ns : this.inScopeNamespaces ) {
                if( ! ns.containedIn( ancestors ) )
                    a.add( ns );
            }
            return a;
        }

        public class namespaceDeclarations extends ENodeFunction {
            public Object call(Scope s, Object foo[]) {
                ArrayList<Namespace> a = getENode( s ).namespaceDeclarations();
                JSArray decs = new JSArray();
                for( Namespace ns : a ) {
                    decs.add( ns );
                }
                return decs;
            }
        }

        public String nodeKind() {
            switch ( this.node.getNodeType() ) {
            case Node.ELEMENT_NODE :
                return "element";
            case Node.COMMENT_NODE :
                return "comment";
            case Node.ATTRIBUTE_NODE :
                return "attribute";
            case Node.TEXT_NODE :
                return "text";
            case Node.PROCESSING_INSTRUCTION_NODE :
                return "processing-instruction";
            default :
                return "unknown";
            }
        }

        public class nodeKind extends ENodeFunction {
            public Object call(Scope s, Object foo[]) {
                return getENode( s ).nodeKind();
            }
        }

        public ENode normalize() {
            int i=0;
            while( i< this.children.size()) {
                if( this.children.get(i).node.getNodeType() == Node.ELEMENT_NODE ) {
                    this.children.get(i).normalize();
                    i++;
                }
                else if( this.children.get(i).node.getNodeType() == Node.TEXT_NODE )  {
                    while( i+1 < this.children.size() && this.children.get(i+1).node.getNodeType() == Node.TEXT_NODE ) {
                        this.children.get(i).node.setNodeValue( this.children.get(i).node.getNodeValue() + this.children.get(i+1).node.getNodeValue());
                        this.children.remove(i+1);
                    }
                    if( this.children.get(i).node.getNodeValue().length() == 0 ) {
                        this.children.remove(i);
                    }
                    else {
                        i++;
                    }
                }
                else {
                    i++;
                }
            }
            return this;
        }

        /** Merges adjacent text nodes and eliminates empty text nodes */
        public class normalize extends ENodeFunction {
            public Object call(Scope s, Object foo[]) {
                return getENode( s ).normalize();
            }
        }

        public class parent extends ENodeFunction {
            public Object call(Scope s, Object foo[]) {
                return getENode( s ).parent;
            }
        }

        public XMLList processingInstructions( String name ) {
            boolean all = name.equals( "*" );

            XMLList list = new XMLList();
            for( ENode n : this.children ) {
                if ( n.node.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE && ( all || name.equals(n.name.localName) ) ) {
                    list.add( n );
                }
            }
            return list;
        }

        public class processingInstructions extends ENodeFunction {
            public Object call(Scope s, Object foo[] ) {
                String name = (foo.length == 0 ) ? "*" : foo[0].toString();
                return getENode( s ).processingInstructions( name );
            }
        }

        /** Inserts the given child into this object prior to the existing XML properties.
         */
        public class prependChild extends ENodeFunction {
            public Object call(Scope s, Object foo[]) {
                return getENode( s )._insertChild( (Object)null, (ENode)getOneArg( foo ), 0 );
            }
        }

        /**
         * So, the spec says that this should only return toString(prop) == "0".  However, the Rhino implementation returns true
         * whenever prop is a valid index, so I'm going with that.
         */
        public boolean propertyIsEnumerable( String prop ) {
            Pattern num = Pattern.compile("\\d+");
            Matcher m = num.matcher(prop);
            if( m.matches() ) {
                ENode n = this.child(prop);
                return !n._dummy;
            }
            return false;
        }

        public class propertyIsEnumerable extends ENodeFunction {
            public Object call(Scope s, Object foo[]) {
                return getENode( s ).propertyIsEnumerable( getOneArg( foo ).toString() );
            }
        }

        public ENode removeNamespace(Object namespace) {
            if( this instanceof XMLList || this.isSimpleTypeNode() ) 
                return this;

            Namespace ns = new Namespace( namespace );

            if( ns.prefix == null || ns.prefix.equals( "" ) ) {
                for( int i=0; i < this.inScopeNamespaces.size(); i++ ) {
                    if( this.inScopeNamespaces.get(i).uri.equals( ns.uri ) ) {
                        this.inScopeNamespaces.remove( i );
                        break;
                    }
                }
            }
            else {
                for( int i=0; i < this.inScopeNamespaces.size(); i++ ) {
                    if( this.inScopeNamespaces.get( i ).uri.equals( ns.uri ) &&
                        this.inScopeNamespaces.get( i ).prefix.equals( ns.prefix ) ) {
                        this.inScopeNamespaces.remove( i );
                        break;
                    }
                }
            }
            for( ENode enode : this.children ) {
                enode.removeNamespace( namespace );
            }
            return this;
        }

        public class removeNamespace extends ENodeFunction {
            public Object call(Scope s, Object foo[]) {
                return getENode( s ).removeNamespace( getOneArg( foo ) );
            }
        }

        public class replace extends ENodeFunction {
            public Object call(Scope s, Object foo[]) {
                Object obj = s.getThis();
                ENode enode = ( obj instanceof ENode ) ? (ENode)obj : ((ENodeFunction)obj).cnode;

                String name = foo[0].toString();
                Object value = foo[1];
                ENode exists = (ENode)enode.get(name);
                if( exists == null )
                    return this;

                return enode.set(name, value);
            }
        }

        public Object setChildren( Object value ) {
            this.set("*", value);
            return this;
        }

        public class setChildren extends ENodeFunction {
            public Object call(Scope s, Object foo[]) {
                return getENode( s ).setChildren( getOneArg( foo ) );
            }
        }

        public void setLocalName( Object name ) {
            if( this.node == null ||
                this.node.getNodeType() == Node.TEXT_NODE ||
                this.node.getNodeType() == Node.COMMENT_NODE )
                return;
            this.name.localName = ( name instanceof QName ) ? ((QName)name).localName : name.toString();
        }

        public class setLocalName extends ENodeFunction {
            public Object call(Scope s, Object foo[]) {
                getENode( s ).setLocalName( getOneArg( foo ) );
                return null;
            }
        }

        /** Set the name (uri:localName) of this.  
         * Behavior depends on node type.
         * <dl>
         * <dt>Text node</dt><dd>Fails</dd>
         * <dt>Comment node</dt>Fails</dd>
         * <dt>Processing instruction node</dt><dd>Local name can be set, but it cannot have a uri associated with its name</dd>
         * <dt>Attribute node</dt><dd>Its name will be set and the new namespace will be added to its parent</dd>
         * <dt>Element node</dt><dd>Succeeds</dd>
         * </dl>
         * @param name Either a string or QName representing the new name
         */
        public void setName( Object name ) {
            if( this.node == null ||
                this.node.getNodeType() == Node.TEXT_NODE ||
                this.node.getNodeType() == Node.COMMENT_NODE )
                return;

            QName n;
            if ( name instanceof QName && ((QName)name).uri.equals("") )
                name = ((QName)name).localName;

            n = new QName( XML.defaultNamespace, name );

            if( this.node.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE )
                n.uri = "";
            this.name = n;

            Namespace ns = n.uri == null ? XML.defaultNamespace : new Namespace( n.prefix, n.uri );
            if( this.node.getNodeType() == Node.ATTRIBUTE_NODE ) {
                if( this.parent == null )
                    return;
                this.parent.addInScopeNamespace( ns );
            }
            if( this.node.getNodeType() == Node.ELEMENT_NODE )
                this.addInScopeNamespace( ns );
        }

        public class setName extends ENodeFunction {
            public Object call(Scope s, Object foo[]) {
                getENode( s ).setName( getOneArg( foo ).toString());
                return null;
            }
        }

        public void setNamespace( Object ns) {
            if( this.node == null ||
                this.node.getNodeType() == Node.TEXT_NODE ||
                this.node.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE ||
                this.node.getNodeType() == Node.COMMENT_NODE )
                return;

            Namespace ns2;
            if( ns instanceof Namespace )
                ns2 = (Namespace)ns;
            else
                ns2 = new Namespace( ns );

            this.name = new QName( ns2, this.name );

            if( this.node.getNodeType() == Node.ATTRIBUTE_NODE ) {
                if (this.parent == null )
                    return;
                this.parent.addInScopeNamespace( ns2 );
            }
            if( this.node.getNodeType() == Node.ELEMENT_NODE ) {
                this.addInScopeNamespace( ns2 );
            }
        }

        public class setNamespace extends ENodeFunction {
            public Object call(Scope s, Object foo[]) {
                getENode( s ).setNamespace( getOneArg( foo ) );
                return null;
            }
        }

        public XMLList text() {
            XMLList list = new XMLList();
            if( this instanceof XMLList ) {
                for ( ENode n : (XMLList)this ) {
                    if( n.node.getNodeType() == Node.TEXT_NODE ) {
                        list.add( n );
                    }
                }
            }
            else if( this.node != null && this.node.getNodeType() == Node.TEXT_NODE ) {
                list.add( this );
            }            
            return list;
        }

        public class text extends ENodeFunction {
            public Object call(Scope s, Object foo[]) {
                return getENode( s ).text();
            }
        }

        public String toString() {
            StringBuilder xml = new StringBuilder();
            if( this.node != null || this.children.size() == 1 ) {
                ENode singleNode = ( this.node != null ) ? this : this.children.get(0);
                List<ENode> kids = singleNode.printableChildren();

                // if this is an empty top level element, return nothing
                if( singleNode.node.getNodeType() == Node.ELEMENT_NODE && ( kids == null || kids.size() == 0 ) )
                    return "";

                if( singleNode.node.getNodeType() == Node.ATTRIBUTE_NODE || singleNode.node.getNodeType() == Node.TEXT_NODE )
                    return singleNode.node.getNodeValue();

                if ( singleNode.node.getNodeType() == Node.ELEMENT_NODE &&
                     singleNode.children != null &&
                     singleNode.childrenAreTextNodes() ) {
                    for( ENode n : kids )
                        xml.append( n.node.getNodeValue() );
                    return xml.toString();
                }

                singleNode.append( xml, 0, new ArrayList<Namespace>() );
            }

            if( xml.length() > 0 && xml.charAt(xml.length() - 1) == '\n' ) {
                xml.deleteCharAt(xml.length()-1);
            }
            return xml.toString();
        }

        public StringBuilder append( StringBuilder buf , int level , ArrayList<Namespace> ancestors ){
            if( XML.prettyPrinting )
                _level( buf, level );

            switch ( this.node.getNodeType() ) {
            case Node.TEXT_NODE :
                if( XML.prettyPrinting ) {
                    return buf.append( escapeElementValue( this.node.getNodeValue().trim() ) );
                }
                else {
                    return buf.append( escapeElementValue( this.node.getNodeValue() ) );
                }
            case Node.ATTRIBUTE_NODE :
                return buf.append( escapeAttributeValue( this.node.getNodeValue() ) );
            case Node.COMMENT_NODE :
                return buf.append( "<!--"+this.node.getNodeValue()+"-->" );
            case Node.PROCESSING_INSTRUCTION_NODE :
                return buf.append( "<?" + this.localName() + " " + ((ProcessingInstruction)this.node).getData() + "?>");
            }

            buf.append( "<" );
            String prefix = "";
            if( this.name.prefix != null && !this.name.prefix.equals( "" ) ) {
                prefix = this.name.prefix + ":";
            }
            buf.append( prefix + this.name.localName ).append( this.attributesToString( ancestors ));

            List<ENode> kids = this.printableChildren();
            if ( kids == null || kids.size() == 0 ) {
                return buf.append( "/>" );
            }
            buf.append(">");

            boolean indentChildren = ( kids.size() > 1 ) || ( kids.size() == 1 && kids.get(0).node.getNodeType() != Node.TEXT_NODE );
            int nextIndentLevel = level;
            if( XML.prettyPrinting && indentChildren )
                nextIndentLevel = level + 1;
            else
                nextIndentLevel = 0;

            for ( ENode c : kids ) {
                if( c.node.getNodeType() == Node.ATTRIBUTE_NODE ||
                    ( XML.ignoreComments && c.node.getNodeType() == Node.COMMENT_NODE ) ||
                    ( XML.ignoreProcessingInstructions && c.node.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE ) )
                    continue;

                if( XML.prettyPrinting && indentChildren )
                    buf.append( "\n" );

                c.append( buf , nextIndentLevel , ancestors );
                // delete from ancestors
                ancestors.remove( c.defaultNamespace );
            }

            if( XML.prettyPrinting && indentChildren ) {
                buf.append( "\n" );
                _level( buf, level );
            }
            buf.append( "</" );
            return buf.append( prefix + this.name.localName ).append( ">" );
        }

        private StringBuilder _level( StringBuilder buf , int level ){
            for ( int i=0; i<level; i++ ) {
                for( int j=0; j< XML.prettyIndent; j++) {
                    buf.append( " " );
                }
            }
            return buf;
        }

        private String attributesToString( ArrayList<Namespace> ancestors ) {
            StringBuilder buf = new StringBuilder();

            boolean defaultDefined = false;
            ArrayList<Namespace> namespaces = this.namespaceDeclarations();
            if( this.defaultNamespace != null && !ancestors.contains( this.defaultNamespace ) ) {
                namespaces.add( 0, this.defaultNamespace );
                ancestors.add( this.defaultNamespace );
            }

            ArrayList<Namespace> xmlns = new ArrayList<Namespace>();
            Namespace lastXmlns = new Namespace();
            for( int i = 0; i < namespaces.size(); i++ ) {
                Namespace ns = namespaces.get(i);
                if( ( ns.prefix == null || ns.prefix.equals( "" ) ) && ns.uri.equals( "" ) ) 
                    continue;

                // if the prefix is null, generate a prefix and display
                // the prefix-less namespace
                if( ns.prefix == null ) {
                    xmlns.remove( lastXmlns );
                    xmlns.add( ns );
                    lastXmlns = new Namespace( "", ns.uri );
                    xmlns.add( lastXmlns );
                }
                else if( ns.prefix.equals( "" ) && !defaultDefined ) {
                    xmlns.remove( lastXmlns );
                    lastXmlns = ns;
                    xmlns.add( ns );
                }
                else if( !ns.prefix.equals( "" ) ) {
                    xmlns.add( ns );
                }
            }

            for( int i=0; i < xmlns.size(); i++ ) {
                Namespace ns = xmlns.get(i);
                if( ns.prefix == null ) {
                    String genPrefix = ns.getPrefix();
                    int matchCount = 0;
                    for( int j=0; j < i; j++ ) {
                        if( xmlns.get(j).prefix.startsWith( genPrefix ) ) {
                            matchCount++;                            
                        }
                    }
                    if( matchCount > 0 ) {
                        genPrefix += "-" + matchCount;
                    }
                    ns.prefix = genPrefix;
                    buf.append( " xmlns:" + genPrefix + "=\"" + ns.uri + "\"" );
                }
                else if( ns.prefix.equals( "" ) ) {
                    buf.append( " xmlns=\"" + ns.uri + "\"" );
                }
                else { //if( !ns.prefix.equals( "" ) ) {
                    buf.append( " xmlns:" + ns.prefix + "=\"" + ns.uri + "\"" );
                }
            }

            // get attrs
            ArrayList<ENode> attr = this.getAttributes();
            String[] attrArr = new String[attr.size()];
            for( int i = 0; i< attr.size(); i++ ) {
                String prefix = "";
                if( !attr.get(i).name.prefix.equals( "" ) )
                    prefix = attr.get(i).name.prefix + ":";
                attrArr[i] = " " + prefix + attr.get(i).localName() + "=\"" + attr.get(i).node.getNodeValue() + "\"";
            }
            Arrays.sort(attrArr);
            for( String a : attrArr ) {
                buf.append( a );
            }
            return buf.toString();
        }

        private List<ENode> printableChildren() {
            List list = new LinkedList<ENode>();
            for ( int i=0; this.children != null && i<this.children.size(); i++ ){
                ENode c = this.children.get(i);
                if( c.node == null )
                    throw new RuntimeException("c.node is null: "+c.getClass());
                if( c.node.getNodeType() == Node.ATTRIBUTE_NODE ||
                    ( c.node.getNodeType() == Node.COMMENT_NODE && XML.ignoreComments ) ||
                    ( c.node.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE && XML.ignoreProcessingInstructions ) )
                    continue;
                list.add(c);
            }
            return list;
        }

        private boolean childrenAreTextNodes() {
            List<ENode> kids = this.printableChildren();
            for( ENode n : kids ) {
                if( n.node.getNodeType() != Node.TEXT_NODE )
                    return false;
            }
            return true;
        }

        public class toString extends ENodeFunction {
            public Object call(Scope s, Object foo[]) {
                return getENode( s ).toString();
            }
        }

        public String toXMLString() {
            return this.append( new StringBuilder(), 0, new ArrayList<Namespace>() ).toString();
        }

        /** too painful to do right now */
        public class toXMLString extends ENodeFunction {
            public Object call(Scope s, Object foo[]) {
                return getENode( s ).toXMLString();
            }
        }

        public class valueOf extends ENodeFunction {
            public Object call(Scope s, Object foo[]) {
                return getENode( s );
            }
        }

        private void addInScopeNamespace( Namespace n ) {
            if ( this.node == null || this.isSimpleTypeNode() )
                return;

            ArrayList<Namespace> match = this.getNamespaces( n.prefix );
            for( Namespace ns : this.inScopeNamespaces ) {
                if( ( ns.prefix == null && n.prefix == null ) ||
                    ( ns.prefix != null && ns.prefix.equals( n.prefix ) ) ) {
                    // no duplicates allowed... bug in spidermonkey!
                    if( ns.uri.equals( n.uri ) ) {
                        return;
                    }
                    // if two prefixes are the same, set the old one to null
                    else if( ns.prefix != null && !ns.prefix.equals( "" ) ) {
                        ns.prefix = null;
                        break;
                    }
                }
            }

            this.inScopeNamespaces.add( n );
        }

        public ArrayList getAttributes() {
            ArrayList<ENode> list = new ArrayList<ENode>();

            if(node == null && ( children == null || children.size() == 0)) 
                return list;

            if(this.node != null && this.children != null) {
                for( ENode child : this.children ) {
                    if( child.node.getNodeType() == Node.ATTRIBUTE_NODE ) {
                        list.add(child);
                    }
                }
            }
            else if (this.children != null ) {
                for( ENode child : this.children ) {
                    if( child.node.getNodeType() == Node.ELEMENT_NODE ) {
                        list.addAll(child.getAttributes());
                    }
                }
            }
            return list;
        }

        public ENode toXML( Object input ) {
            if( input == null )
                return null;

            if( input instanceof Boolean ||
                input instanceof Number ||
                input instanceof JSString )
                return toXML(input.toString());
            else if( input instanceof String )
                return new ENode(this.node.getOwnerDocument().createTextNode((String)input), this);
            else if( input instanceof Node )
                return new ENode((Node)input, this);
            else if( input instanceof ENode )
                return (ENode)input;
            else
                return null;
        }

        public abstract class ENodeFunction extends JSFunctionCalls0 {
            public String toString() {
                return getNode() == null ? "" : cnode.toString();
            }

            public Object get( Object n ) {
                return getNode().get( n );
            }

            public Object set( Object n, Object v ) {
                // there's this stupid thing where set is called for every xml node created
                if( n.equals("prototype") && v instanceof JSObjectBase)
                    return null;

                return getNode() == null ? null : cnode.set( n, v );
            }

            public Object removeField( Object f ) {
                return getNode().removeField(f);
            }

            public ENode getNode() {
                if( cnode != null) return cnode;
                cnode = (ENode)E4X._nodeGet(ENode.this, this.getClass().getSimpleName());
                return cnode;
            }

            ENode cnode;
        }

        public Collection<String> keySet( boolean includePrototype ) {
            XMLList list = ( this instanceof XMLList ) ? (XMLList)this : this.children;
            Collection<String> c = new ArrayList<String>();
            for( int i=0; i<list.size(); i++ ) {
                c.add( String.valueOf( i ) );
            }
            return c;
        }

        public Collection<ENode> valueSet() {
            XMLList list = ( this instanceof XMLList ) ? (XMLList)this : this.children;
            Collection<ENode> c = new ArrayList<ENode>();
            for( ENode n : list ) {
                c.add( n );
            }
            return c;
        }

        private static ENode getENode( Scope s ) {
            Object obj = s.getThis();
            return ( obj instanceof ENode ) ? (ENode)obj : ((ENodeFunction)obj).cnode;
        }

        private static Object getOneArg( Object foo[] ) {
            if( foo.length == 0 ) 
                throw new RuntimeException( "This method requires one argument." );
            return foo[0];
        }

        private static Object[] getTwoArgs( Object foo[] ) {
            Object[] o = new Object[2];
            if( foo.length < 2 ) 
                throw new RuntimeException( "This method requires two arguments." );

            o[0] = foo[0];
            o[1] = foo[1];
            return o;
        }

        private XMLList children;
        private ENode parent;
        private Node node;

        private boolean _dummy;
        private ArrayList<Namespace> inScopeNamespaces = new ArrayList<Namespace>();
        private QName name;

        public Namespace defaultNamespace;
    }

    static class XMLList extends ENode implements List<ENode>, Iterable<ENode> {
        public List<ENode> children;
        public XMLList() {
            children = new LinkedList<ENode>();
        }

        public XMLList( ENode node ) {
            children = new LinkedList<ENode>();
            // make a copy of an existing xmllist
            if( node instanceof XMLList ) {
                for( ENode child : (XMLList)node ) {
                    ENode temp = child.copy();
                    this.add( temp );
                }
            }
            else if( node != null && node.node != null ) {
                children.add( node );
            }
        }

        public XMLList( List<ENode> list ) {
            children = list;
        }

        public Iterator<ENode> iterator() {
            return children.iterator();
        }

        public int size() {
            return children.size();
        }

        public ENode get( int index ) {
            return children.get(index);
        }

        public String toString() {
            StringBuilder xml = new StringBuilder();
            if( children.size() == 1 ) {
                return children.get(0).toString();
            }
            for( ENode n : this ) {
                xml.append( n.toXMLString() );
                if( this.hasComplexContent() )
                    xml.append( "\n" );
            }
            if( xml.length() > 0 && xml.charAt(xml.length() - 1) == '\n' ) {
                xml.deleteCharAt(xml.length()-1);
            }
            return xml.toString();
        }

        public boolean addAll(XMLList list) { 
            for( ENode n : list ) 
                children.add( n ); 
            return true;
        }

        public boolean add( ENode n ) { return children.add(n); }
        public void add( int index, ENode n) { children.add( index, n); }
        public boolean addAll( Collection<? extends E4X.ENode> list ) { return children.addAll( list ); }
        public boolean addAll( int index, Collection<? extends E4X.ENode> list ) { return children.addAll( index, list ); }
        public void clear() {  children.clear(); }
        public boolean contains( Object o ) { return  children.contains( o ); }
        public boolean containsAll( Collection o ) { return  children.containsAll( o ); }
        public boolean equals( Object o) { return children.equals(o); }
        public int hashCode( IdentitySet seen ) { return children.hashCode(); }
        public int indexOf( Object o ) { return children.indexOf(o); }
        public boolean isEmpty() { return children.isEmpty(); }
        public int lastIndexOf( Object o) { return children.lastIndexOf( o ); }
        public ListIterator<ENode> listIterator() { return children.listIterator(); }
        public ListIterator<ENode> listIterator( int index) { return children.listIterator(index); }
        public ENode remove(int index) { return children.remove( index); }
        public boolean remove(Object o) { return children.remove( o ); }
        public boolean removeAll(Collection c) { return children.removeAll(c); }
        public boolean retainAll(Collection c) { return children.retainAll(c); }
        public ENode set(int index, ENode o) { return children.set(index, o); }
        public List<ENode> subList(int from, int to) { return children.subList(from, to); }
        public Object[] toArray() { return children.toArray(); }
        public <T> T[] toArray(T[] a) { return children.toArray(a); }
    }

    static Object _nodeGet( ENode start , String s ){
        if( start instanceof XMLList )
            return _nodeGet( (XMLList)start, s );
        return _nodeGet( new XMLList( start ), s );
    }

    static Object _nodeGet( XMLList start , String s ){
        final boolean search = s.startsWith( ".." );
        if ( search )
            s = s.substring(2);

        final boolean attr = s.startsWith( "@" );
        if ( attr )
            s = s.substring(1);

        final boolean qualified = s.contains( "::" );
        String uri = "";
        if( qualified ) {
            uri = s.substring( 0, s.indexOf("::") );
            s = s.substring( s.indexOf( "::" ) + 2 );
        }

        final boolean all = s.endsWith("*");
        if( all ) {
            if( s.length() > 1) return null;
            s = "";
        }

        List<ENode> traverse = new LinkedList<ENode>();
	List<ENode> res = new ArrayList<ENode>();

        for(int k=0; k< start.size(); k++) {
            traverse.add( start.get(k) );

            while ( ! traverse.isEmpty() ){
                ENode n = traverse.remove(0);

                if ( attr ){
                    ArrayList<ENode> nnm = n.getAttributes();
                    for( ENode enode : nnm ) {
                        if( all || ( ( ( qualified && enode.name.uri.equals( uri ) ) || !qualified ) && enode.localName().equals( s ) ) ) {
                            res.add( enode );
                        }
                    }
                }

                XMLList kids = n.children;
                if ( kids == null || kids.size() == 0 )
                    continue;

                for ( int i=0; i<kids.size(); i++ ){
                    ENode c = kids.get(i);
                    if ( !attr && c.node.getNodeType() != Node.ATTRIBUTE_NODE && 
                         ( all || 
                           ( ( c.node.getNodeType() == Node.TEXT_NODE && c.text().equals( s ) ) || c.node.getNodeType() != Node.TEXT_NODE ) &&
                           ( ( ( qualified && c.name.uri.equals( uri ) ) || !qualified ) && 
                             ( c.localName() != null && c.localName().equals( s ) ) ) ) ) {
                        res.add( c );
                    }

                    if ( search )
                        traverse.add( c );
                }
            }
        }
	return _handleListReturn( res );
    }

    static Object _handleListReturn( List<ENode> lst ){
	if ( lst.size() == 0 )
	    return null;

	if ( lst.size() == 1 ){
            return lst.get(0);
        }
        return new XMLList(lst);
    }

    public static abstract class Query {
	public Query( String what , JSString match ){
	    _what = what;
	    _match = match;
	}

	abstract boolean match( ENode n );

	final String _what;
	final JSString _match;
    }

    public static class Query_EQ extends Query {

	public Query_EQ( String what , JSString match ){
	    super( what , match );
	}

	boolean match( ENode n ){
            ENode result = (ENode)n.get( _what );
            if( result._dummy )
                return false;
            if( result.node.getNodeType() == Node.ATTRIBUTE_NODE )
                return result.node.getNodeValue().equals( _match.toString() );
            else
                return JSInternalFunctions.JS_eq( _nodeGet( n , _what ) , _match );
	}

	public String toString(){
	    return " [[ " + _what + " == " + _match + " ]] ";
	}

    }

    public static boolean isXMLName( String name ) {
        Pattern invalidChars = Pattern.compile("[@\\s\\{\\/\\']|(\\.\\.)|(\\:\\:)");
        Matcher m = invalidChars.matcher( name );
        if( m.find() ) {
            return false;
        }
        return true;
    }

    static class QName extends JSObjectBase {
        public String localName;
        public String uri;
        public String prefix;

        public QName() {
            this( null, null );
        }

        public QName( Object name )  {
            this( null, name );
        }

        public QName( Namespace namespace, Object name )  {
            if( name instanceof QName ) {
                if ( namespace == null ) {
                    this.localName = ((QName)name).localName;
                    this.uri = ((QName)name).uri;
                    return;
                }
                else {
                    name = ((QName)name).localName;
                }
            }
            this.localName = name == null ? "" : name.toString();
            if( namespace != null ) {
                this.uri = namespace.uri;
                this.prefix = namespace.prefix;
            }
        }

        public String toString() {
            String s = this.uri == null ? "*::" : ( this.uri.equals("") ? "" : this.uri + "::" );
            return s + this.localName;
        }

        public boolean equals( QName o ) {
            if( ( ( this.localName == null && o.localName == null) || (this.localName != null && this.localName.equals( o.localName ) ) ) &&
                ( ( this.uri == null && o.uri == null ) || ( this.uri != null && this.uri.equals( o.uri ) ) ) &&
                ( ( this.prefix == null && o.prefix == null ) || ( this.prefix != null && this.prefix.equals( o.prefix ) ) ) )
                return true;
            return false;
        }

        public Namespace getNamespace() {
            return getNamespace( null );
        }

        public Namespace getNamespace( ArrayList<Namespace> isn ) {
            if( this.uri == null )
                return null;

            for( Namespace ns : isn ) {
                if( ns.uri.equals( this.uri ) ) {
                    return ns;
                }
            }
            return new Namespace( this.uri );
        }

        public String get( Object n ) {
            if( n.toString().equals( "uri" ) ) {
                return this.uri;
            }
            else if ( n.toString().equals( "prefix" ) ) {
                return this.prefix;
            }
            else 
                return null;
        }
    }

    static class Namespace extends JSObjectBase {

        void init( String s ) {
            this.uri = s;
        }

        void init( String p, String s ) {
            this.prefix = p;
            this.uri = s;
        }

        public String prefix;
        public String uri;

        public Namespace() {
            this(null, null);
        }

        public Namespace( Object uri) {
            this(null, uri);
        }

        public Namespace( String prefix, Object uri) {
            if(prefix == null && uri == null) {
                this.prefix = "";
                this.uri = "";
            }
            else if (prefix == null) {
                if ( uri instanceof Namespace ) {
                    this.prefix = ((Namespace)uri).prefix;
                    this.uri = ((Namespace)uri).uri;
                }
                else if( uri instanceof QName ) {
                    this.uri = ((QName)uri).uri;
                }
                else {
                    this.uri = uri.toString();
                    this.prefix = this.uri.equals("") ? "" : null;
                }
            }
            else {
                if( uri instanceof QName && ((QName)uri).uri != null) {
                    this.uri = ((QName)uri).uri;
                }
                else {
                    this.uri = uri == null ? "" : uri.toString();
                }
                if( this.uri.equals("") ) {
                    if( prefix == null || prefix.equals("") ) {
                        this.prefix = "";
                    }
                    else {
                        return;
                    }
                }
                else if( prefix == null || !E4X.isXMLName( prefix ) ) {
                    this.prefix = null;
                }
                else {
                    this.prefix = prefix;
                }
            }
        }

        public boolean equals( Namespace ns ) {
            if( ( ns.prefix == null && this.prefix != null ) ||
                ( ns.prefix != null && this.prefix == null ) ||
                ( ns.uri == null && this.uri != null ) ||
                ( ns.uri != null && this.uri == null ) )
                return false;

            if( ( ns.prefix == null || ns.prefix.equals( this.prefix ) ) &&
                ( ns.uri == null || ns.uri.equals( this.uri ) ) )
                return true;
            return false;
        }

        public String toString() {
            return this.uri;
        }

        private boolean containedIn( ArrayList<Namespace> list ) {
            for( Namespace ns : list ) {
                if( ( ns.prefix == null && this.prefix != null ) ||
                    ( ns.prefix != null && this.prefix == null ) ||
                    ( ns.uri == null && this.uri != null ) ||
                    ( ns.uri != null && this.uri == null ) )
                    continue;

                if( ns.prefix == null && this.prefix == null ) {
                    if( ns.uri == null && this.uri == null || ns.uri.equals( this.uri ) ) {
                        return true;
                    }
                    return false;
                }
                if( ns.prefix.equals( this.prefix ) && ns.uri.equals( this.uri ) )
                    return true;
            }
            return false;
        }

        private boolean containsPrefix( ArrayList<Namespace> list ) {
            for( Namespace ns : list ) {
                if( ( ns.prefix == null && this.prefix != null ) ||
                    ( ns.prefix != null && this.prefix == null ) )
                    continue;
                if( ( ns.prefix == null && this.prefix == null ) ||
                    ns.prefix.equals( this.prefix ) )
                    return true;
            }
            return false;
        }

        public boolean isEmpty() {
            return this.uri == null || this.uri.equals( "" );
        }

        public String getPrefix() {
            String prefix = this.uri;
            while( prefix.endsWith("/") || prefix.endsWith(".xml") ) {
                if ( prefix.endsWith( "/" ) )
                    prefix.substring( 0, prefix.length() - 1 );
                if ( prefix.endsWith( ".xml" ) )
                    prefix.substring( 0, prefix.length() - 4 );
            }
            prefix = prefix.substring( prefix.lastIndexOf("/") + 1 );
            prefix = prefix.substring( prefix.lastIndexOf(".") + 1 );
            return prefix;
        }
    }

    public static String escapeElementValue( String s ) {
        s = s.replaceAll( "<", "&lt;" );
        s = s.replaceAll( ">", "&gt;" );
        s = s.replaceAll( "&", "&amp;" );
        return s;
    }

    public static String escapeAttributeValue( String s ) {
        s = s.replaceAll( "\"", "&quot;" );
        s = s.replaceAll( ">", "&gt;" );
        s = s.replaceAll( "&", "&amp;" );

        s = s.replaceAll( "\\u000A", "&#xA;" );
        s = s.replaceAll( "\\u000D", "&#xD;" );
        s = s.replaceAll( "\u0009", "&#x9;" );
        return s;
    }

    public static XMLList addNodes(ENode a, ENode b) {
        if( a instanceof XMLList && b instanceof XMLList) {
            ((XMLList)a).addAll(b);
            return (XMLList)a;
        }
        else if ( a instanceof XMLList ) {
            ((XMLList)a).add(b);
            return (XMLList)a;
        }
        else if ( b instanceof XMLList ) {
            ((XMLList)b).add(0, a);
            return (XMLList)b;
        }
        else {
            XMLList list = new XMLList();
            list.add( a );
            list.add( b );
            return list;
        }
    }

}
