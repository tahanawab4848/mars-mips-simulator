package mars.ccompiler.builtin;

import java.util.ArrayList;
import java.util.List;

/** Recursive descent parser for the built-in C transpiler. */
public class CParser {
    private final List<CToken> tokens;
    private int pos = 0;

    public CParser(List<CToken> tokens) {
        this.tokens = tokens;
    }

    public CASTNode.Program parse() throws CTranspileException {
        CASTNode.Program prog = new CASTNode.Program(1);
        while (!isAtEnd()) {
            prog.decls.add(parseDecl());
        }
        return prog;
    }

    private CASTNode parseDecl() throws CTranspileException {
        String type = parseType("Expected type");
        String name = consumeIdent("Expected name");

        if (match(CToken.Type.LPAREN)) {
            // Function
            CASTNode.FuncDecl func = new CASTNode.FuncDecl(peek(-2).line, type, name);
            if (!check(CToken.Type.RPAREN)) {
                do {
                    String pType = parseType("Expected param type");
                    String pName = consumeIdent("Expected param name");
                    func.params.add(new CASTNode.Param(pType, pName));
                } while (match(CToken.Type.COMMA));
            }
            consume(CToken.Type.RPAREN, "Expected ')' after parameters");
            func.body = parseBlock();
            return func;
        } else {
            // Global Var
            CASTNode.Expr arraySize = null;
            if (match(CToken.Type.LBRACKET)) {
                arraySize = parseExpr();
                consume(CToken.Type.RBRACKET, "Expected ']'");
            }
            CASTNode.Expr init = null;
            if (match(CToken.Type.ASSIGN)) {
                init = parseExpr();
            }
            consume(CToken.Type.SEMICOLON, "Expected ';' after variable declaration");
            return new CASTNode.VarDecl(peek(-1).line, type, name, init, arraySize);
        }
    }

    private CASTNode.Block parseBlock() throws CTranspileException {
        consume(CToken.Type.LBRACE, "Expected '{'");
        CASTNode.Block block = new CASTNode.Block(peek(-1).line);
        while (!check(CToken.Type.RBRACE) && !isAtEnd()) {
            block.stmts.add(parseStmt());
        }
        consume(CToken.Type.RBRACE, "Expected '}'");
        return block;
    }

    private CASTNode.Stmt parseStmt() throws CTranspileException {
        if (match(CToken.Type.LBRACE)) {
            pos--; // back up to let parseBlock consume '{'
            return parseBlock();
        }
        if (match(CToken.Type.INT) || match(CToken.Type.FLOAT) || match(CToken.Type.CHAR)) {
            pos--; // let parseVarDecl see the type
            return parseVarDecl();
        }
        if (match(CToken.Type.IF)) return parseIfStmt();
        if (match(CToken.Type.WHILE)) return parseWhileStmt();
        if (match(CToken.Type.FOR)) return parseForStmt();
        if (match(CToken.Type.RETURN)) return parseReturnStmt();
        if (match(CToken.Type.BREAK)) {
            CASTNode.BreakStmt b = new CASTNode.BreakStmt(peek(-1).line);
            consume(CToken.Type.SEMICOLON, "Expected ';'");
            return b;
        }
        if (match(CToken.Type.CONTINUE)) {
            CASTNode.ContinueStmt c = new CASTNode.ContinueStmt(peek(-1).line);
            consume(CToken.Type.SEMICOLON, "Expected ';'");
            return c;
        }

        CASTNode.Expr expr = parseExpr();
        consume(CToken.Type.SEMICOLON, "Expected ';'");
        return new CASTNode.ExprStmt(peek(-1).line, expr);
    }

    private CASTNode.VarDecl parseVarDecl() throws CTranspileException {
        String type = parseType("Expected type");
        String name = consumeIdent("Expected variable name");
        CASTNode.Expr arraySize = null;
        if (match(CToken.Type.LBRACKET)) {
            arraySize = parseExpr();
            consume(CToken.Type.RBRACKET, "Expected ']'");
        }
        CASTNode.Expr init = null;
        if (match(CToken.Type.ASSIGN)) {
            init = parseExpr();
        }
        consume(CToken.Type.SEMICOLON, "Expected ';' after variable declaration");
        return new CASTNode.VarDecl(peek(-1).line, type, name, init, arraySize);
    }

    private CASTNode.IfStmt parseIfStmt() throws CTranspileException {
        int line = peek(-1).line;
        consume(CToken.Type.LPAREN, "Expected '(' after if");
        CASTNode.Expr cond = parseExpr();
        consume(CToken.Type.RPAREN, "Expected ')' after condition");
        CASTNode.Stmt thenBranch = parseStmt();
        CASTNode.Stmt elseBranch = null;
        if (match(CToken.Type.ELSE)) {
            elseBranch = parseStmt();
        }
        return new CASTNode.IfStmt(line, cond, thenBranch, elseBranch);
    }

    private CASTNode.WhileStmt parseWhileStmt() throws CTranspileException {
        int line = peek(-1).line;
        consume(CToken.Type.LPAREN, "Expected '(' after while");
        CASTNode.Expr cond = parseExpr();
        consume(CToken.Type.RPAREN, "Expected ')' after condition");
        CASTNode.Stmt body = parseStmt();
        return new CASTNode.WhileStmt(line, cond, body);
    }

    private CASTNode.ForStmt parseForStmt() throws CTranspileException {
        int line = peek(-1).line;
        consume(CToken.Type.LPAREN, "Expected '(' after for");
        CASTNode.Stmt init = null;
        if (!match(CToken.Type.SEMICOLON)) {
            if (check(CToken.Type.INT) || check(CToken.Type.FLOAT) || check(CToken.Type.CHAR)) {
                init = parseVarDecl(); // consumes semicolon
            } else {
                init = new CASTNode.ExprStmt(peek().line, parseExpr());
                consume(CToken.Type.SEMICOLON, "Expected ';'");
            }
        }
        CASTNode.Expr cond = null;
        if (!check(CToken.Type.SEMICOLON)) {
            cond = parseExpr();
        }
        consume(CToken.Type.SEMICOLON, "Expected ';'");
        CASTNode.Expr step = null;
        if (!check(CToken.Type.RPAREN)) {
            step = parseExpr();
        }
        consume(CToken.Type.RPAREN, "Expected ')' after for clauses");
        CASTNode.Stmt body = parseStmt();
        return new CASTNode.ForStmt(line, init, cond, step, body);
    }

    private CASTNode.ReturnStmt parseReturnStmt() throws CTranspileException {
        int line = peek(-1).line;
        CASTNode.Expr val = null;
        if (!check(CToken.Type.SEMICOLON)) {
            val = parseExpr();
        }
        consume(CToken.Type.SEMICOLON, "Expected ';'");
        return new CASTNode.ReturnStmt(line, val);
    }

    // --- Expressions ---
    private CASTNode.Expr parseExpr() throws CTranspileException {
        return parseAssign();
    }

    private CASTNode.Expr parseAssign() throws CTranspileException {
        CASTNode.Expr expr = parseLogicalOr();
        if (match(CToken.Type.ASSIGN)) {
            if (expr instanceof CASTNode.Ident || expr instanceof CASTNode.ArrayAccess || expr instanceof CASTNode.Dereference) {
                CASTNode.Expr value = parseAssign();
                return new CASTNode.Assign(expr.line, expr, value);
            }
            throw new CTranspileException("Invalid assignment target at line " + expr.line);
        }
        return expr;
    }

    private CASTNode.Expr parseLogicalOr() throws CTranspileException {
        CASTNode.Expr expr = parseLogicalAnd();
        while (match(CToken.Type.OR)) {
            CToken op = peek(-1);
            CASTNode.Expr right = parseLogicalAnd();
            expr = new CASTNode.BinaryOp(expr.line, op.type, expr, right);
        }
        return expr;
    }

    private CASTNode.Expr parseLogicalAnd() throws CTranspileException {
        CASTNode.Expr expr = parseEquality();
        while (match(CToken.Type.AND)) {
            CToken op = peek(-1);
            CASTNode.Expr right = parseEquality();
            expr = new CASTNode.BinaryOp(expr.line, op.type, expr, right);
        }
        return expr;
    }

    private CASTNode.Expr parseEquality() throws CTranspileException {
        CASTNode.Expr expr = parseRelational();
        while (match(CToken.Type.EQ) || match(CToken.Type.NEQ)) {
            CToken op = peek(-1);
            CASTNode.Expr right = parseRelational();
            expr = new CASTNode.BinaryOp(expr.line, op.type, expr, right);
        }
        return expr;
    }

    private CASTNode.Expr parseRelational() throws CTranspileException {
        CASTNode.Expr expr = parseTerm();
        while (match(CToken.Type.LT) || match(CToken.Type.LE) || match(CToken.Type.GT) || match(CToken.Type.GE)) {
            CToken op = peek(-1);
            CASTNode.Expr right = parseTerm();
            expr = new CASTNode.BinaryOp(expr.line, op.type, expr, right);
        }
        return expr;
    }

    private CASTNode.Expr parseTerm() throws CTranspileException {
        CASTNode.Expr expr = parseFactor();
        while (match(CToken.Type.PLUS) || match(CToken.Type.MINUS)) {
            CToken op = peek(-1);
            CASTNode.Expr right = parseFactor();
            expr = new CASTNode.BinaryOp(expr.line, op.type, expr, right);
        }
        return expr;
    }

    private CASTNode.Expr parseFactor() throws CTranspileException {
        CASTNode.Expr expr = parseUnary();
        while (match(CToken.Type.STAR) || match(CToken.Type.SLASH) || match(CToken.Type.PERCENT)) {
            CToken op = peek(-1);
            CASTNode.Expr right = parseUnary();
            expr = new CASTNode.BinaryOp(expr.line, op.type, expr, right);
        }
        return expr;
    }

    private CASTNode.Expr parseUnary() throws CTranspileException {
        if (match(CToken.Type.AMP)) {
            return new CASTNode.AddressOf(peek(-1).line, parseUnary());
        }
        if (match(CToken.Type.STAR)) {
            return new CASTNode.Dereference(peek(-1).line, parseUnary());
        }
        // Just primitive unary ops
        return parsePrimary();
    }

    private CASTNode.Expr parsePrimary() throws CTranspileException {
        if (match(CToken.Type.INT_LIT)) {
            return new CASTNode.IntLit(peek(-1).line, Integer.parseInt(peek(-1).value));
        }
        if (match(CToken.Type.IDENT)) {
            String name = peek(-1).value;
            int line = peek(-1).line;
            if (match(CToken.Type.LPAREN)) {
                // Function call
                List<CASTNode.Expr> args = new ArrayList<CASTNode.Expr>();
                if (!check(CToken.Type.RPAREN)) {
                    do {
                        args.add(parseExpr());
                    } while (match(CToken.Type.COMMA));
                }
                consume(CToken.Type.RPAREN, "Expected ')' after arguments");
                return new CASTNode.Call(line, name, args);
            }
            if (match(CToken.Type.LBRACKET)) {
                // Array access
                CASTNode.Expr index = parseExpr();
                consume(CToken.Type.RBRACKET, "Expected ']' after array index");
                return new CASTNode.ArrayAccess(line, name, index);
            }
            return new CASTNode.Ident(line, name);
        }
        if (match(CToken.Type.LPAREN)) {
            CASTNode.Expr expr = parseExpr();
            consume(CToken.Type.RPAREN, "Expected ')'");
            return expr;
        }

        throw new CTranspileException("Unexpected token " + peek().type + " at line " + peek().line);
    }

    // --- Helpers ---
    private boolean check(CToken.Type type) {
        if (isAtEnd()) return false;
        return peek().type == type;
    }

    private boolean match(CToken.Type type) {
        if (check(type)) { pos++; return true; }
        return false;
    }

    private void consume(CToken.Type type, String msg) throws CTranspileException {
        if (check(type)) pos++;
        else throw new CTranspileException(msg + " at line " + peek().line);
    }

    private String parseType(String msg) throws CTranspileException {
        String base = consumeIdent(msg);
        while (match(CToken.Type.STAR)) {
            base += "*";
        }
        return base;
    }

    private String consumeIdent(String msg) throws CTranspileException {
        if (match(CToken.Type.IDENT)) return peek(-1).value;
        if (match(CToken.Type.INT)) return "int";
        if (match(CToken.Type.FLOAT)) return "float";
        if (match(CToken.Type.CHAR)) return "char";
        if (match(CToken.Type.VOID)) return "void";
        throw new CTranspileException(msg + " at line " + peek().line);
    }

    private CToken peek() { return tokens.get(pos); }
    private CToken peek(int offset) { return tokens.get(pos + offset); }
    private boolean isAtEnd() { return peek().type == CToken.Type.EOF; }
}
