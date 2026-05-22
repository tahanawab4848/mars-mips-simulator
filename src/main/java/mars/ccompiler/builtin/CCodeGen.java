package mars.ccompiler.builtin;

import java.util.*;

/** A simple AST-walking MIPS code generator. */
public class CCodeGen {
    private final CASTNode.Program prog;
    private final StringBuilder out = new StringBuilder();

    private int labelCount = 0;
    private final Map<String, Integer> localOffsets = new HashMap<>();
    private final Map<String, Integer> globalSizes = new HashMap<>();
    private int currentOffset = 0;
    private String currentFunc = "";

    public CCodeGen(CASTNode.Program prog) {
        this.prog = prog;
    }

    public String generate() throws CTranspileException {
        out.append(".data\n");
        // Globals
        for (CASTNode node : prog.decls) {
            if (node instanceof CASTNode.VarDecl) {
                CASTNode.VarDecl var = (CASTNode.VarDecl) node;
                int size = 4;
                if (var.arraySize != null && var.arraySize instanceof CASTNode.IntLit) {
                    size = ((CASTNode.IntLit)var.arraySize).value * 4;
                }
                globalSizes.put(var.name, size);
                out.append(var.name).append(":\n");
                if (var.init != null && var.init instanceof CASTNode.IntLit) {
                    out.append("  .word ").append(((CASTNode.IntLit)var.init).value).append("\n");
                } else {
                    out.append("  .space ").append(size).append("\n");
                }
            }
        }

        out.append("\n.text\n");
        // Startup code to call main and then exit
        out.append(".globl main\n");
        out.append("  jal main\n");
        out.append("  li $v0, 10\n");
        out.append("  syscall\n\n");

        for (CASTNode node : prog.decls) {
            if (node instanceof CASTNode.FuncDecl) {
                genFunc((CASTNode.FuncDecl) node);
            }
        }
        return out.toString();
    }

    private void genFunc(CASTNode.FuncDecl func) throws CTranspileException {
        currentFunc = func.name;
        localOffsets.clear();
        currentOffset = 0;

        out.append(func.name).append(":\n");
        // Setup frame
        out.append("  .loc 1 ").append(func.line).append("\n");
        out.append("  addi $sp, $sp, -4\n");
        out.append("  sw $ra, 0($sp)\n");
        out.append("  addi $sp, $sp, -4\n");
        out.append("  sw $fp, 0($sp)\n");
        out.append("  move $fp, $sp\n");

        // Pre-allocate locals & map params
        int paramOffset = 8; // above $fp: old_fp, ra, arg0, arg1...
        for (CASTNode.Param p : func.params) {
            localOffsets.put(p.name, paramOffset);
            paramOffset += 4;
        }

        // Just reserve a big chunk for locals to be safe
        int maxLocals = countLocals(func.body);
        if (maxLocals > 0) {
            out.append("  addi $sp, $sp, -").append(maxLocals * 4).append("\n");
        }
        
        currentOffset = -4; // First local is below fp

        genBlock(func.body);

        // Epilogue (if no explicit return hit)
        out.append(func.name).append("_end:\n");
        out.append("  move $sp, $fp\n");
        out.append("  lw $fp, 0($sp)\n");
        out.append("  addi $sp, $sp, 4\n");
        out.append("  lw $ra, 0($sp)\n");
        out.append("  addi $sp, $sp, 4\n");
        out.append("  jr $ra\n\n");
    }

    private int countLocals(CASTNode.Stmt stmt) {
        if (stmt instanceof CASTNode.Block) {
            int c = 0;
            for (CASTNode.Stmt s : ((CASTNode.Block)stmt).stmts) c += countLocals(s);
            return c;
        }
        if (stmt instanceof CASTNode.VarDecl) {
            CASTNode.VarDecl v = (CASTNode.VarDecl) stmt;
            if (v.arraySize != null && v.arraySize instanceof CASTNode.IntLit) {
                return ((CASTNode.IntLit)v.arraySize).value;
            }
            return 1;
        }
        if (stmt instanceof CASTNode.IfStmt) {
            CASTNode.IfStmt i = (CASTNode.IfStmt) stmt;
            return countLocals(i.thenStmt) + (i.elseStmt != null ? countLocals(i.elseStmt) : 0);
        }
        if (stmt instanceof CASTNode.WhileStmt) return countLocals(((CASTNode.WhileStmt)stmt).body);
        if (stmt instanceof CASTNode.ForStmt) {
            CASTNode.ForStmt f = (CASTNode.ForStmt) stmt;
            return countLocals(f.init) + countLocals(f.body);
        }
        return 0;
    }

    private void genStmt(CASTNode.Stmt stmt) throws CTranspileException {
        if (stmt == null) return;
        out.append("  .loc 1 ").append(stmt.line).append("\n");

        if (stmt instanceof CASTNode.Block) {
            genBlock((CASTNode.Block) stmt);
        } else if (stmt instanceof CASTNode.ExprStmt) {
            genExpr(((CASTNode.ExprStmt) stmt).expr);
        } else if (stmt instanceof CASTNode.VarDecl) {
            CASTNode.VarDecl var = (CASTNode.VarDecl) stmt;
            int size = 4;
            if (var.arraySize != null && var.arraySize instanceof CASTNode.IntLit) {
                size = ((CASTNode.IntLit)var.arraySize).value * 4;
            }
            localOffsets.put(var.name, currentOffset);
            currentOffset -= size;
            if (var.init != null) {
                genExpr(var.init);
                int off = localOffsets.get(var.name);
                out.append("  sw $v0, ").append(off).append("($fp)\n");
            }
        } else if (stmt instanceof CASTNode.IfStmt) {
            CASTNode.IfStmt i = (CASTNode.IfStmt) stmt;
            String lElse = nextLabel("else");
            String lEnd = nextLabel("endif");
            genExpr(i.cond);
            out.append("  beq $v0, $zero, ").append(lElse).append("\n");
            genStmt(i.thenStmt);
            out.append("  j ").append(lEnd).append("\n");
            out.append(lElse).append(":\n");
            if (i.elseStmt != null) genStmt(i.elseStmt);
            out.append(lEnd).append(":\n");
        } else if (stmt instanceof CASTNode.WhileStmt) {
            CASTNode.WhileStmt w = (CASTNode.WhileStmt) stmt;
            String lStart = nextLabel("while");
            String lEnd = nextLabel("endwhile");
            out.append(lStart).append(":\n");
            genExpr(w.cond);
            out.append("  beq $v0, $zero, ").append(lEnd).append("\n");
            genStmt(w.body);
            out.append("  j ").append(lStart).append("\n");
            out.append(lEnd).append(":\n");
        } else if (stmt instanceof CASTNode.ForStmt) {
            CASTNode.ForStmt f = (CASTNode.ForStmt) stmt;
            String lStart = nextLabel("for");
            String lEnd = nextLabel("endfor");
            if (f.init != null) genStmt(f.init);
            out.append(lStart).append(":\n");
            if (f.cond != null) {
                genExpr(f.cond);
                out.append("  beq $v0, $zero, ").append(lEnd).append("\n");
            }
            genStmt(f.body);
            if (f.step != null) {
                out.append("  .loc 1 ").append(f.step.line).append("\n");
                genExpr(f.step);
            }
            out.append("  j ").append(lStart).append("\n");
            out.append(lEnd).append(":\n");
        } else if (stmt instanceof CASTNode.ReturnStmt) {
            CASTNode.ReturnStmt r = (CASTNode.ReturnStmt) stmt;
            if (r.expr != null) genExpr(r.expr);
            out.append("  j ").append(currentFunc).append("_end\n");
        }
    }

    private void genBlock(CASTNode.Block block) throws CTranspileException {
        for (CASTNode.Stmt s : block.stmts) genStmt(s);
    }

    private void genExpr(CASTNode.Expr expr) throws CTranspileException {
        if (expr instanceof CASTNode.IntLit) {
            out.append("  li $v0, ").append(((CASTNode.IntLit) expr).value).append("\n");
        } else if (expr instanceof CASTNode.Ident) {
            String name = ((CASTNode.Ident) expr).name;
            if (localOffsets.containsKey(name)) {
                out.append("  lw $v0, ").append(localOffsets.get(name)).append("($fp)\n");
            } else if (globalSizes.containsKey(name)) {
                out.append("  lw $v0, ").append(name).append("\n");
            } else {
                throw new CTranspileException("Unknown variable: " + name + " at line " + expr.line);
            }
        } else if (expr instanceof CASTNode.Assign) {
            CASTNode.Assign asg = (CASTNode.Assign) expr;
            genExpr(asg.value);
            
            if (asg.target instanceof CASTNode.Ident) {
                String name = ((CASTNode.Ident) asg.target).name;
                if (localOffsets.containsKey(name)) {
                    out.append("  sw $v0, ").append(localOffsets.get(name)).append("($fp)\n");
                } else if (globalSizes.containsKey(name)) {
                    out.append("  sw $v0, ").append(name).append("\n");
                } else {
                    throw new CTranspileException("Unknown variable: " + name + " at line " + expr.line);
                }
            } else if (asg.target instanceof CASTNode.ArrayAccess) {
                CASTNode.ArrayAccess acc = (CASTNode.ArrayAccess) asg.target;
                out.append("  addi $sp, $sp, -4\n");
                out.append("  sw $v0, 0($sp)\n"); // save RHS value
                
                genExpr(acc.index);
                out.append("  li $t0, 4\n");
                out.append("  mul $v0, $v0, $t0\n"); // byte offset
                
                if (localOffsets.containsKey(acc.name)) {
                    int baseOff = localOffsets.get(acc.name);
                    out.append("  li $t1, ").append(baseOff).append("\n");
                    out.append("  sub $t1, $t1, $v0\n"); 
                    out.append("  add $t1, $fp, $t1\n"); // target addr
                    out.append("  lw $v0, 0($sp)\n"); // restore RHS
                    out.append("  addi $sp, $sp, 4\n");
                    out.append("  sw $v0, 0($t1)\n");
                } else if (globalSizes.containsKey(acc.name)) {
                    out.append("  la $t1, ").append(acc.name).append("\n");
                    out.append("  add $t1, $t1, $v0\n");
                    out.append("  lw $v0, 0($sp)\n"); // restore RHS
                    out.append("  addi $sp, $sp, 4\n");
                    out.append("  sw $v0, 0($t1)\n");
                } else {
                    throw new CTranspileException("Unknown array: " + acc.name + " at line " + expr.line);
                }
            } else if (asg.target instanceof CASTNode.Dereference) {
                CASTNode.Dereference deref = (CASTNode.Dereference) asg.target;
                out.append("  addi $sp, $sp, -4\n");
                out.append("  sw $v0, 0($sp)\n"); // save RHS value
                
                genExpr(deref.expr);
                out.append("  move $t1, $v0\n"); // $t1 = ptr address
                out.append("  lw $v0, 0($sp)\n"); // restore RHS
                out.append("  addi $sp, $sp, 4\n");
                out.append("  sw $v0, 0($t1)\n");
            }
        } else if (expr instanceof CASTNode.BinaryOp) {
            CASTNode.BinaryOp bin = (CASTNode.BinaryOp) expr;
            genExpr(bin.left);
            out.append("  addi $sp, $sp, -4\n");
            out.append("  sw $v0, 0($sp)\n"); // save left
            genExpr(bin.right);
            out.append("  lw $t0, 0($sp)\n"); // $t0 = left, $v0 = right
            out.append("  addi $sp, $sp, 4\n");

            switch (bin.op) {
                case PLUS:  out.append("  add $v0, $t0, $v0\n"); break;
                case MINUS: out.append("  sub $v0, $t0, $v0\n"); break;
                case STAR:  out.append("  mul $v0, $t0, $v0\n"); break;
                case SLASH: out.append("  div $t0, $v0\n  mflo $v0\n"); break;
                case PERCENT: out.append("  div $t0, $v0\n  mfhi $v0\n"); break;
                case EQ:    out.append("  seq $v0, $t0, $v0\n"); break;
                case NEQ:   out.append("  sne $v0, $t0, $v0\n"); break;
                case LT:    out.append("  slt $v0, $t0, $v0\n"); break;
                case LE:    out.append("  sle $v0, $t0, $v0\n"); break;
                case GT:    out.append("  sgt $v0, $t0, $v0\n"); break;
                case GE:    out.append("  sge $v0, $t0, $v0\n"); break;
                default: throw new CTranspileException("Unknown op: " + bin.op + " at line " + expr.line);
            }
        } else if (expr instanceof CASTNode.Call) {
            CASTNode.Call call = (CASTNode.Call) expr;
            if ("malloc".equals(call.funcName) && call.args.size() == 1) {
                genExpr(call.args.get(0));
                out.append("  move $a0, $v0\n");
                out.append("  li $v0, 9\n");
                out.append("  syscall\n");
            } else if ("free".equals(call.funcName) && call.args.size() == 1) {
                out.append("  # free() called - ignored in basic MARS\n");
            } else {
                // Push args
                for (int i = call.args.size() - 1; i >= 0; i--) {
                    genExpr(call.args.get(i));
                    out.append("  addi $sp, $sp, -4\n");
                    out.append("  sw $v0, 0($sp)\n");
                }
                out.append("  jal ").append(call.funcName).append("\n");
                // Pop args
                if (call.args.size() > 0) {
                    out.append("  addi $sp, $sp, ").append(call.args.size() * 4).append("\n");
                }
            }
        } else if (expr instanceof CASTNode.ArrayAccess) {
            CASTNode.ArrayAccess acc = (CASTNode.ArrayAccess) expr;
            genExpr(acc.index);
            out.append("  li $t0, 4\n");
            out.append("  mul $v0, $v0, $t0\n"); // byte offset
            if (localOffsets.containsKey(acc.name)) {
                int baseOff = localOffsets.get(acc.name);
                out.append("  li $t1, ").append(baseOff).append("\n");
                out.append("  sub $t1, $t1, $v0\n"); // arrays grow downwards in locals space usually... wait, let's just do base - (index*4) 
                out.append("  add $t1, $fp, $t1\n"); // addr
                out.append("  lw $v0, 0($t1)\n");
            } else if (globalSizes.containsKey(acc.name)) {
                out.append("  la $t1, ").append(acc.name).append("\n");
                out.append("  add $t1, $t1, $v0\n");
                out.append("  lw $v0, 0($t1)\n");
            } else {
                throw new CTranspileException("Unknown array: " + acc.name + " at line " + expr.line);
            }
        } else if (expr instanceof CASTNode.AddressOf) {
            CASTNode.AddressOf addrOf = (CASTNode.AddressOf) expr;
            if (addrOf.expr instanceof CASTNode.Ident) {
                String name = ((CASTNode.Ident) addrOf.expr).name;
                if (localOffsets.containsKey(name)) {
                    int off = localOffsets.get(name);
                    out.append("  li $v0, ").append(off).append("\n");
                    out.append("  add $v0, $fp, $v0\n");
                } else if (globalSizes.containsKey(name)) {
                    out.append("  la $v0, ").append(name).append("\n");
                } else {
                    throw new CTranspileException("Unknown variable for AddressOf: " + name + " at line " + expr.line);
                }
            } else if (addrOf.expr instanceof CASTNode.ArrayAccess) {
                CASTNode.ArrayAccess acc = (CASTNode.ArrayAccess) addrOf.expr;
                genExpr(acc.index);
                out.append("  li $t0, 4\n");
                out.append("  mul $v0, $v0, $t0\n"); // byte offset
                if (localOffsets.containsKey(acc.name)) {
                    int baseOff = localOffsets.get(acc.name);
                    out.append("  li $t1, ").append(baseOff).append("\n");
                    out.append("  sub $t1, $t1, $v0\n"); 
                    out.append("  add $v0, $fp, $t1\n"); // addr in $v0
                } else if (globalSizes.containsKey(acc.name)) {
                    out.append("  la $t1, ").append(acc.name).append("\n");
                    out.append("  add $v0, $t1, $v0\n");
                } else {
                    throw new CTranspileException("Unknown array: " + acc.name + " at line " + expr.line);
                }
            } else {
                throw new CTranspileException("AddressOf requires an identifier or array access at line " + expr.line);
            }
        } else if (expr instanceof CASTNode.Dereference) {
            CASTNode.Dereference deref = (CASTNode.Dereference) expr;
            genExpr(deref.expr);
            out.append("  lw $v0, 0($v0)\n");
        }
    }

    private String nextLabel(String prefix) {
        return prefix + "_" + (++labelCount);
    }
}
