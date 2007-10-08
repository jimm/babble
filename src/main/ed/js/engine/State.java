// State.java

package ed.js.engine;

import java.util.*;

/**
 * this is used only be Convert
 * to keep compile state
 */
class State {
    
    State(){
        _parent = null;
    }
    
    State( State s ){
        _parent = s;
    }
    
    State child(){
        return new State( this );
    }
    
    State parent(){
        return _parent;
    }
    
    boolean hasSymbol( String s ){
        return _localSymbols.contains( s );
    }
    
    boolean addSymbol( String s ){
        return _localSymbols.add( s );
    }

    boolean useLocalVariable( String name ){
        return 
            ! _hasLambdaExpressions && 
            ( _badLocals == null || ! _badLocals.contains( name ) );
    }
    
    void addBadLocal( String s ){
        if ( _badLocals == null )
            _badLocals = new HashSet<String>();
        _badLocals.add( s );
    }

    final Set<String> _localSymbols = new HashSet<String>();
    final Map<Integer,String> _functionIdToName = new HashMap<Integer,String>();
    final Stack<String> _tempOpNames = new Stack<String>();

    final State _parent;
    
    boolean _hasLambdaExpressions = true;
    private  Set<String> _badLocals;
}
