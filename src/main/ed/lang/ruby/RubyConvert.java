// RubyConvert.java

package ed.lang.ruby;

import java.io.*;
import java.util.*;

import org.jruby.ast.*;
import org.jruby.ast.types.*;
import org.jruby.common.*;
import org.jruby.parser.*;
import org.jruby.lexer.yacc.*;

import ed.lang.*;
import ed.js.*;
import ed.js.engine.*;
import ed.appserver.templates.*;

public class RubyConvert extends ed.MyAsserts {
    
    final static boolean D = Boolean.getBoolean( "DEBUG.RUBY" );

    public static class TemplateImpl implements TemplateConverter {

        public Result convert( Template t ){
            if ( ! t.getName().endsWith( ".rb" ) )
                return null;
            
            try {
                final RubyConvert rc = new RubyConvert( t.getName() , t.getContent() );
                final String jsSource = rc.getJSSource();
                if ( D ) System.out.println( jsSource );
                return new TemplateConverter.Result( new Template( t.getName().replaceAll( ".rb$" , "_rb.js" ) , jsSource ) , rc._lineMapping );
            }
            catch ( IOException ioe ){
                throw new RuntimeException( "couldn't convert : " + t.getName() , ioe );
            }
        }
    }
    
    public RubyConvert( File f )
        throws IOException {
        this( f.toString() , new FileInputStream( f ) );
    }

    public RubyConvert( String name , String code )
        throws IOException {
        this( name , new ByteArrayInputStream( code.getBytes() ) );
    }
    
    public RubyConvert( String name , InputStream in )
        throws IOException {

        _name = name;
        _lines = new ArrayList<String>();
        _warnings = new NullWarnings();

        DefaultRubyParser p = new DefaultRubyParser();
        p.setWarnings( _warnings );
        
        RubyParserResult r = p.parse( new ParserConfiguration( 1 , true ) ,
                                      new InputStreamLexerSource( _name , in ,
                                                                  _lines , 1 , false ) );
        _ast = r.getAST();
        if ( D ) _print( 0 , _ast );
        _add( _ast , new State() );
    }
    
    void _add( Node node , State state ){
        
        if ( node == null || state == null )
            return;
        
        // --- blocking ----

        if ( node instanceof RootNode ){
            _assertOne( node );
            _add( node.childNodes().get(0) , state );
        }

        else if ( node instanceof BlockNode ){
            _appned( "{\n" , node );
            if ( node.childNodes() != null )
                for ( Node c : node.childNodes() )
                    _add( c , state );
            _appned( "\n}\n" , node );
        }

        else if ( node instanceof NewlineNode ){
            _assertOne( node );
            _add( node.childNodes().get(0) , state );
            _appned( ";\n" , node );
        }

        else if ( node instanceof ReturnNode ){
            _assertOne( node );
            _appned( "return " , node );
            _add( node.childNodes().get(0), state );
        }

        else if ( node instanceof ForNode ){

            ForNode fn = (ForNode)node;
            
            _add( fn.getIterNode() , state );
            _appned( ".forEach( " , fn );
            _addIterBlock( fn , state );
            _appned( " ) " , fn );
        }

        else if ( node instanceof IterNode ){
            _addIterBlock( (IterNode)node , state );
        }

        else if ( node instanceof YieldNode ){
            _appned( "arguments[ arguments.length - 1 ]" , node );
            _addArgs( node , node.childNodes() , state );
        }

        // --- function stuff ---

        else if ( node instanceof FCallNode ){
            FCallNode f = (FCallNode)node;
            
            if ( ( f.getArgsNode() == null || f.getArgsNode().childNodes() == null || f.getArgsNode().childNodes().size() == 0 ) &&
                 f.getIterNode() == null ){
                // no args
                _appned( Ruby.RUBY_V_CALL + "(" + _getFuncName( f ) + ")" , f );
            }
            else {
                _appned( _getFuncName( f )  , node );
                
                _appned( "(" , node );
                
                boolean first = true;
                
                if ( f.getArgsNode() != null && f.getArgsNode().childNodes() != null ){
                    for ( Node temp : f.getArgsNode().childNodes() ){
                        if ( first )
                            first = false;
                        else
                            _appned( " , " , temp );
                        _add( temp , state );
                    }
                }
                
                if ( f.getIterNode() != null ){
                    if ( ! first )
                        _appned( " , " , f );
                    _add( f.getIterNode() , state );
                }
            
                _appned( ")" , node );    
            }
            
        }
        
        
        else if ( node instanceof CallNode ){
            _addCall( (CallNode)node , state );
        }
        
        else if ( node instanceof MethodDefNode ){

            MethodDefNode dn = (MethodDefNode)node;
            
            if ( state._className != null ){
                _appned( state._className + "." + ( ( dn instanceof DefsNode || state._module ) ? "" : "prototype." ) , node );
            }
            _appned( _mangleFunctionName( dn.getName() ) + " = function(" , node );
            
            ArgsNode an = dn.getArgsNode();
            if ( an != null ){
                if ( an.getArgs() != null ){
                    for ( int i=0; i<an.getArgs().size(); i++ ){
                        ArgumentNode a = (ArgumentNode)an.getArgs().get(i);
                        if ( i > 0 )
                            _appned( " , " , a );
                        _appned( a.getName() , a );
                    }
                }

                if ( an.getBlockArgNode() != null ){
                    _appned( " , " + an.getBlockArgNode().getName() , an );
                }
                
            }

            _appned( " ){\n" , node );
            _add( dn.getBodyNode() , state );
            _appned( " \n}\n " , node );
        }

        else if ( node instanceof VCallNode ){
            _assertNoChildren( node );
            VCallNode vcn = (VCallNode)node;
            _appned( Ruby.RUBY_V_CALL + "( " + _getFuncName( vcn ) + ")" , node );
        }
        
        else if ( node instanceof ZSuperNode ){
            
        }

        else if ( node instanceof SuperNode ){
            SuperNode sn = (SuperNode)node;
            
            if ( sn.getArgsNode() == null )
                throw new RuntimeException( "shouldn't this be a Z, not a regular?" );

            if ( sn.getIterNode() != null )
                throw new RuntimeException( "what?" );

            _appned( "this.__proto__.constructor.call( this " , sn );
            for ( Node foo : sn.getArgsNode().childNodes() ){
                _appned( " , " , sn );
                _add( foo , state );
            }
            _appned( " )" , sn );
        }

        // --- class stuff ---
        
        else if ( node instanceof ClassNode ){
            // complicated enough to warrant own method
            _addClass( (ClassNode)node , state );
        }

        else if ( node instanceof ModuleNode ){
            _addModule( (ModuleNode)node , state );
        }

        else if ( node instanceof InstAsgnNode ){
            _assertOne( node );
            InstAsgnNode lan = (InstAsgnNode)node;
            _appned( "this." + lan.getName().substring(1) + " = " , node );
            _add( node.childNodes().get( 0 ) , state );
            _appned( "" , node );            
        }

        else if ( node instanceof InstVarNode ){
            _assertNoChildren( node );
            InstVarNode lvn = (InstVarNode)node;
            _appned( "this." + lvn.getName().substring(1) , node );
        }
        
        else if ( node instanceof SelfNode ){
            _appned( "this" , node );
        }

        // --- looping ---

        else if ( node instanceof WhileNode ){
            _assertType( node.childNodes().get(0) , NewlineNode.class );
            _appned( "while ( " , node );
            _add( node.childNodes().get(0).childNodes().get(0) , state );
            _appned( " ){ \n " , node );
            _add( node.childNodes().get(1) , state );
            _appned( "\n } \n " , node );
        }

        else if ( node instanceof IfNode ){
            IfNode ifn = (IfNode)node;
            _appned( "if ( " , ifn );
            if ( ifn.getCondition() != null )
                _add( ifn.getCondition() , state );
            _appned( " ){ \n " , ifn );
            if ( ifn.getThenBody() != null )
                _add( ifn.getThenBody() , state );
            _appned( " } \n " , ifn );
            if ( ifn.getElseBody() != null ){
                _appned( " else { \n " , ifn );
                _add( ifn.getElseBody() , state );
                _appned( " } \n " , ifn );
            }
        }

        // --- operators ---

        else if ( node instanceof OpAsgnOrNode ){
            OpAsgnOrNode op = (OpAsgnOrNode)node;
            _add( op.getFirstNode() , state );
            _appned( " = " , node );
            _appned( " ( " , node );
            _add( op.getFirstNode() , state );
            _appned( " || " , node );
            _add( op.getSecondNode().childNodes().get(0) , state );
            _appned( " ) " , node );
        }

        // --- vars ---

        else if ( node instanceof ArgumentNode ){
            _appned( ((ArgumentNode)node).getName() , node );
        }

        else if ( node instanceof LocalAsgnNode ){
            _assertOne( node );
            LocalAsgnNode lan = (LocalAsgnNode)node;
            _appned( "var " + lan.getName() + " = " , node );
            _add( node.childNodes().get( 0 ) , state );
            _appned( "" , node );
        }
        
        else if ( node instanceof LocalVarNode ){
            _assertNoChildren( node );
            LocalVarNode lvn = (LocalVarNode)node;
            _appned( lvn.getName() , node );
        }

        else if ( node instanceof DVarNode ){
            _assertNoChildren( node );
            DVarNode lvn = (DVarNode)node;
            _appned( lvn.getName() , node );
        }

        else if ( node instanceof ConstNode ){
            _assertNoChildren( node );
            ConstNode lvn = (ConstNode)node;
            _appned( lvn.getName() , node );
        }
        
        else if ( node instanceof DStrNode ){
            _appned( " ( " , node );
            for ( int i=0; i<node.childNodes().size(); i++ ){
                if ( i > 0 )
                    _appned( " + " , node );
                _add( node.childNodes().get(i) , state );
            }
            _appned( " ) " , node );
        }

        else if ( node instanceof EvStrNode ){
            _assertOne( node );
            _add( node.childNodes().get(0).childNodes().get(0) , state );
        }

        else if ( node instanceof Colon2Node ){
            _assertOne( node );
            Colon2Node cn = (Colon2Node)node;
            _add( node.childNodes().get(0) , state );
            _appned( "." + cn.getName() , node );
        }
        
        else if ( node instanceof AttrAssignNode ){
            AttrAssignNode aan = (AttrAssignNode)node;

            final String name = aan.getName().trim();
            
            _appned( " ( " , aan );
            _add( aan.getReceiverNode() , state );
            
            if ( name.equals( "[]=" ) ){
                _appned( "[" , aan );
                _add( aan.getArgsNode().childNodes().get(0) , state );
                _appned( "]" , aan );
                
                _appned( " = " , aan );
                _add( aan.getArgsNode().childNodes().get(1) , state );
            }
            else {
                _appned( "." + name , aan );
                _add( aan.getArgsNode().childNodes().get(0) , state );
            }
                

            _appned( " ) " , aan );

        }

        else if ( node instanceof ClassVarNode ){
            _appned( "this.__constructor__." + ((ClassVarNode)node).getName().substring(2) , node );
        }

        // --- literals ---
        
        else if ( node instanceof ArrayNode ){
            
            if ( node.childNodes() == null || 
                 node.childNodes().size() == 0 ){}
            else if ( node.childNodes().size() > 1 ){ 
                _appned( "[" , node );
                for ( int i=0; i<node.childNodes().size(); i++){
                    if ( i > 0 )
                        _appned( " , " , node );
                    _add( node.childNodes().get(i) , state );
                }
                _appned( "]" , node );
            }
            else {
                _add( node.childNodes().get(0) , state );
            }
        }

        else if ( node instanceof TrueNode ){
            _assertNoChildren( node );
            _appned( ((TrueNode)node).getName() , node );
       } 

        else if ( node instanceof FalseNode ){
            _assertNoChildren( node );
            _appned( ((FalseNode)node).getName() , node );
        }

        else if ( node instanceof FixnumNode ){
            _assertNoChildren( node );
            _appned( String.valueOf( ((FixnumNode)node).getValue() ) , node );
        }

        else if ( node instanceof StrNode ){
            _assertNoChildren( node );
            _appned( "\"" , node );
            _appned( _escape( ((StrNode)node).getValue().toString() ) , node );
            _appned( "\"" , node );
        }

        else if ( node instanceof SymbolNode ){
            _assertNoChildren( node );
            _appned( "\"" , node );
            _appned( _escape( ((SymbolNode)node).getName() ) , node );
            _appned( "\"" , node );
        }

        else if ( node instanceof HashNode ){
            _assertOne( node );
            _assertType( node.childNodes().get(0) , ArrayNode.class );
            
            _appned( " { " , node );
            List<Node> lst = node.childNodes().get(0).childNodes();
            for ( int i=0; i<lst.size(); i+=2 ){
                if ( i > 0 )
                    _appned( " , " , node );
                
                _add( lst.get(i) , state );
                _appned( " : " , node );
                _add( lst.get(i+1) , state );
                
            }
            _appned( " } " , node );
        }
        

        // --- end ---

        else {
            String msg = "don't yet support : " + node.getClass();
            msg += "\t" + node.getPosition();
            System.err.println( msg );
            _print( 0 , node );
            throw new RuntimeException( msg );
        }
    }

    // ---  code generation types ---

    void _addIterBlock( IterNode it , State state ){
        _appned( "function(" , it );
        if ( it.getVarNode() != null ){
            final Node var = it.getVarNode();
            if ( var instanceof DAsgnNode )
                _appned( ((DAsgnNode)var).getName() , it );
            else if ( var instanceof ConstDeclNode )
                _appned( ((ConstDeclNode)var).getName() , it );
            else if ( var instanceof LocalAsgnNode )
                _appned( ((LocalAsgnNode)var).getName() , it );
            else
                throw new RuntimeException( "don't know what to do with : " + var );
        }
        _appned( " ){ \n" , it );
        _add( it.getBodyNode() , state );
        _appned( " }" , it );
    }

    void _addClass( ClassNode cn , State state ){
        
        final String name = cn.getCPath().getName();
        
        state = state.child();
        state._className = name;

        _assertType( cn.childNodes().get(0) , Colon2Node.class );
        
        state._classInit = _findClassInit( cn );
        
        // constructor
        if ( state._classInit == null ){
            _appned( name + " = function(){ if ( this.__proto__ && this.__proto__.constructor ) this.__proto__.constructor.apply( this , arguments ); };" , cn );
        }
        else {
            _appned( name + " = function" , state._classInit );
            if ( state._classInit.getArgsNode() == null || 
                 state._classInit.getArgsNode().getArgs() == null )
                _appned( "()" , state._classInit );
            else
                _addArgs( state._classInit , state._classInit.getArgsNode().getArgs().childNodes() , state );
            _appned( "{\n" , state._classInit );
            _add( state._classInit.getBodyNode() , state );
            _appned( "\n}\n" , state._classInit );
        }

        if ( cn.getSuperNode() != null ){
            _appned( "\n" + name + ".prototype = new " , cn );
            _add( cn.getSuperNode() , state );
            _appned( "();\n" , cn );
        }
        
        for ( Node c : cn.childNodes() )
            _addClassPiece( c , state );
    }

    void _addModule( ModuleNode mn , State state ){
        
        final String name = 
            ( state._className == null ? "" : state._className + "." ) 
            + mn.getCPath().getName();
        
        state = state.child();
        state._className = name;
        state._classInit = null;
        state._module = true;

        _appned( name + " = {};\n" , mn );

        /*
        if ( cn.getSuperNode() != null ){
            _appned( "\n" + name + ".prototype = new " , cn );
            _add( cn.getSuperNode() , state );
            _appned( "();\n" , cn );
        }
        */
        
        for ( Node c : mn.childNodes() )
            _addClassPiece( c , state );
    }

    DefnNode _findClassInit( Node n ){
        for ( Node c : n.childNodes() ){

            if ( c instanceof DefnNode ){
                DefnNode dn = (DefnNode)c;
                if ( dn.getName().equals( "initialize" ) )
                    return dn;
            }

            if ( c instanceof BlockNode || 
                 c instanceof NewlineNode ){
                DefnNode ret = _findClassInit( c );
                if ( ret != null )
                    return ret;
            }
        }    

        return null;
    }

    void _addClassPiece( Node n , State state ){
        if ( n instanceof Colon2Node || // meta class info
             n instanceof ConstNode  // inheritance
             )
            return;

        if ( n instanceof BlockNode ){
            for ( Node c : n.childNodes() )
                _addClassPiece( c , state );
            return;
        }

        if ( n instanceof NewlineNode ){
            _addClassPiece( n.childNodes().get(0) , state );
            _appned( ";\n" , n );
            return;
        }
        
        if ( n instanceof MethodDefNode ){
            MethodDefNode dn = (MethodDefNode)n;
            if ( dn != state._classInit )
                _add( dn , state );
            return;
        }
        
        if ( n instanceof FCallNode ){
            FCallNode f = (FCallNode)n;
            _appned( _getFuncName( f ) + ".call( " + state._className + ".prototype " , f );
            if ( f.getArgsNode() != null ){
                if ( f.getArgsNode().childNodes() != null ){
                    for ( int i=0; i<f.getArgsNode().childNodes().size(); i++ ){
                        _appned( " , " , f );
                        _add( f.getArgsNode().childNodes().get(i) , state );
                    }
                }
            }
            _appned( ")" , f );
            return;
        }

        if ( n instanceof ClassVarDeclNode ){
            ClassVarDeclNode dn = (ClassVarDeclNode)n;
            _appned( state._className + "." + dn.getName().substring(2) + " = " , dn );
            _add( dn.getValueNode() , state );
            return;
        }

        if ( n instanceof ModuleNode ){
            _add( n , state );
            return;
        }

        throw new RuntimeException( "don't know about class piece : " + n.getClass() + " " + n.getPosition() );

    }

    void _addCall( CallNode call , State state ){
            
        if ( call.getName().equals( "[]" ) ){
            _add( call.childNodes().get(0) , state );
            _appned( "[" , call );
            _add( call.childNodes().get(1) , state );
            _appned( "]" , call );
            return;
        }

        if ( _isOperator( call ) ){
            _addArgs( call , call.childNodes() , state , " " + call.getName() + " " );
            return;
        }

        if ( D ){
            System.err.println( "CallNode : " + call.getName() );
            System.err.println( "\t iter : " + call.getIterNode() );
            System.err.println( "\t recv : " + call.getReceiverNode() );
            System.err.println( "\t args : " + call.getArgsNode() );
        }

        if ( call.getName().equals( "new" ) ){
            _appned( Ruby.RUBY_NEW + "( " , call );
            _add( call.getReceiverNode() , state );

            if ( call.getArgsNode() != null && 
                 call.getArgsNode().childNodes() != null ){

                for ( Node temp : call.getArgsNode().childNodes() ){
                    _appned( " , " , call );
                    _add( temp , state );
                }

            }

            _appned( " ) " , call );
            return;
        }
        
        Node self = call.getReceiverNode();
        Node args = call.getArgsNode();
        Node iter = call.getIterNode();
        
        if ( args != null && iter != null )
            throw new RuntimeException( "how can you have args and iter?" );
        
        if ( args == null )
            args = iter;

        // class method call        
        if ( self != null ){
            
            if ( args != null && args.childNodes().size() > 0 ){
                _add( call.childNodes().get(0) , state );
                _appned( "." + _getFuncName( call ) , call );
                if ( args instanceof ArrayNode )
                    _addArgs( call , args.childNodes() , state );
                else {
                    _appned( "(" , call );
                    _add( args , state );
                    _appned( ")" , call );
                }
            }
            else {
                _appned( Ruby.RUBY_CV_CALL + "( " , call);
                _add( self , state );
                _appned( " , " , call );
                _appned( "\"" + _getFuncName( call ) + "\"" , call );
                _appned( " ) " , call );
            }
            return;            
        }

        // normal function call        
        if ( args != null ){
            _appned( _getFuncName( call ) , call );
            _addArgs( call , args.childNodes() , state );
            return;
        }
        
        // no-args
        _appned( Ruby.RUBY_V_CALL + "(" , call );
        _add( call.childNodes().get(0) , state );
        _appned( "." + _getFuncName( call ) , call );
        _appned( ")" , call );

    }

    // ---- add args ----

    void _addArgs( final Node where , List<Node> lst , final State state ){
        _addArgs( where , lst , state , " , " );
    }

    void _addArgs( final Node where , final List<Node> lst , final State state , final String sep ){
        _appned( "(" , where );

        if ( lst != null ){
            for ( int i=0; i<lst.size(); i++ ){
                if ( i > 0 )
                    _appned( sep , where );
                _add( lst.get( i ) , state );
            }
        }

        _appned( ")" , where );
    }

    // ---  asserts  ---

    void _assertNoChildren( Node n ){
        if ( n.childNodes() != null && n.childNodes().size() > 0 )
            throw new RuntimeException( "has children but shouldn't" );
    }
    
    void _assertOne( Node n ){
        if ( n == null )
            throw new RuntimeException( "can't be null" );
        if ( n.childNodes() == null || n.childNodes().size() != 1 )
            throw new RuntimeException( "need exactly 1 child" );
        
    }
    
    void _assertType( Node n , Class c ){
        _assertType( n , c , null );
    }
    
    void _assertType( Node n , Class c , Node toDisplay ){
        if ( n != null && c.isAssignableFrom( n.getClass() ) )
            return;
        if ( toDisplay != null )
            _print( 0 , toDisplay );
        

        
        String msg = n + " is not an instanceof " + c;
        {
            Node line = n != null ? n : toDisplay;
            if ( line != null )
                msg += " line: " + line.getPosition();
        }
        throw new RuntimeException( msg );
    }

    // ---  utility stuff ---

    void _print( int space , Node n ){
        for ( int i=0; i<space; i++ )
            System.out.print( " " );
        
        System.out.print( n );
        if ( n instanceof INameNode )
            System.out.print( " " + ((INameNode)n).getName() + " " );
        System.out.println();
        
        for ( Node c : n.childNodes() )
            _print( space + 1 , c );
        
    }
    

    String _escape( String s ){
        StringBuilder buf = new StringBuilder( s.length() );
        for ( int i=0; i<s.length(); i++ ){
            char c = s.charAt( i );
            if(c == '\\')
                buf.append("\\\\");
            else if(c == '"')
                buf.append("\\\"");
            else if(c == '\n')
                buf.append("\\n");
            else if(c == '\r')
                buf.append("\\r");
            else 
                buf.append(c);
        }
        return buf.toString();
    }

    boolean _isOperator( CallNode node ){
        if ( node.getName().equals( "[]" ) )
            throw new RuntimeException( "array thing" );
        return _operatorNames.contains( node.getName() );
    }

    static String _getFuncName( INameNode node ){
        String name = node.getName();
        
        if ( name.equals( "puts" ) )
            return "print";

        if ( name.equals( "include" ) )
            return Ruby.RUBY_INCLUDE;
        
        return _mangleFunctionName( name );
    }
    
    static String _mangleFunctionName( String name ){
        if ( name.equals( "new" ) )
            return Ruby.RUBY_NEWNAME;
        
        if ( name.equals( "<<" ) )
            return Ruby.RUBY_SHIFT;

        if ( name.endsWith( "?" ) )
            name = name.substring( 0 , name.length() - 1 ) + "_q";
        
        if ( name.endsWith( "!" ) )
            name = name.substring( 0 , name.length() - 1 ) + "_ex";

        if ( name.endsWith( "=" ) )
            name = name.substring( 0 , name.length() - 1 ) + "_eq";

        if ( _specialNames.contains( name ) )
            return "__" + name;

        return name;
    }

    public String getJSSource(){
        String js = _js.toString();
        if ( D ) System.out.println( js );
        return js;
    }

    public JSFunction get()
        throws IOException {
        return getFunc();
    }

    public JSFunction getFunc()
        throws IOException {
        if ( _func != null )
            return _func;
        
        final String js = getJSSource();
        
        Convert c = new Convert( _name , js , false );
        _func = c.get();
        
        return _func;
    }

    class State {
        
        State(){
            this( null );
        }
        
        State( State parent ){
            _parent = parent;
        }

        State child(){
            return new State( this );
        }
        
        final State _parent;
        String _className;
        DefnNode _classInit;
        boolean _module = false;
    }

    void _appned( String s , Node where ){
        _js.append( s );
        
        for ( int i=0; i<s.length(); i++ ){
            _lineMapping.put( _line , where.getPosition().getStartLine() );
            
            if ( s.charAt( i ) != '\n' )
                continue;
            _line++;
        }
    }

    private int _line = 1;
    private Map<Integer,Integer> _lineMapping = new TreeMap<Integer,Integer>();

    final String _name;
    final List<String> _lines;
    final IRubyWarnings _warnings;
    final Node _ast;
    
    final StringBuilder _js = new StringBuilder();
    final Map<Integer,ISourcePosition> _lineMap = new TreeMap<Integer,ISourcePosition>();

    private JSFunction _func;

    static final Set<String> _operatorNames = new HashSet<String>();
    static {
        _operatorNames.add( "+" );
        _operatorNames.add( "-" );
        _operatorNames.add( "*" );
        _operatorNames.add( "/" );
        _operatorNames.add( ">" );
        _operatorNames.add( ">=" );
        _operatorNames.add( "<" );
        _operatorNames.add( "<=" );
        _operatorNames.add( "==" );
    }

    static final Set<String> _specialNames = new HashSet<String>();
    static {
        _specialNames.add( "send" );
        _specialNames.add( "include" );
        _specialNames.add( "extend" );
    }

}
