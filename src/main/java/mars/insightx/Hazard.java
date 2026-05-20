package mars.insightx;

/**
 * Records a detected pipeline hazard for one instruction.
 */
public class Hazard {

    public enum Kind {
        RAW,         // Read-After-Write data dependency
        LOAD_USE,    // lw followed immediately by dependent instruction
        CONTROL      // branch / jump control hazard
    }

    public final Kind   kind;
    public final int    producerIndex; // index of instruction that causes the hazard (-1 for control)
    public final String register;      // register involved ("" for control hazards)
    public final int    stallCycles;   // number of stall cycles this hazard contributes

    public Hazard(Kind kind, int producerIndex, String register, int stallCycles) {
        this.kind          = kind;
        this.producerIndex = producerIndex;
        this.register      = register;
        this.stallCycles   = stallCycles;
    }

    @Override
    public String toString() {
        switch (kind) {
            case RAW:      return "RAW on $" + register + " (instr " + producerIndex + ")";
            case LOAD_USE: return "Load-Use on $" + register;
            case CONTROL:  return "Control hazard (1 bubble)";
            default:       return kind.name();
        }
    }
}
