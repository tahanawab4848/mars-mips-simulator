package mars.visualization;

import mars.ProgramStatement;
import mars.mapping.ExecutionTracker;
import mars.visualization.ExecutionAnimator.InstructionType;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/*
 * InstructionFlowPanel — scrolling list of recently-executed MIPS instructions.
 * Color-coded by type, with the currently-executing instruction highlighted.
 *
 * Author: MARS C Extension
 */
public class InstructionFlowPanel extends JPanel
    implements ExecutionTracker.ExecutionListener {

    // ── Colors ─────────────────────────────────────────────────────────
    private static final Color BG       = new Color(0x1E1E2E);
    private static final Color HDR_BG   = new Color(0x313147);
    private static final Color TEXT     = new Color(0xCDD6F4);
    private static final Color ACCENT   = new Color(0x89B4FA);
    private static final Color BORDER   = new Color(0x45475A);
    private static final Color CURR_BG  = new Color(0x313147);

    private static final int MAX_HISTORY = 200;

    // ── State ──────────────────────────────────────────────────────────
    private final List<InstrRecord> history = new ArrayList<InstrRecord>();
    private int currentRow = -1;
    private FlowTableModel model;
    private JTable table;

    private static class InstrRecord {
        int    address;
        String mnemonic;
        String operands;
        int    cLine;
        InstructionType type;
        Color  typeColor;

        InstrRecord(int addr, String mn, String ops, int cLine, InstructionType t) {
            this.address  = addr;
            this.mnemonic = mn;
            this.operands = ops;
            this.cLine    = cLine;
            this.type     = t;
            this.typeColor = ExecutionAnimator.getTypeColor(t);
        }
    }

    // ── Constructor ────────────────────────────────────────────────────
    public InstructionFlowPanel() {
        setLayout(new BorderLayout());
        setBackground(BG);
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        buildUI();
        ExecutionTracker.getInstance().addListener(this);
    }

    private void buildUI() {
        JLabel title = new JLabel("  Instruction Flow", JLabel.LEFT);
        title.setFont(new Font("SansSerif", Font.BOLD, 13));
        title.setForeground(ACCENT);
        title.setBackground(HDR_BG);
        title.setOpaque(true);
        title.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        model = new FlowTableModel();
        table = new JTable(model) {
            public Component prepareRenderer(TableCellRenderer r, int row, int col) {
                Component c = super.prepareRenderer(r, row, col);
                if (row >= 0 && row < history.size()) {
                    InstrRecord rec = history.get(history.size() - 1 - row); // newest first
                    boolean isCurrent = (row == 0);
                    c.setBackground(isCurrent ? new Color(0x45475A) : BG);
                    if (col == 1) c.setForeground(rec.typeColor);
                    else if (col == 0) c.setForeground(ACCENT);
                    else              c.setForeground(TEXT);
                }
                return c;
            }
        };
        table.setBackground(BG);
        table.setForeground(TEXT);
        table.setGridColor(BORDER);
        table.setRowHeight(19);
        table.setFont(new Font("Monospaced", Font.PLAIN, 12));
        table.getTableHeader().setBackground(HDR_BG);
        table.getTableHeader().setForeground(ACCENT);
        table.getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 11));
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(90);
        table.getColumnModel().getColumn(1).setPreferredWidth(70);
        table.getColumnModel().getColumn(2).setPreferredWidth(120);
        table.getColumnModel().getColumn(3).setPreferredWidth(50);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBackground(BG);
        scroll.getViewport().setBackground(BG);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER, 1));

        // Legend panel
        JPanel legend = buildLegend();

        add(title,  BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(legend, BorderLayout.SOUTH);
    }

    private JPanel buildLegend() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setBackground(HDR_BG);
        p.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER));
        addLegendItem(p, "Arithmetic", InstructionType.ARITHMETIC);
        addLegendItem(p, "Load",       InstructionType.LOAD);
        addLegendItem(p, "Store",      InstructionType.STORE);
        addLegendItem(p, "Branch",     InstructionType.BRANCH);
        addLegendItem(p, "Jump",       InstructionType.JUMP);
        addLegendItem(p, "Syscall",    InstructionType.SYSCALL);
        return p;
    }

    private void addLegendItem(JPanel p, String name, InstructionType type) {
        JLabel dot = new JLabel("* ");
        dot.setForeground(ExecutionAnimator.getTypeColor(type));
        dot.setFont(new Font("SansSerif", Font.PLAIN, 14));
        JLabel lbl = new JLabel(name);
        lbl.setForeground(TEXT);
        lbl.setFont(new Font("SansSerif", Font.PLAIN, 10));
        p.add(dot);
        p.add(lbl);
    }

    // ── ExecutionListener ──────────────────────────────────────────────
    public void instructionExecuted(int mipsAddress, int cSourceLine, ProgramStatement stmt) {
        String mnemonic = "";
        String operands = "";
        if (stmt != null) {
            if (stmt.getInstruction() != null) mnemonic = stmt.getInstruction().getName();
            // Use printable form stripped of address prefix
            String printed = stmt.getPrintableBasicAssemblyStatement();
            if (printed != null && !printed.isEmpty()) {
                String[] parts = printed.split("\\s+", 2);
                if (parts.length > 1) operands = parts[1];
            }
        }
        InstructionType type = ExecutionAnimator.classify(mnemonic);
        InstrRecord rec = new InstrRecord(mipsAddress, mnemonic, operands, cSourceLine, type);

        history.add(rec);
        if (history.size() > MAX_HISTORY) history.remove(0);

        currentRow = history.size() - 1;
        model.fireTableDataChanged();
        // Scroll to top (newest)
        if (table.getRowCount() > 0) {
            table.scrollRectToVisible(table.getCellRect(0, 0, true));
        }
    }

    public void programAssembled() {
        history.clear();
        currentRow = -1;
        model.fireTableDataChanged();
    }

    public void simulationReset() {
        history.clear();
        currentRow = -1;
        model.fireTableDataChanged();
    }

    // ── Table Model ────────────────────────────────────────────────────
    private class FlowTableModel extends AbstractTableModel {
        private final String[] COLS = {"PC Address", "Mnemonic", "Operands", "C Line"};

        public int getRowCount()    { return history.size(); }
        public int getColumnCount() { return 4; }
        public String getColumnName(int c) { return COLS[c]; }

        public Object getValueAt(int row, int col) {
            // Display newest first
            int idx = history.size() - 1 - row;
            if (idx < 0 || idx >= history.size()) return "";
            InstrRecord rec = history.get(idx);
            switch (col) {
                case 0: return String.format("0x%08X", rec.address);
                case 1: return rec.mnemonic;
                case 2: return rec.operands;
                case 3: return (rec.cLine > 0) ? String.valueOf(rec.cLine) : "-";
                default: return "";
            }
        }
        public boolean isCellEditable(int r, int c) { return false; }
    }
}
