package mars.insightx.ui;

import mars.insightx.Schedule;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.*;
import java.util.List;

/**
 * PipelineFlowPanel — presents a 5-stage MIPS pipeline block diagram (IF, ID, EX, MEM, WB)
 * with rich animations (shake, bounce, radial glows, active sweep waves).
 *
 * Supports two modes:
 *   1. Static Playback Mode: updates to match the instruction distribution of a selected clock cycle.
 *   2. Live Simulation Mode: steps instruction flow sequentially in real-time.
 */
public class PipelineFlowPanel extends JPanel {

    private static final int NUM_STAGES = 5;
    private static final String[] STAGE_NAMES = {"IF", "ID", "EX", "MEM", "WB"};
    private static final String[] STAGE_DESC = {
        "Instruction\nFetch", "Instruction\nDecode",
        "Execute\n(ALU)", "Memory\nAccess", "Write\nBack"
    };

    // State
    private String[]  pipelineInstr = new String[NUM_STAGES];
    private String[]  pipelinePC    = new String[NUM_STAGES];
    private boolean[] stageActive   = new boolean[NUM_STAGES];
    private int       lastPC        = -1;
    private int       cycleCount    = 0;

    // Animation phases (from PipelineVisualizer)
    private float shiftProgress = 1.0f;
    private int[]   stageDelay  = new int[NUM_STAGES];
    private float[] shakePhase  = new float[NUM_STAGES];
    private float[] bouncePhase = new float[NUM_STAGES];
    private float[] glowPhase   = new float[NUM_STAGES];
    private float[] tintPhase   = new float[NUM_STAGES]; 
    private float[] radialPhase = new float[NUM_STAGES];
    private float[] arrowPhase = new float[NUM_STAGES - 1];

    private javax.swing.Timer masterTimer;

    // Design System
    private static final Color BG         = new Color(0x0E0E1B);
    private static final Color PANEL_BG   = new Color(0x1B1B2F);
    private static final Color STAGE_IDLE = new Color(0x22223B);
    private static final Color[] STAGE_ACTIVE_COLORS = {
        new Color(0x4A90D9), // IF  – steel blue
        new Color(0x9B59B6), // ID  – purple
        new Color(0xE67E22), // EX  – orange
        new Color(0x1ABC9C), // MEM – teal
        new Color(0xE74C3C)  // WB  – coral
    };
    private static final Color ARROW_COLOR  = new Color(0x3E3E5C);
    private static final Color TEXT_DARK    = new Color(0x0E0E1B);
    private static final Color TEXT_LIGHT   = new Color(0xE2E8F0);
    private static final Color HEADER_COLOR = new Color(0x38BDF8);

    public PipelineFlowPanel() {
        setBackground(BG);
        setDoubleBuffered(true);
        setBorder(new EmptyBorder(10, 10, 10, 10));

        // Initialize state arrays
        initializeState();

        // 60 FPS animation timer
        masterTimer = new javax.swing.Timer(16, e -> tick());
    }

    private void initializeState() {
        pipelineInstr = new String[NUM_STAGES];
        pipelinePC = new String[NUM_STAGES];
        stageActive = new boolean[NUM_STAGES];
        stageDelay = new int[NUM_STAGES];
        shakePhase = new float[NUM_STAGES];
        bouncePhase = new float[NUM_STAGES];
        glowPhase = new float[NUM_STAGES];
        tintPhase = new float[NUM_STAGES];
        radialPhase = new float[NUM_STAGES];
        arrowPhase = new float[NUM_STAGES - 1];
    }

    // Lifecycle hooks
    @Override
    public void addNotify() {
        super.addNotify();
        if (masterTimer != null && !masterTimer.isRunning()) masterTimer.start();
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        if (masterTimer != null) masterTimer.stop();
    }

    /**
     * Updates the pipeline state dynamically for a chosen clock cycle.
     */
    public void setCycleState(int cycle, List<Schedule> schedules) {
        // Reset stages
        for (int i = 0; i < NUM_STAGES; i++) {
            pipelineInstr[i] = null;
            pipelinePC[i] = null;
            stageActive[i] = false;
        }

        if (schedules == null || schedules.isEmpty()) {
            repaint();
            return;
        }

        cycleCount = cycle;

        // Query which instructions were active in this cycle
        for (int stage = 0; stage < NUM_STAGES; stage++) {
            for (int idx = 0; idx < schedules.size(); idx++) {
                Schedule s = schedules.get(idx);
                if (s.stageCycle[stage] == cycle) {
                    pipelineInstr[stage] = s.instruction.mnemonic;
                    pipelinePC[stage] = String.format("0x%04X", 0x00400000 + idx * 4);
                    stageActive[stage] = true;

                    // Trigger micro-animations on update
                    if (glowPhase[stage] == 0f || glowPhase[stage] == 1f) glowPhase[stage] = 0.001f;
                    if (bouncePhase[stage] == 0f || bouncePhase[stage] == 1f) bouncePhase[stage] = 0.001f;
                    break;
                }
            }
        }

        // Active sweeps for arrows
        for (int a = 0; a < NUM_STAGES - 1; a++) {
            if (stageActive[a] || stageActive[a + 1]) {
                if (arrowPhase[a] == 0f || arrowPhase[a] == 1f) arrowPhase[a] = 0.001f;
            }
        }

        if (masterTimer != null && !masterTimer.isRunning()) {
            masterTimer.start();
        }
        repaint();
    }

    /**
     * Steps the simulation forward by 1 instruction (Live mode).
     */
    public void step(String instr, String pc) {
        // Shift state
        for (int i = NUM_STAGES - 1; i > 0; i--) {
            pipelineInstr[i] = pipelineInstr[i - 1];
            pipelinePC[i]    = pipelinePC[i - 1];
            stageActive[i]   = stageActive[i - 1];
        }
        pipelineInstr[0] = instr;
        pipelinePC[0]    = pc;
        stageActive[0]   = true;

        cycleCount++;

        // Staggered cascade animations
        for (int i = 0; i < NUM_STAGES; i++) {
            if (stageActive[i]) {
                if (i == 0) {
                    fireStage(0);
                    stageDelay[0] = 0;
                } else {
                    stageDelay[i] = i * 4;
                }
            }
        }

        for (int a = 0; a < NUM_STAGES - 1; a++) {
            if (stageActive[a] || stageActive[a + 1]) {
                arrowPhase[a] = 0.001f;
            }
        }

        shiftProgress = 0f;
        if (masterTimer != null && !masterTimer.isRunning()) {
            masterTimer.start();
        }
        repaint();
    }

    public void reset() {
        initializeState();
        cycleCount = 0;
        lastPC = -1;
        repaint();
    }

    private void tick() {
        boolean any = false;

        if (shiftProgress < 1f) {
            shiftProgress = Math.min(1f, shiftProgress + 0.07f);
            any = true;
        }

        for (int i = 0; i < NUM_STAGES; i++) {
            if (stageDelay[i] > 0) {
                stageDelay[i]--;
                if (stageDelay[i] == 0 && stageActive[i]) {
                    fireStage(i);
                }
                any = true;
            }
            if (adv(shakePhase,  i, 0.10f)) any = true;
            if (adv(bouncePhase, i, 0.07f)) any = true;
            if (adv(glowPhase,   i, 0.04f)) any = true;
            if (adv(tintPhase,   i, 0.05f)) any = true;
            if (adv(radialPhase, i, 0.038f)) any = true;
        }

        for (int a = 0; a < NUM_STAGES - 1; a++) {
            if (adv(arrowPhase, a, 0.028f)) any = true;
        }

        repaint();
        if (!any && masterTimer != null) {
            masterTimer.stop();
        }
    }

    private boolean adv(float[] arr, int i, float step) {
        if (arr[i] > 0f && arr[i] < 1f) {
            arr[i] = Math.min(1f, arr[i] + step);
            return true;
        }
        return false;
    }

    private void fireStage(int i) {
        shakePhase[i]  = 0.001f;
        bouncePhase[i] = 0.001f;
        glowPhase[i]   = 0.001f;
        tintPhase[i]   = 0.001f;
        radialPhase[i] = 0.001f;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int panelW = getWidth();
        int panelH = getHeight();

        // Title and cycle indicator
        g2.setColor(TEXT_LIGHT);
        g2.setFont(new Font("SansSerif", Font.BOLD, 13));
        g2.drawString("5-Stage MIPS Pipeline Stage View", 20, 25);
        g2.setFont(new Font("Monospaced", Font.BOLD, 12));
        g2.setColor(HEADER_COLOR);
        g2.drawString("Current Cycle: " + cycleCount, panelW - 160, 25);

        // Core boxes layout
        int boxW = 100, boxH = 130, gap = 20, arrowW = 16;
        int totalW = NUM_STAGES * boxW + (NUM_STAGES - 1) * (gap + arrowW);
        int startX = (panelW - totalW) / 2;
        int startY = (panelH - boxH) / 2 + 10;
        int midY = startY + boxH / 2;

        int[] nomX = new int[NUM_STAGES];
        for (int i = 0; i < NUM_STAGES; i++) {
            nomX[i] = startX + i * (boxW + gap + arrowW);
        }

        // Draw connections (arrows)
        for (int i = 1; i < NUM_STAGES; i++) {
            drawArrow(g2, nomX[i - 1] + boxW, midY, nomX[i], midY, i - 1);
        }

        // Draw boxes
        for (int i = 0; i < NUM_STAGES; i++) {
            drawStageBox(g2, nomX[i], startY, boxW, boxH, gap, arrowW, i);
        }

        g2.dispose();
    }

    private void drawStageBox(Graphics2D g2, int nomX, int y, int boxW, int boxH, int gap, int arrowW, int stage) {
        boolean active = stageActive[stage];
        Color stageColor = active ? STAGE_ACTIVE_COLORS[stage] : STAGE_IDLE;

        // Shake animation calculation
        float sp = shakePhase[stage];
        int shakeX = (sp > 0f && sp < 1f) ? (int)(Math.sin(sp * Math.PI * 4) * (1.0 - sp) * 1.5) : 0;

        // Bounce animation calculation
        float bp = bouncePhase[stage];
        float scale = 1f + (bp > 0f && bp < 1f ? 0.04f * (float)Math.sin(bp * Math.PI) * (1f - bp) : 0f);

        // Slide-in for IF stage
        float slideX = 0f;
        if (stage == 0 && shiftProgress < 1f) {
            slideX = -(1f - shiftProgress) * (boxW + gap + arrowW) * easeOut(1f - shiftProgress);
        }

        int bxNom = (int)(nomX + slideX) + shakeX;
        int cx = bxNom + boxW / 2, cy = y + boxH / 2;
        int bw = (int)(boxW * scale), bh = (int)(boxH * scale);
        int bx = cx - bw / 2, by = cy - bh / 2;

        // Glow ring glow
        float gp = glowPhase[stage];
        if (active && gp > 0f && gp < 1f) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (1f - gp) * 0.45f));
            g2.setColor(stageColor);
            g2.setStroke(new BasicStroke(2.5f));
            float ex = gp * 12f;
            g2.drawRoundRect((int)(bx - ex), (int)(by - ex), (int)(bw + ex * 2), (int)(bh + ex * 2), 16, 16);
            g2.setStroke(new BasicStroke(1f));
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }

        // Draw Shadow
        g2.setColor(new Color(0, 0, 0, 100));
        g2.fillRoundRect(bx + 3, by + 3, bw, bh, 14, 14);

        // Fill body
        g2.setColor(active ? new Color(stageColor.getRed(), stageColor.getGreen(), stageColor.getBlue(), 35) : STAGE_IDLE);
        g2.fillRoundRect(bx, by, bw, bh, 14, 14);

        // Draw Border
        g2.setColor(active ? stageColor : new Color(0x3E3E5C));
        g2.setStroke(new BasicStroke(active ? 2.5f : 1.2f));
        g2.drawRoundRect(bx, by, bw, bh, 14, 14);
        g2.setStroke(new BasicStroke(1f));

        // Tint flash overlay
        float tp = tintPhase[stage];
        if (active && tp > 0f && tp < 1f) {
            float ta = Math.max(0f, Math.min(0.3f, (float)Math.sin(tp * Math.PI) * 0.3f));
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ta));
            g2.setColor(stageColor.brighter());
            g2.fillRoundRect(bx, by, bw, bh, 14, 14);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }

        // Radial ring explosion
        float rp = radialPhase[stage];
        if (active && rp > 0f && rp < 1f) {
            Shape oldClip = g2.getClip();
            g2.setClip(new RoundRectangle2D.Float(bx, by, bw, bh, 14, 14));
            float maxR = (float)Math.sqrt(bw * bw + bh * bh) / 2f;
            float r1 = rp * maxR;

            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (1f - rp) * 0.5f));
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2f));
            g2.drawOval(cx - (int)r1, cy - (int)r1, (int)(r1 * 2), (int)(r1 * 2));
            g2.setStroke(new BasicStroke(1f));
            g2.setClip(oldClip);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }

        // Render Stage Header Name
        g2.setFont(new Font("SansSerif", Font.BOLD, 15));
        g2.setColor(active ? stageColor : new Color(0x6C7086));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(STAGE_NAMES[stage], bx + (bw - fm.stringWidth(STAGE_NAMES[stage])) / 2, by + 26);

        // Render Stage Sub-description
        g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g2.setColor(active ? TEXT_LIGHT : new Color(0x4A4A6A));
        String[] descLines = STAGE_DESC[stage].split("\n");
        int textY = by + 46;
        for (String line : descLines) {
            fm = g2.getFontMetrics();
            g2.drawString(line, bx + (bw - fm.stringWidth(line)) / 2, textY);
            textY += 12;
        }

        // Draw active mnemonic / instruction PC
        if (active && pipelineInstr[stage] != null) {
            g2.setFont(new Font("Monospaced", Font.BOLD, 13));
            g2.setColor(Color.WHITE);
            fm = g2.getFontMetrics();
            String instrText = pipelineInstr[stage];
            g2.drawString(instrText, bx + (bw - fm.stringWidth(instrText)) / 2, by + 90);

            g2.setFont(new Font("Monospaced", Font.PLAIN, 9));
            g2.setColor(new Color(0x89B4FA));
            String pcText = pipelinePC[stage] != null ? pipelinePC[stage] : "";
            fm = g2.getFontMetrics();
            g2.drawString(pcText, bx + (bw - fm.stringWidth(pcText)) / 2, by + 104);
        } else if (!active) {
            g2.setFont(new Font("SansSerif", Font.ITALIC, 11));
            g2.setColor(new Color(0x3E3E5C));
            fm = g2.getFontMetrics();
            g2.drawString("(bubble)", bx + (bw - fm.stringWidth("(bubble)")) / 2, by + 90);
        }
    }

    private void drawArrow(Graphics2D g2, int x1, int y, int x2, int y2, int idx) {
        // Base arrow connection line
        g2.setColor(ARROW_COLOR);
        g2.setStroke(new BasicStroke(2f));
        g2.drawLine(x1, y, x2 - 5, y2);
        int[] xp = {x2, x2 - 7, x2 - 7};
        int[] yp = {y2, y2 - 4, y2 + 4};
        g2.fillPolygon(xp, yp, 3);
        g2.setStroke(new BasicStroke(1f));

        // Sweep wave animation
        float ap = arrowPhase[idx];
        if (ap > 0f && ap < 1f) {
            Color sc = STAGE_ACTIVE_COLORS[idx];
            int totalLen = x2 - x1;
            float ww = 0.35f;
            float front = ap;
            float back = Math.max(0f, front - ww);
            int wx1 = x1 + (int)(back  * totalLen);
            int wx2 = x1 + (int)(front * totalLen);
            float alpha = (float)(Math.sin(ap * Math.PI) * 0.8f);

            GradientPaint gp = new GradientPaint(
                wx1, y, new Color(sc.getRed(), sc.getGreen(), sc.getBlue(), 0),
                wx2, y, new Color(sc.getRed(), sc.getGreen(), sc.getBlue(), (int)(alpha * 200)));
            
            g2.setPaint(gp);
            g2.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(wx1, y, Math.min(wx2, x2 - 5), y2);

            // Leading dot
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2.setColor(Color.WHITE);
            g2.fillOval(wx2 - 3, y - 3, 6, 6);

            g2.setStroke(new BasicStroke(1f));
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }
    }

    private float easeOut(float t) {
        return 1f - (1f - t) * (1f - t);
    }
}
