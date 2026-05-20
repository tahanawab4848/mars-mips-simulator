package mars.insightx.ui;

import mars.insightx.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.List;

/**
 * GanttPanel — a scrollable Gantt-style pipeline diagram.
 *
 * Rows    = instructions (Y-axis)
 * Columns = cycles      (X-axis)
 *
 * Each stage cell is color-coded:
 *   IF  = steel blue
 *   ID  = medium purple
 *   EX  = orange
 *   MEM = teal
 *   WB  = coral / rose
 *   Stall bubble = dark gray
 */
public class GanttPanel extends JPanel {

    // ── Visual constants ─────────────────────────────────────────────────────
    private static final int ROW_H      = 36;
    private static final int COL_W      = 44;
    private static final int LABEL_W    = 230;
    private static final int HEADER_H   = 28;
    private static final Font CELL_FONT = new Font("Monospaced", Font.BOLD, 11);
    private static final Font LABEL_FONT= new Font("SansSerif",  Font.PLAIN, 12);
    private static final Font HDR_FONT  = new Font("SansSerif",  Font.BOLD,  12);

    private static final Color[] STAGE_COLORS = {
        new Color(0x4A90D9),   // IF  – steel blue
        new Color(0x9B59B6),   // ID  – purple
        new Color(0xE67E22),   // EX  – orange
        new Color(0x1ABC9C),   // MEM – teal
        new Color(0xE74C3C),   // WB  – coral
    };
    private static final Color STALL_COLOR  = new Color(0x555555);
    private static final Color BG_COLOR     = new Color(0x1E1E2E);
    private static final Color GRID_COLOR   = new Color(0x2E2E40);
    private static final Color HDR_COLOR    = new Color(0x16213E);
    private static final Color TEXT_COLOR   = new Color(0xECF0F1);
    private static final Color HAZARD_MARK  = new Color(0xFF4444);
    private static final Color HOVER_COLOR  = new Color(0xFFFFFF, true);

    // ── State ────────────────────────────────────────────────────────────────
    private List<Schedule> schedules;
    private int            totalCycles;
    private int            hoverRow = -1;
    private int            selectedCycle = 1;

    // Cycle selection callback
    public interface CycleSelectionListener {
        void cycleSelected(int cycle);
    }
    private CycleSelectionListener cycleListener;

    public void setCycleSelectionListener(CycleSelectionListener listener) {
        this.cycleListener = listener;
    }

    // Tooltip support
    private String tooltipText = "";

    public GanttPanel() {
        setBackground(BG_COLOR);
        setOpaque(true);
        MouseAdapter ma = new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent e) { onMouseMove(e); }
            @Override public void mouseExited(MouseEvent e) { hoverRow = -1; repaint(); }
            @Override public void mouseClicked(MouseEvent e) { onMouseClick(e); }
        };
        addMouseMotionListener(ma);
        addMouseListener(ma);
        setToolTipText(""); // enable tooltip
    }

    public void setSchedules(List<Schedule> sched) {
        this.schedules   = sched;
        this.totalCycles = sched.isEmpty() ? 0 :
            sched.get(sched.size() - 1).wbEndCycle();
        this.selectedCycle = 1; // reset
        recomputeSize();
        repaint();
    }

    private void onMouseClick(MouseEvent e) {
        if (schedules == null || schedules.isEmpty()) return;
        int x = e.getX();
        if (x >= LABEL_W) {
            int cycle = (x - LABEL_W) / COL_W + 1;
            if (cycle >= 1 && cycle <= totalCycles) {
                selectedCycle = cycle;
                repaint();
                if (cycleListener != null) {
                    cycleListener.cycleSelected(selectedCycle);
                }
            }
        }
    }

    public int getSelectedCycle() { return selectedCycle; }

    @Override
    public String getToolTipText(MouseEvent e) { return tooltipText; }

    // ── Layout ───────────────────────────────────────────────────────────────

    private void recomputeSize() {
        if (schedules == null) return;
        int rows = schedules.size();
        int cols = totalCycles + 2;
        int w = LABEL_W + cols * COL_W + 20;
        int h = HEADER_H + rows * ROW_H + 20;
        Dimension d = new Dimension(w, h);
        setPreferredSize(d);
        revalidate();
    }

    // ── Painting ─────────────────────────────────────────────────────────────

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (schedules == null || schedules.isEmpty()) {
            g.setColor(TEXT_COLOR);
            g.setFont(new Font("SansSerif", Font.ITALIC, 14));
            g.drawString("Load a .asm file to see the pipeline diagram.", 30, 60);
            return;
        }
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        drawHeader(g2);
        for (int r = 0; r < schedules.size(); r++) {
            drawRow(g2, r, schedules.get(r));
        }

        // Draw selected cycle visual track highlight
        if (selectedCycle >= 1 && selectedCycle <= totalCycles) {
            int sx = LABEL_W + (selectedCycle - 1) * COL_W;
            g2.setColor(new Color(0x38, 0xBD, 0xF8, 40)); // Translucent cyan fill
            g2.fillRect(sx, 0, COL_W, getHeight());
            g2.setColor(new Color(0x38, 0xBD, 0xF8, 150)); // Clear highlighted borders
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRect(sx, 0, COL_W, getHeight() - 1);
            g2.setStroke(new BasicStroke(1f));
        }
    }

    private void drawHeader(Graphics2D g2) {
        g2.setColor(HDR_COLOR);
        g2.fillRect(0, 0, getWidth(), HEADER_H);
        g2.setFont(HDR_FONT);
        g2.setColor(TEXT_COLOR);
        g2.drawString("Instruction", 8, HEADER_H - 8);

        for (int c = 1; c <= totalCycles + 1; c++) {
            int x = LABEL_W + (c - 1) * COL_W;
            g2.setColor(GRID_COLOR);
            g2.drawLine(x, 0, x, getHeight());
            g2.setColor(TEXT_COLOR);
            g2.drawString(String.valueOf(c), x + COL_W / 2 - 5, HEADER_H - 8);
        }
    }

    private void drawRow(Graphics2D g2, int row, Schedule sc) {
        int y = HEADER_H + row * ROW_H;
        boolean hovered = (row == hoverRow);

        // Row background
        g2.setColor(hovered ? new Color(0x2A2A3E) : (row % 2 == 0 ? BG_COLOR : new Color(0x25253A)));
        g2.fillRect(0, y, getWidth(), ROW_H);

        // Instruction label
        g2.setFont(LABEL_FONT);
        g2.setColor(TEXT_COLOR);
        String label = truncate(sc.instruction.display, 28);
        g2.drawString(label, 8, y + ROW_H / 2 + 5);

        // Hazard indicator dot on label
        if (!sc.hazards.isEmpty()) {
            g2.setColor(HAZARD_MARK);
            g2.fillOval(LABEL_W - 16, y + ROW_H / 2 - 5, 10, 10);
        }

        // Stall bubbles (gray boxes in cycles before IF)
        int ifCycle = sc.stageCycle[Schedule.IF];
        int idealIF = ifCycle - sc.stallsBefore;
        for (int stall = 0; stall < sc.stallsBefore; stall++) {
            int cx = idealIF + stall;
            drawCell(g2, cx, y, "stall", STALL_COLOR);
        }

        // Stage cells
        for (int s = 0; s < 5; s++) {
            int cx = sc.stageCycle[s];
            drawCell(g2, cx, y, Schedule.STAGE_NAMES[s], STAGE_COLORS[s]);
        }

        // Horizontal divider
        g2.setColor(GRID_COLOR);
        g2.drawLine(0, y + ROW_H - 1, getWidth(), y + ROW_H - 1);
    }

    private void drawCell(Graphics2D g2, int cycle, int rowY, String label, Color color) {
        int x = LABEL_W + (cycle - 1) * COL_W + 2;
        int y = rowY + 3;
        int w = COL_W - 4;
        int h = ROW_H - 6;

        // Rounded fill
        g2.setColor(color);
        g2.fillRoundRect(x, y, w, h, 6, 6);

        // Slight gradient gloss
        g2.setPaint(new GradientPaint(x, y, new Color(255,255,255,40), x, y+h, new Color(0,0,0,0)));
        g2.fillRoundRect(x, y, w, h/2, 6, 6);

        // Text
        g2.setFont(CELL_FONT);
        g2.setColor(Color.WHITE);
        FontMetrics fm = g2.getFontMetrics();
        int tx = x + (w - fm.stringWidth(label)) / 2;
        int ty = y + (h + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(label, tx, ty);
    }

    // ── Interaction ──────────────────────────────────────────────────────────

    private void onMouseMove(MouseEvent e) {
        int y = e.getY() - HEADER_H;
        hoverRow = (y >= 0 && schedules != null) ? Math.min(y / ROW_H, schedules.size() - 1) : -1;
        if (hoverRow >= 0 && hoverRow < schedules.size()) {
            Schedule sc = schedules.get(hoverRow);
            StringBuilder sb = new StringBuilder("<html><b>");
            sb.append(sc.instruction.display).append("</b><br>");
            sb.append("IF=").append(sc.stageCycle[0])
              .append("  ID=").append(sc.stageCycle[1])
              .append("  EX=").append(sc.stageCycle[2])
              .append("  MEM=").append(sc.stageCycle[3])
              .append("  WB=").append(sc.stageCycle[4]);
            if (sc.stallsBefore > 0)
                sb.append("<br>Stalls: ").append(sc.stallsBefore);
            for (Hazard h : sc.hazards)
                sb.append("<br><font color='#FF6666'>").append(h).append("</font>");
            sb.append("</html>");
            tooltipText = sb.toString();
        }
        repaint();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        s = s.trim();
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
