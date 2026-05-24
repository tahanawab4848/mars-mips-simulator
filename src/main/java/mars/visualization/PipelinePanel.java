package mars.visualization;

import mars.ProgramStatement;
import mars.mapping.ExecutionTracker;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.geom.*;

/**
 * PipelinePanel — replacement for the old grid-based pipeline display.
 * Renders an animated 5-stage block pipeline diagram (IF, ID, EX, MEM, WB)
 * showing live instructions progressing in real-time as the simulator steps.
 */
public class PipelinePanel extends JPanel
    implements ExecutionTracker.ExecutionListener {

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
    private int       cycleCount    = 0;

    // Animation phases
    private float shiftProgress = 1.0f;
    private int[]   stageDelay  = new int[NUM_STAGES];
    private float[] shakePhase  = new float[NUM_STAGES];
    private float[] bouncePhase = new float[NUM_STAGES];
    private float[] glowPhase   = new float[NUM_STAGES];
    private float[] tintPhase   = new float[NUM_STAGES]; 
    private float[] radialPhase = new float[NUM_STAGES];
    private float[] arrowPhase = new float[NUM_STAGES - 1];

    private javax.swing.Timer masterTimer;

    // Colors
    private static final Color BG         = new Color(0x1E1E2E);
    private static final Color COMP_BG    = new Color(0x252537);
    private static final Color STAGE_IDLE = new Color(0x1E1E2E);
    private static final Color[] STAGE_ACTIVE_COLORS = {
        new Color(0x89B4FA), // IF  - blue
        new Color(0xA6E3A1), // ID  - green
        new Color(0xFAB387), // EX  - orange
        new Color(0xF38BA8), // MEM - red
        new Color(0xCBA6F7)  // WB  - purple
    };
    private static final Color ARROW_COLOR  = new Color(0x45475A);
    private static final Color TEXT_DARK    = new Color(0x11111B);
    private static final Color TEXT_LIGHT   = new Color(0xCDD6F4);
    private static final Color ACCENT       = new Color(0x89B4FA);

    public PipelinePanel() {
        setBackground(BG);
        setDoubleBuffered(true);
        setBorder(new EmptyBorder(6, 6, 6, 6));

        initializeState();
        masterTimer = new javax.swing.Timer(16, e -> tick());
        ExecutionTracker.getInstance().addListener(this);
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

    // ── Execution Tracker Listeners ──────────────────────────────────────────

    @Override
    public void instructionExecuted(int addr, int cLine, ProgramStatement stmt) {
        String mn = (stmt != null && stmt.getInstruction() != null) ? stmt.getInstruction().getName() : "?";
        String pcStr = String.format("0x%04X", addr);

        // Shift stage state
        for (int i = NUM_STAGES - 1; i > 0; i--) {
            pipelineInstr[i] = pipelineInstr[i - 1];
            pipelinePC[i]    = pipelinePC[i - 1];
            stageActive[i]   = stageActive[i - 1];
        }
        pipelineInstr[0] = mn;
        pipelinePC[0]    = pcStr;
        stageActive[0]   = true;

        cycleCount++;

        // Trigger animations
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

    @Override
    public void programAssembled() {
        initializeState();
        cycleCount = 0;
        repaint();
    }

    @Override
    public void simulationReset() { programAssembled(); }

    // ── Paint Component ──────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int panelW = getWidth();
        int panelH = getHeight();

        // Title and cycle count
        g2.setColor(ACCENT);
        g2.setFont(new Font("SansSerif", Font.BOLD, 13));
        g2.drawString("5-Stage MIPS Pipeline Flow View", 16, 22);
        g2.setFont(new Font("Monospaced", Font.BOLD, 11));
        g2.setColor(TEXT_LIGHT);
        g2.drawString("Cycle: " + cycleCount, panelW - 100, 22);

        // Core layout
        int boxW = 85, boxH = 115, gap = 16, arrowW = 14;
        int totalW = NUM_STAGES * boxW + (NUM_STAGES - 1) * (gap + arrowW);
        int startX = (panelW - totalW) / 2;
        int startY = (panelH - boxH) / 2 + 10;
        int midY = startY + boxH / 2;

        int[] nomX = new int[NUM_STAGES];
        for (int i = 0; i < NUM_STAGES; i++) {
            nomX[i] = startX + i * (boxW + gap + arrowW);
        }

        // Draw connections
        for (int i = 1; i < NUM_STAGES; i++) {
            drawArrow(g2, nomX[i - 1] + boxW, midY, nomX[i], midY, i - 1);
        }

        // Draw stage boxes
        for (int i = 0; i < NUM_STAGES; i++) {
            drawStageBox(g2, nomX[i], startY, boxW, boxH, gap, arrowW, i);
        }

        g2.dispose();
    }

    private void drawStageBox(Graphics2D g2, int nomX, int y, int boxW, int boxH, int gap, int arrowW, int stage) {
        boolean active = stageActive[stage];
        Color stageColor = active ? STAGE_ACTIVE_COLORS[stage] : STAGE_IDLE;

        float sp = shakePhase[stage];
        int shakeX = (sp > 0f && sp < 1f) ? (int)(Math.sin(sp * Math.PI * 4) * (1.0 - sp) * 1.5) : 0;

        float bp = bouncePhase[stage];
        float scale = 1f + (bp > 0f && bp < 1f ? 0.04f * (float)Math.sin(bp * Math.PI) * (1f - bp) : 0f);

        float slideX = 0f;
        if (stage == 0 && shiftProgress < 1f) {
            slideX = -(1f - shiftProgress) * (boxW + gap + arrowW) * easeOut(1f - shiftProgress);
        }

        int bxNom = (int)(nomX + slideX) + shakeX;
        int cx = bxNom + boxW / 2, cy = y + boxH / 2;
        int bw = (int)(boxW * scale), bh = (int)(boxH * scale);
        int bx = cx - bw / 2, by = cy - bh / 2;

        // Glow ring
        float gp = glowPhase[stage];
        if (active && gp > 0f && gp < 1f) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (1f - gp) * 0.4f));
            g2.setColor(stageColor);
            g2.setStroke(new BasicStroke(2f));
            float ex = gp * 10f;
            g2.drawRoundRect((int)(bx - ex), (int)(by - ex), (int)(bw + ex * 2), (int)(bh + ex * 2), 12, 12);
            g2.setStroke(new BasicStroke(1f));
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }

        // Shadow
        g2.setColor(new Color(0, 0, 0, 70));
        g2.fillRoundRect(bx + 3, by + 3, bw, bh, 12, 12);

        // Body
        g2.setColor(active ? new Color(stageColor.getRed(), stageColor.getGreen(), stageColor.getBlue(), 30) : COMP_BG);
        g2.fillRoundRect(bx, by, bw, bh, 12, 12);

        // Border
        g2.setColor(active ? stageColor : new Color(0x3E3E5C));
        g2.setStroke(new BasicStroke(active ? 2f : 1f));
        g2.drawRoundRect(bx, by, bw, bh, 12, 12);
        g2.setStroke(new BasicStroke(1f));

        // Text
        g2.setFont(new Font("SansSerif", Font.BOLD, 14));
        g2.setColor(active ? stageColor : new Color(0x6C7086));
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(STAGE_NAMES[stage], bx + (bw - fm.stringWidth(STAGE_NAMES[stage])) / 2, by + 22);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 9));
        g2.setColor(active ? TEXT_LIGHT : new Color(0x4A4A6A));
        String[] descLines = STAGE_DESC[stage].split("\n");
        int textY = by + 38;
        for (String line : descLines) {
            fm = g2.getFontMetrics();
            g2.drawString(line, bx + (bw - fm.stringWidth(line)) / 2, textY);
            textY += 11;
        }

        if (active && pipelineInstr[stage] != null) {
            g2.setFont(new Font("Monospaced", Font.BOLD, 11));
            g2.setColor(Color.WHITE);
            fm = g2.getFontMetrics();
            String instrText = pipelineInstr[stage];
            g2.drawString(instrText, bx + (bw - fm.stringWidth(instrText)) / 2, by + 76);

            g2.setFont(new Font("Monospaced", Font.PLAIN, 8));
            g2.setColor(new Color(0x89B4FA));
            String pcText = pipelinePC[stage] != null ? pipelinePC[stage] : "";
            fm = g2.getFontMetrics();
            g2.drawString(pcText, bx + (bw - fm.stringWidth(pcText)) / 2, by + 90);
        } else if (!active) {
            g2.setFont(new Font("SansSerif", Font.ITALIC, 10));
            g2.setColor(new Color(0x3E3E5C));
            fm = g2.getFontMetrics();
            g2.drawString("(bubble)", bx + (bw - fm.stringWidth("(bubble)")) / 2, by + 76);
        }
    }

    private void drawArrow(Graphics2D g2, int x1, int y, int x2, int y2, int idx) {
        g2.setColor(ARROW_COLOR);
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(x1, y, x2 - 4, y2);
        int[] xp = {x2, x2 - 5, x2 - 5};
        int[] yp = {y2, y2 - 3, y2 + 3};
        g2.fillPolygon(xp, yp, 3);
        g2.setStroke(new BasicStroke(1f));

        float ap = arrowPhase[idx];
        if (ap > 0f && ap < 1f) {
            Color sc = STAGE_ACTIVE_COLORS[idx];
            int totalLen = x2 - x1;
            float ww = 0.35f;
            float front = ap;
            float back = Math.max(0f, front - ww);
            int wx1 = x1 + (int)(back  * totalLen);
            int wx2 = x1 + (int)(front * totalLen);
            float alpha = (float)(Math.sin(ap * Math.PI) * 0.7f);

            GradientPaint gp = new GradientPaint(
                wx1, y, new Color(sc.getRed(), sc.getGreen(), sc.getBlue(), 0),
                wx2, y, new Color(sc.getRed(), sc.getGreen(), sc.getBlue(), (int)(alpha * 180)));
            
            g2.setPaint(gp);
            g2.setStroke(new BasicStroke(3.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(wx1, y, Math.min(wx2, x2 - 4), y2);

            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2.setColor(Color.WHITE);
            g2.fillOval(wx2 - 2, y - 2, 4, 4);

            g2.setStroke(new BasicStroke(1f));
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }
    }

    private float easeOut(float t) { return 1f - (1f - t) * (1f - t); }
}
