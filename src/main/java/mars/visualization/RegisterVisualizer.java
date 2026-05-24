package mars.visualization;

import mars.ProgramStatement;
import mars.mapping.ExecutionTracker;
import mars.mips.hardware.Register;
import mars.mips.hardware.RegisterFile;
import mars.visualization.ExecutionAnimator.AnimationTarget;
import mars.visualization.ExecutionAnimator.InstructionType;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Map;

/*
 * RegisterVisualizer — displays all 32 MIPS general-purpose registers plus
 * PC, HI, and LO with real-time update and animated highlight on modification.
 *
 * Author: MARS C Extension
 */
public class RegisterVisualizer extends JPanel
    implements ExecutionTracker.ExecutionListener, AnimationTarget {

    // ── Color palette ──────────────────────────────────────────────────
    private static final Color BG_DARK       = new Color(0x1E1E2E);
    private static final Color ROW_ODD       = new Color(0x252537);
    private static final Color ROW_EVEN      = new Color(0x1E1E2E);
    private static final Color HEADER_BG     = new Color(0x313147);
    private static final Color TEXT_COLOR    = new Color(0xCDD6F4);
    private static final Color LABEL_COLOR   = new Color(0x89B4FA);
    private static final Color MODIFIED_BASE = new Color(0x2ECC71);
    private static final Color BORDER_COLOR  = new Color(0x45475A);

    // ── Register names (32 GP + PC + HI + LO) ─────────────────────────
    private static final String[] REG_NAMES = {
        "$zero","$at","$v0","$v1","$a0","$a1","$a2","$a3",
        "$t0","$t1","$t2","$t3","$t4","$t5","$t6","$t7",
        "$s0","$s1","$s2","$s3","$s4","$s5","$s6","$s7",
        "$t8","$t9","$k0","$k1","$gp","$sp","$fp","$ra",
        "PC","HI","LO"
    };
    private static final int TOTAL_REGS = REG_NAMES.length;

    // ── State ─────────────────────────────────────────────────────────
    private final int[]   values      = new int[TOTAL_REGS];
    private final int[]   prevValues  = new int[TOTAL_REGS];
    private final float[] highlights  = new float[TOTAL_REGS]; // 0=none, 1=peak
    private final Color[] rowColors   = new Color[TOTAL_REGS];

    private boolean showHex = true;
    private float   currentAnimProgress = 1.0f;
    private int     lastModifiedReg = -1;

    // ── GUI components ─────────────────────────────────────────────────
    private JTable         table;
    private RegisterTableModel model;

    // ── Constructor ────────────────────────────────────────────────────
    public RegisterVisualizer() {
        setLayout(new BorderLayout(0, 0));
        setBackground(BG_DARK);
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));

        buildUI();

        ExecutionTracker.getInstance().addListener(this);
        ExecutionAnimator.getInstance().addTarget(this);

        refreshAll();
    }

    // ── UI Construction ────────────────────────────────────────────────
    private void buildUI() {
        // Header
        JLabel title = new JLabel("  MIPS Registers", JLabel.LEFT);
        title.setFont(new Font("SansSerif", Font.BOLD, 13));
        title.setForeground(LABEL_COLOR);
        title.setBackground(HEADER_BG);
        title.setOpaque(true);
        title.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // Hex/Dec toggle
        final JToggleButton hexBtn = new JToggleButton("HEX", true);
        hexBtn.setFont(new Font("Monospaced", Font.BOLD, 11));
        hexBtn.setForeground(TEXT_COLOR);
        hexBtn.setBackground(HEADER_BG);
        hexBtn.setFocusPainted(false);
        hexBtn.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                showHex = hexBtn.isSelected();
                hexBtn.setText(showHex ? "HEX" : "DEC");
                model.fireTableDataChanged();
            }
        });

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(HEADER_BG);
        headerPanel.add(title, BorderLayout.CENTER);
        headerPanel.add(hexBtn, BorderLayout.EAST);
        headerPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));

        // Table
        model = new RegisterTableModel();
        table = new JTable(model) {
            @Override
            public Component prepareRenderer(TableCellRenderer renderer, int row, int col) {
                Component c = super.prepareRenderer(renderer, row, col);
                Color baseRow = (row % 2 == 0) ? ROW_EVEN : ROW_ODD;
                Color highlight = rowColors[row];
                c.setBackground(highlight != null ? highlight : baseRow);
                c.setForeground(col == 0 ? LABEL_COLOR : TEXT_COLOR);
                if (col == 0) {
                    c.setFont(new Font("Monospaced", Font.BOLD, 12));
                } else {
                    c.setFont(new Font("Monospaced", Font.PLAIN, 12));
                }
                return c;
            }
        };
        table.setBackground(BG_DARK);
        table.setForeground(TEXT_COLOR);
        table.setGridColor(BORDER_COLOR);
        table.setRowHeight(20);
        table.setIntercellSpacing(new Dimension(1, 1));
        table.setShowGrid(true);
        table.setSelectionBackground(new Color(0x45475A));
        table.getTableHeader().setBackground(HEADER_BG);
        table.getTableHeader().setForeground(LABEL_COLOR);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 11));
        table.getTableHeader().setReorderingAllowed(false);

        // Column widths
        table.getColumnModel().getColumn(0).setPreferredWidth(55);
        table.getColumnModel().getColumn(1).setPreferredWidth(100);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBackground(BG_DARK);
        scroll.getViewport().setBackground(BG_DARK);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR, 1));

        add(headerPanel, BorderLayout.NORTH);
        add(scroll,      BorderLayout.CENTER);
    }

    // ── Public methods ─────────────────────────────────────────────────

    /** Force refresh all register values from MARS RegisterFile. */
    public void refreshAll() {
        try {
            Register[] regs = RegisterFile.getRegisters();
            for (int i = 0; i < 32 && i < regs.length; i++) {
                values[i] = regs[i].getValue();
                rowColors[i] = null;
            }
            values[32] = RegisterFile.getProgramCounter();
            // HI and LO: read via register names
            Register hi = RegisterFile.getUserRegister("$hi");
            Register lo = RegisterFile.getUserRegister("$lo");
            values[33] = (hi != null) ? hi.getValue() : 0;
            values[34] = (lo != null) ? lo.getValue() : 0;
        } catch (Exception ignored) {}
        model.fireTableDataChanged();
    }

    // ── ExecutionListener ──────────────────────────────────────────────

    public void instructionExecuted(int mipsAddress, int cSourceLine, ProgramStatement stmt) {
        // Snapshot previous values, then refresh
        System.arraycopy(values, 0, prevValues, 0, TOTAL_REGS);
        refreshAll();

        // Find which registers changed
        String mnemonic = (stmt != null && stmt.getInstruction() != null)
            ? stmt.getInstruction().getName() : "";
        InstructionType type = ExecutionAnimator.classify(mnemonic);

        Color baseColor = ExecutionAnimator.getTypeColor(type);
        for (int i = 0; i < TOTAL_REGS; i++) {
            if (values[i] != prevValues[i]) {
                rowColors[i] = baseColor;
                highlights[i] = 0f;
            } else {
                highlights[i] = 1f; // will fade this one quickly
            }
        }
        ExecutionAnimator.getInstance().startAnimation(type);
    }

    public void programAssembled() {
        for (int i = 0; i < TOTAL_REGS; i++) rowColors[i] = null;
        refreshAll();
    }

    public void simulationReset() {
        for (int i = 0; i < TOTAL_REGS; i++) rowColors[i] = null;
        refreshAll();
    }

    // ── AnimationTarget ────────────────────────────────────────────────

    public void animationFrame(float progress) {
        currentAnimProgress = progress;
        // Fade all currently-highlighted rows
        for (int i = 0; i < TOTAL_REGS; i++) {
            if (rowColors[i] != null) {
                rowColors[i] = ExecutionAnimator.fadeColor(
                    ExecutionAnimator.getTypeColor(ExecutionAnimator.getInstance().getCurrentType()),
                    progress);
                if (progress >= 1.0f) rowColors[i] = null; // clear at end
            }
        }
        model.fireTableDataChanged();
    }

    // ── Table Model ────────────────────────────────────────────────────

    private class RegisterTableModel extends AbstractTableModel {
        private final String[] COLS = {"Register", "Value (Hex)", "Value (Dec)"};

        public int getRowCount()    { return TOTAL_REGS; }
        public int getColumnCount() { return 3; }
        public String getColumnName(int col) { return COLS[col]; }

        public Object getValueAt(int row, int col) {
            switch (col) {
                case 0: return REG_NAMES[row];
                case 1: return String.format("0x%08X", values[row]);
                case 2: return Integer.toString(values[row]);
                default: return "";
            }
        }

        public boolean isCellEditable(int r, int c) { return false; }
    }
}
