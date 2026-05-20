package mars.insightx;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the pipeline schedule for one instruction:
 * the cycle at which each of the 5 stages begins, stall count, and hazards.
 */
public class Schedule {

    // Stage indices
    public static final int IF  = 0;
    public static final int ID  = 1;
    public static final int EX  = 2;
    public static final int MEM = 3;
    public static final int WB  = 4;

    public static final String[] STAGE_NAMES = { "IF", "ID", "EX", "MEM", "WB" };

    public final Instruction     instruction;
    public final int[]           stageCycle;   // stageCycle[s] = cycle when stage s starts (1-based)
    public final int             stallsBefore; // total stall cycles inserted before IF
    public final List<Hazard>    hazards;

    public Schedule(Instruction instruction, int ifCycle, int stallsBefore, List<Hazard> hazards) {
        this.instruction  = instruction;
        this.stallsBefore = stallsBefore;
        this.hazards      = hazards != null ? hazards : new ArrayList<Hazard>();
        this.stageCycle   = new int[5];
        for (int s = 0; s < 5; s++) {
            this.stageCycle[s] = ifCycle + s;
        }
    }

    /** Returns the cycle in which WB completes (i.e., WB stage start + 1). */
    public int wbEndCycle() {
        return stageCycle[WB] + 1;
    }

    public boolean hasHazardOfKind(Hazard.Kind kind) {
        for (Hazard h : hazards) {
            if (h.kind == kind) return true;
        }
        return false;
    }
}
