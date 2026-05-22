package mars.mapping;

import mars.Globals;
import mars.ProgramStatement;
import mars.mapping.SourceMapper;
import mars.mips.hardware.RegisterFile;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;

/*
 * ExecutionTracker — singleton that tracks MIPS instruction execution and
 * fires events to registered listeners so visualization panels can update.
 *
 * It is called from Simulator.java's main instruction loop after each step.
 * Registration/deregistration of listeners is safe from any thread.
 *
 * Author: MARS C Extension
 */
public class ExecutionTracker {

    /** Listener interface for execution step events. */
    public interface ExecutionListener {
        /**
         * Called on the Swing EDT after each MIPS instruction completes.
         *
         * @param mipsAddress  PC address of the instruction just executed
         * @param cSourceLine  corresponding C source line (1-based), or -1 if unknown
         * @param statement    the ProgramStatement that was executed
         */
        void instructionExecuted(int mipsAddress, int cSourceLine, ProgramStatement statement);

        /**
         * Called when a new program is assembled and ready to run.
         */
        void programAssembled();

        /**
         * Called when the simulation is reset.
         */
        void simulationReset();
    }

    // ── Singleton ──────────────────────────────────────────────────────

    private static ExecutionTracker instance;

    private ExecutionTracker() {}

    public static synchronized ExecutionTracker getInstance() {
        if (instance == null) {
            instance = new ExecutionTracker();
        }
        return instance;
    }

    // ── State ──────────────────────────────────────────────────────────

    private SourceMapper sourceMapper;
    private final List<ExecutionListener> listeners = new ArrayList<ExecutionListener>();

    // Statistics counters
    private int totalInstructions = 0;
    private int arithmeticCount   = 0;
    private int memoryCount       = 0;
    private int branchCount       = 0;
    private int jumpCount         = 0;
    private long startTimeMs      = 0L;
    private long elapsedMs        = 0L;

    // Pipeline tracking (last 10 instructions for pipeline diagram)
    private final int MAX_PIPELINE_HISTORY = 20;
    private final List<int[]> recentInstructions = new ArrayList<int[]>(); // [address, cLine]

    // ── Registration ──────────────────────────────────────────────────

    public synchronized void addListener(ExecutionListener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public synchronized void removeListener(ExecutionListener l) {
        listeners.remove(l);
    }

    public void setSourceMapper(SourceMapper mapper) {
        this.sourceMapper = mapper;
    }

    public SourceMapper getSourceMapper() { return sourceMapper; }

    // ── Hooks called from Simulator ────────────────────────────────────

    /**
     * Called from Simulator.SimThread after each instruction is executed.
     * May be called from a background thread — fires listeners on EDT.
     *
     * @param mipsAddress  the PC of the just-executed instruction
     * @param stmt         the ProgramStatement (may be null for safety)
     */
    public void onInstructionExecuted(final int mipsAddress, final ProgramStatement stmt) {
        // Compute C line mapping
        final int cLine = (sourceMapper != null && sourceMapper.isMappingBuilt())
            ? sourceMapper.getCLineForAddress(mipsAddress) : -1;

        // Update stats (from background thread, but counters are only reset on EDT so safe)
        totalInstructions++;
        classifyInstruction(stmt);

        // Track pipeline history
        synchronized (recentInstructions) {
            recentInstructions.add(new int[]{mipsAddress, cLine});
            if (recentInstructions.size() > MAX_PIPELINE_HISTORY) {
                recentInstructions.remove(0);
            }
        }

        // Fire listeners on EDT
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                elapsedMs = System.currentTimeMillis() - startTimeMs;
                List<ExecutionListener> copy;
                synchronized (ExecutionTracker.this) {
                    copy = new ArrayList<ExecutionListener>(listeners);
                }
                for (ExecutionListener l : copy) {
                    l.instructionExecuted(mipsAddress, cLine, stmt);
                }
            }
        });
    }

    /**
     * Called from RunAssembleAction after successful assembly.
     * Resets stats and notifies listeners.
     */
    public void onAssemblyComplete() {
        resetStats();
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                List<ExecutionListener> copy;
                synchronized (ExecutionTracker.this) {
                    copy = new ArrayList<ExecutionListener>(listeners);
                }
                for (ExecutionListener l : copy) {
                    l.programAssembled();
                }
            }
        });
    }

    /**
     * Called when the simulation is reset.
     */
    public void onSimulationReset() {
        resetStats();
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                List<ExecutionListener> copy;
                synchronized (ExecutionTracker.this) {
                    copy = new ArrayList<ExecutionListener>(listeners);
                }
                for (ExecutionListener l : copy) {
                    l.simulationReset();
                }
            }
        });
    }

    // ── Statistics accessors ───────────────────────────────────────────

    public int  getTotalInstructions() { return totalInstructions; }
    public int  getArithmeticCount()   { return arithmeticCount; }
    public int  getMemoryCount()       { return memoryCount; }
    public int  getBranchCount()       { return branchCount; }
    public int  getJumpCount()         { return jumpCount; }
    public long getElapsedMs()         { return elapsedMs; }

    /** Estimated CPI — simplified as always 1.0 for single-cycle model. */
    public double getEstimatedCPI() {
        return 1.0; // upgraded to pipeline model in future phases
    }

    /** Returns a snapshot of recent instruction addresses for pipeline display. */
    public synchronized List<int[]> getRecentInstructions() {
        return new ArrayList<int[]>(recentInstructions);
    }

    // ── Private helpers ────────────────────────────────────────────────

    private void resetStats() {
        totalInstructions = 0;
        arithmeticCount   = 0;
        memoryCount       = 0;
        branchCount       = 0;
        jumpCount         = 0;
        startTimeMs       = System.currentTimeMillis();
        elapsedMs         = 0L;
        synchronized (recentInstructions) {
            recentInstructions.clear();
        }
    }

    private void classifyInstruction(ProgramStatement stmt) {
        if (stmt == null) return;
        String mnemonic = stmt.getInstruction() != null
            ? stmt.getInstruction().getName().toLowerCase()
            : "";

        if (mnemonic.startsWith("lw") || mnemonic.startsWith("sw") ||
            mnemonic.startsWith("lb") || mnemonic.startsWith("sb") ||
            mnemonic.startsWith("lh") || mnemonic.startsWith("sh") ||
            mnemonic.startsWith("ll") || mnemonic.startsWith("sc")) {
            memoryCount++;
        } else if (mnemonic.startsWith("beq") || mnemonic.startsWith("bne") ||
                   mnemonic.startsWith("blt") || mnemonic.startsWith("bgt") ||
                   mnemonic.startsWith("ble") || mnemonic.startsWith("bge") ||
                   mnemonic.startsWith("bltz") || mnemonic.startsWith("bgtz") ||
                   mnemonic.startsWith("bltzal") || mnemonic.startsWith("bgezal")) {
            branchCount++;
        } else if (mnemonic.startsWith("j") || mnemonic.startsWith("jr") ||
                   mnemonic.startsWith("jal") || mnemonic.startsWith("jalr")) {
            jumpCount++;
        } else if (!mnemonic.isEmpty() && !mnemonic.equals("syscall") &&
                   !mnemonic.equals("nop")  && !mnemonic.equals("break")) {
            arithmeticCount++;
        }
    }
}
