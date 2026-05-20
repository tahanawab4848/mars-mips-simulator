package mars.insightx;

/**
 * Represents a single parsed MIPS instruction for InsightX pipeline simulation.
 */
public class Instruction {

    public enum Type { R, I_LOAD, I_STORE, I_BRANCH, J, NOP, UNKNOWN }

    public final String mnemonic;
    public final Type   type;
    public final String dest;    // destination register (rd or rt), or "" if none
    public final String src1;    // first source register, or ""
    public final String src2;    // second source register, or ""
    public final String display; // original cleaned display text

    public Instruction(String mnemonic, Type type,
                       String dest, String src1, String src2,
                       String display) {
        this.mnemonic = mnemonic;
        this.type     = type;
        this.dest     = normalise(dest);
        this.src1     = normalise(src1);
        this.src2     = normalise(src2);
        this.display  = display;
    }

    /** Normalise register names: strip leading '$', lowercase. */
    private static String normalise(String reg) {
        if (reg == null) return "";
        reg = reg.trim().toLowerCase();
        if (reg.startsWith("$")) reg = reg.substring(1);
        return reg;
    }

    /** Returns true if this instruction writes to a register. */
    public boolean writesRegister() {
        return !dest.isEmpty()
            && type != Type.I_STORE
            && type != Type.I_BRANCH
            && type != Type.J
            && type != Type.NOP;
    }

    @Override
    public String toString() {
        return display;
    }
}
