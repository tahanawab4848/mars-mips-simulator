package mars.ccompiler.builtin;

/** Transpile-time exception from the built-in C compiler. */
public class CTranspileException extends Exception {
    public CTranspileException(String msg) { super(msg); }
}
