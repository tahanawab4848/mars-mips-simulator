package mars.visualization;

import mars.mapping.ExecutionTracker;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

/*
 * ExecutionAnimator — manages Swing timers for smooth animations in visualization panels.
 *
 * Each animation target registers itself; when a step occurs, the animator runs a
 * fade-in highlight effect over several frames and then fades it out.
 *
 * Author: MARS C Extension
 */
public class ExecutionAnimator {

    /** Interface for panels that can be animated by this manager. */
    public interface AnimationTarget {
        /** Called each animation frame. progress ∈ [0.0, 1.0] (0=start, 1=end of fade). */
        void animationFrame(float progress);
    }

    // ── Constants ──────────────────────────────────────────────────────

    private static final int FRAME_INTERVAL_MS = 33;  // ~30 fps
    private static final int TOTAL_FRAMES      = 18;  // ~600 ms total fade

    // ── Instruction type classification ───────────────────────────────

    public enum InstructionType {
        ARITHMETIC,
        LOAD,
        STORE,
        BRANCH,
        JUMP,
        SYSCALL,
        OTHER
    }

    // ── State ─────────────────────────────────────────────────────────

    private final List<AnimationTarget> targets = new ArrayList<AnimationTarget>();
    private Timer animTimer;
    private int currentFrame = 0;
    private InstructionType currentType = InstructionType.OTHER;
    private boolean paused = false;

    // ── Singleton ──────────────────────────────────────────────────────

    private static ExecutionAnimator instance;

    private ExecutionAnimator() {}

    public static synchronized ExecutionAnimator getInstance() {
        if (instance == null) {
            instance = new ExecutionAnimator();
        }
        return instance;
    }

    // ── Registration ──────────────────────────────────────────────────

    public void addTarget(AnimationTarget target) {
        if (!targets.contains(target)) targets.add(target);
    }

    public void removeTarget(AnimationTarget target) {
        targets.remove(target);
    }

    // ── Control ────────────────────────────────────────────────────────

    /**
     * Trigger a new animation sequence for the given instruction type.
     * Restarts animation from frame 0.
     */
    public void startAnimation(InstructionType type) {
        if (paused) return;
        this.currentType  = type;
        this.currentFrame = 0;

        if (animTimer != null && animTimer.isRunning()) {
            animTimer.stop();
        }

        animTimer = new Timer(FRAME_INTERVAL_MS, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (currentFrame >= TOTAL_FRAMES) {
                    animTimer.stop();
                    // Fire final frame at 1.0 to reset highlight
                    fireFrame(1.0f);
                    return;
                }
                float progress = (float) currentFrame / (TOTAL_FRAMES - 1);
                fireFrame(progress);
                currentFrame++;
            }
        });
        animTimer.setInitialDelay(0);
        animTimer.start();
    }

    public void pauseAnimation() {
        paused = true;
        if (animTimer != null) animTimer.stop();
    }

    public void resumeAnimation() {
        paused = false;
    }

    public void resetAnimation() {
        paused = false;
        currentFrame = 0;
        if (animTimer != null) animTimer.stop();
        fireFrame(1.0f); // clear all highlights
    }

    public InstructionType getCurrentType() { return currentType; }
    public boolean isPaused()              { return paused; }

    // ── Helpers ────────────────────────────────────────────────────────

    private void fireFrame(float progress) {
        for (AnimationTarget t : targets) {
            t.animationFrame(progress);
        }
    }

    // ── Color utilities ────────────────────────────────────────────────

    /**
     * Returns the highlight color for the given instruction type.
     * Used by visualization panels to pick the right color.
     */
    public static Color getTypeColor(InstructionType type) {
        switch (type) {
            case ARITHMETIC: return new Color(0x2ECC71); // green
            case LOAD:       return new Color(0x3498DB); // blue
            case STORE:      return new Color(0xE67E22); // orange
            case BRANCH:     return new Color(0x9B59B6); // purple
            case JUMP:       return new Color(0xE74C3C); // red
            case SYSCALL:    return new Color(0xF39C12); // amber
            default:         return new Color(0x95A5A6); // grey
        }
    }

    /**
     * Blends the given color with white based on progress (0=fully colored, 1=white/transparent).
     */
    public static Color fadeColor(Color base, float progress) {
        // Use a triangle: fade IN from 0→0.3, hold 0.3→0.7, fade OUT 0.7→1.0
        float intensity;
        if (progress < 0.3f) {
            intensity = progress / 0.3f;
        } else if (progress < 0.7f) {
            intensity = 1.0f;
        } else {
            intensity = 1.0f - (progress - 0.7f) / 0.3f;
        }
        intensity = Math.max(0f, Math.min(1f, intensity));

        // Blend with the dark UI background (0x1E1E2E) instead of white, to keep text readable.
        Color bg = new Color(0x1E, 0x1E, 0x2E);
        int r = (int) (base.getRed()   * intensity + bg.getRed()   * (1f - intensity));
        int g = (int) (base.getGreen() * intensity + bg.getGreen() * (1f - intensity));
        int b = (int) (base.getBlue()  * intensity + bg.getBlue()  * (1f - intensity));
        return new Color(
            Math.max(0, Math.min(255, r)),
            Math.max(0, Math.min(255, g)),
            Math.max(0, Math.min(255, b))
        );
    }

    /**
     * Classify a MIPS instruction mnemonic into an InstructionType.
     */
    public static InstructionType classify(String mnemonic) {
        if (mnemonic == null) return InstructionType.OTHER;
        String m = mnemonic.toLowerCase().trim();
        if (m.startsWith("lw") || m.startsWith("lb") || m.startsWith("lh") ||
            m.startsWith("ll") || m.startsWith("lwl") || m.startsWith("lwr")) {
            return InstructionType.LOAD;
        }
        if (m.startsWith("sw") || m.startsWith("sb") || m.startsWith("sh") ||
            m.startsWith("sc") || m.startsWith("swl") || m.startsWith("swr")) {
            return InstructionType.STORE;
        }
        if (m.startsWith("beq") || m.startsWith("bne") || m.startsWith("blt") ||
            m.startsWith("bgt") || m.startsWith("ble") || m.startsWith("bge") ||
            m.startsWith("bltz") || m.startsWith("bgtz") || m.startsWith("bgez") ||
            m.startsWith("bltzal") || m.startsWith("bgezal")) {
            return InstructionType.BRANCH;
        }
        if (m.equals("j") || m.equals("jr") || m.equals("jal") || m.equals("jalr")) {
            return InstructionType.JUMP;
        }
        if (m.equals("syscall")) {
            return InstructionType.SYSCALL;
        }
        if (!m.isEmpty() && !m.equals("nop") && !m.equals("break")) {
            return InstructionType.ARITHMETIC;
        }
        return InstructionType.OTHER;
    }
}
