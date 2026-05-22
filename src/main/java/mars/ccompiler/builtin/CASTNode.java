package mars.ccompiler.builtin;

import java.util.ArrayList;
import java.util.List;

/** AST Node for the built-in C transpiler. */
public abstract class CASTNode {
    public int line;

    public CASTNode(int line) { this.line = line; }

    // --- Expressions ---
    public static abstract class Expr extends CASTNode {
        public Expr(int line) { super(line); }
    }

    public static class IntLit extends Expr {
        public int value;
        public IntLit(int line, int value) { super(line); this.value = value; }
    }

    public static class Ident extends Expr {
        public String name;
        public Ident(int line, String name) { super(line); this.name = name; }
    }

    public static class AddressOf extends Expr {
        public Expr expr;
        public AddressOf(int line, Expr expr) { super(line); this.expr = expr; }
    }

    public static class Dereference extends Expr {
        public Expr expr;
        public Dereference(int line, Expr expr) { super(line); this.expr = expr; }
    }

    public static class BinaryOp extends Expr {
        public CToken.Type op;
        public Expr left, right;
        public BinaryOp(int line, CToken.Type op, Expr left, Expr right) {
            super(line); this.op = op; this.left = left; this.right = right;
        }
    }

    public static class Assign extends Expr {
        public Expr target;
        public Expr value;
        public Assign(int line, Expr target, Expr value) {
            super(line); this.target = target; this.value = value;
        }
    }

    public static class Call extends Expr {
        public String funcName;
        public List<Expr> args;
        public Call(int line, String funcName, List<Expr> args) {
            super(line); this.funcName = funcName; this.args = args;
        }
    }
    
    public static class ArrayAccess extends Expr {
        public String name;
        public Expr index;
        public ArrayAccess(int line, String name, Expr index) {
            super(line); this.name = name; this.index = index;
        }
    }

    // --- Statements ---
    public static abstract class Stmt extends CASTNode {
        public Stmt(int line) { super(line); }
    }

    public static class Block extends Stmt {
        public List<Stmt> stmts = new ArrayList<Stmt>();
        public Block(int line) { super(line); }
    }

    public static class ExprStmt extends Stmt {
        public Expr expr;
        public ExprStmt(int line, Expr expr) { super(line); this.expr = expr; }
    }

    public static class VarDecl extends Stmt {
        public String type;
        public String name;
        public Expr init; // can be null
        public Expr arraySize; // if it's an array
        public VarDecl(int line, String type, String name, Expr init, Expr arraySize) {
            super(line); this.type = type; this.name = name; this.init = init; this.arraySize = arraySize;
        }
    }

    public static class IfStmt extends Stmt {
        public Expr cond;
        public Stmt thenStmt;
        public Stmt elseStmt; // can be null
        public IfStmt(int line, Expr cond, Stmt thenStmt, Stmt elseStmt) {
            super(line); this.cond = cond; this.thenStmt = thenStmt; this.elseStmt = elseStmt;
        }
    }

    public static class WhileStmt extends Stmt {
        public Expr cond;
        public Stmt body;
        public WhileStmt(int line, Expr cond, Stmt body) {
            super(line); this.cond = cond; this.body = body;
        }
    }

    public static class ForStmt extends Stmt {
        public Stmt init;
        public Expr cond;
        public Expr step;
        public Stmt body;
        public ForStmt(int line, Stmt init, Expr cond, Expr step, Stmt body) {
            super(line); this.init = init; this.cond = cond; this.step = step; this.body = body;
        }
    }

    public static class ReturnStmt extends Stmt {
        public Expr expr; // can be null
        public ReturnStmt(int line, Expr expr) { super(line); this.expr = expr; }
    }
    
    public static class BreakStmt extends Stmt {
        public BreakStmt(int line) { super(line); }
    }
    
    public static class ContinueStmt extends Stmt {
        public ContinueStmt(int line) { super(line); }
    }

    // --- Declarations ---
    public static class FuncDecl extends CASTNode {
        public String retType;
        public String name;
        public List<Param> params = new ArrayList<Param>();
        public Block body;
        public FuncDecl(int line, String retType, String name) {
            super(line); this.retType = retType; this.name = name;
        }
    }

    public static class Param {
        public String type;
        public String name;
        public Param(String type, String name) { this.type = type; this.name = name; }
    }

    public static class Program extends CASTNode {
        public List<CASTNode> decls = new ArrayList<CASTNode>(); // FuncDecl or VarDecl
        public Program(int line) { super(line); }
    }
}
