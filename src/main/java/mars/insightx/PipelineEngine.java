package mars.insightx;

import java.util.*;

/**
 * PipelineEngine — simulates a simplified 5-stage in-order MIPS pipeline.
 *
 * Hazard model (Patterson & Hennessy):
 *
 *  With forwarding (default):
 *    - EX/MEM and MEM/WB forwarding eliminates pure RAW stalls.
 *    - Load-use (lw immediately followed by dependent instruction) still costs 1 stall.
 *    - Branch/jump causes 1 bubble.
 *
 *  Without forwarding:
 *    - A producer completes WB at cycle (IF+4). A consumer needs the value at its EX stage.
 *    - Distance 1 (back-to-back)   → 2 stalls
 *    - Distance 2 (one instr gap)  → 1 stall
 *    - Distance 3+                 → 0 stalls  (value available before EX)
 *    - Branch/jump causes 1 bubble regardless.
 */
public class PipelineEngine {

    private final boolean forwarding;

    public PipelineEngine(boolean forwarding) {
        this.forwarding = forwarding;
    }

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Schedule the given instruction list and return one Schedule per instruction.
     */
    public List<Schedule> schedule(List<Instruction> instructions) {
        List<Schedule> result = new ArrayList<Schedule>();
        int n = instructions.size();
        if (n == 0) return result;

        int[] ifCycles = new int[n]; // cycle in which instruction i starts IF

        for (int i = 0; i < n; i++) {
            Instruction instr = instructions.get(i);

            // Ideal start: immediately after previous instruction's IF
            int idealStart = (i == 0) ? 1 : ifCycles[i - 1] + 1;

            // Compute required stalls from hazards against previous instructions
            List<Hazard> hazards = new ArrayList<Hazard>();
            int maxDelay = 0;

            // Check up to 4 prior instructions for data dependencies
            for (int j = Math.max(0, i - 4); j < i; j++) {
                Instruction prod = instructions.get(j);
                if (!prod.writesRegister()) continue;

                int dist = i - j; // 1 = immediately after, 2 = one gap, etc.
                int stalls = dataHazardStalls(prod, instr, dist, j, hazards);
                if (stalls > maxDelay) maxDelay = stalls;
            }

            // Control hazard from the immediately preceding instruction
            if (i > 0) {
                Instruction prev = instructions.get(i - 1);
                if (prev.type == Instruction.Type.I_BRANCH || prev.type == Instruction.Type.J) {
                    int ctrlStalls = 1;
                    if (ctrlStalls > maxDelay) maxDelay = ctrlStalls;
                    hazards.add(new Hazard(Hazard.Kind.CONTROL, i - 1, "", ctrlStalls));
                }
            }

            ifCycles[i] = idealStart + maxDelay;
            result.add(new Schedule(instr, ifCycles[i], maxDelay, hazards));
        }

        return result;
    }

    // ── Statistics helpers ──────────────────────────────────────────────────

    public static double computeCPI(List<Schedule> schedules) {
        if (schedules.isEmpty()) return 0.0;
        int n = schedules.size();
        int lastWbEnd = schedules.get(n - 1).wbEndCycle();
        return (double) lastWbEnd / n;
    }

    public static double idealCPI(int n) {
        if (n == 0) return 0.0;
        return (double)(n + 4) / n;   // pipeline fill + drain, no stalls
    }

    public static int totalStalls(List<Schedule> schedules) {
        int s = 0;
        for (Schedule sc : schedules) s += sc.stallsBefore;
        return s;
    }

    public static double speedup(List<Schedule> schedules) {
        if (schedules.isEmpty()) return 0.0;
        int n = schedules.size();
        double ideal  = idealCPI(n) * n;
        double actual = schedules.get(n - 1).wbEndCycle();
        return ideal / actual;
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    /**
     * Returns stall cycles needed because instruction prod (at index j, distance dist
     * before current instruction) writes a register that the current instruction reads.
     * Appends any discovered hazard to the hazards list.
     */
    private int dataHazardStalls(Instruction prod, Instruction cons,
                                  int dist, int prodIdx, List<Hazard> hazards) {
        // Collect source registers of consumer
        List<String> srcs = new ArrayList<String>();
        if (!cons.src1.isEmpty()) srcs.add(cons.src1);
        if (!cons.src2.isEmpty()) srcs.add(cons.src2);

        // For store, the data register (rt) is also a source
        if (cons.type == Instruction.Type.I_STORE && !cons.dest.isEmpty()) {
            srcs.add(cons.dest);
        }

        boolean conflict = srcs.contains(prod.dest);
        if (!conflict) return 0;

        if (forwarding) {
            // With forwarding:
            if (prod.type == Instruction.Type.I_LOAD && dist == 1) {
                // Load-use: 1 mandatory stall
                hazards.add(new Hazard(Hazard.Kind.LOAD_USE, prodIdx, prod.dest, 1));
                return 1;
            }
            // EX-EX and MEM-EX forwarding covers everything else
            hazards.add(new Hazard(Hazard.Kind.RAW, prodIdx, prod.dest, 0));
            return 0;
        } else {
            // Without forwarding: consumer needs value at its EX stage (dist-1 cycles after prod's WB)
            // Producer finishes WB at: prodIF + 4
            // Consumer EX starts at: consIF + 2  = (prodIF + dist) + stalls + 2
            // Need: prodIF + 4 <= consIF + 2  → stalls >= 2 - dist
            int stalls = Math.max(0, 2 - (dist - 1));
            if (stalls > 0) {
                hazards.add(new Hazard(Hazard.Kind.RAW, prodIdx, prod.dest, stalls));
            } else {
                hazards.add(new Hazard(Hazard.Kind.RAW, prodIdx, prod.dest, 0));
            }
            return stalls;
        }
    }
}
