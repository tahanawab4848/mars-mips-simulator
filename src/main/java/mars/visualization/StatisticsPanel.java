package mars.visualization;

import mars.ProgramStatement;
import mars.mapping.ExecutionTracker;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.*;
import java.util.List;

/*
 * StatisticsPanel — live execution statistics dashboard.
 * Shows instruction counts, CPI estimate, and wall-clock time.
 *
 * Author: MARS C Extension
 */
public class StatisticsPanel extends JPanel
    implements ExecutionTracker.ExecutionListener {

    // ── Colors ─────────────────────────────────────────────────────────
    private static final Color BG        = new Color(0x1E1E2E);
    private static final Color HDR_BG    = new Color(0x313147);
    private static final Color TEXT      = new Color(0xCDD6F4);
    private static final Color ACCENT    = new Color(0x89B4FA);
    private static final Color BORDER    = new Color(0x45475A);
    private static final Color ROW_ODD   = new Color(0x252537);
    private static final Color ROW_EVEN  = new Color(0x1E1E2E);
    private static final Color VAL_GREEN = new Color(0xA6E3A1);
    private static final Color VAL_BLUE  = new Color(0x89DCEB);
    private static final Color VAL_ORANGE= new Color(0xFAB387);

    // ── Row definitions: [label, field key] ───────────────────────────
    private static final String[][] ROWS = {
        {"Total Instructions",  "total"},
        {"Arithmetic / Logic",  "arith"},
        {"Memory (Load/Store)", "mem"},
        {"Branches",            "branch"},
        {"Jumps",               "jump"},
        {"",                    "sep"},
        {"Estimated CPI",       "cpi"},
        {"Execution Time (ms)", "time"},
        {"Instructions / sec",  "ips"},
    };

    // ── State ──────────────────────────────────────────────────────────
    private final Map<String, String> values = new LinkedHashMap<String, String>();
    private StatsTableModel model;

    // ── Constructor ────────────────────────────────────────────────────
    public StatisticsPanel() {
        setLayout(new BorderLayout(0, 0));
        setBackground(BG);
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        initValues();
        buildUI();
        ExecutionTracker.getInstance().addListener(this);
    }

    // ── Build UI ───────────────────────────────────────────────────────
    private void buildUI() {
        JLabel title = new JLabel("  Execution Statistics", JLabel.LEFT);
        title.setFont(new Font("SansSerif", Font.BOLD, 13));
        title.setForeground(ACCENT);
        title.setBackground(HDR_BG);
        title.setOpaque(true);
        title.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        model = new StatsTableModel();
        JTable table = new JTable(model);
        table.setBackground(BG);
        table.setForeground(TEXT);
        table.setGridColor(BORDER);
        table.setRowHeight(24);
        table.setFont(new Font("Monospaced", Font.PLAIN, 12));
        table.setTableHeader(null); // no header for this table
        table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            public Component getTableCellRendererComponent(
                JTable t, Object val, boolean sel, boolean foc, int r, int c) {
                Component comp = super.getTableCellRendererComponent(t,val,sel,foc,r,c);
                String key = ROWS[r][1];
                if ("sep".equals(key)) {
                    comp.setBackground(BORDER);
                    ((JLabel)comp).setText("");
                    return comp;
                }
                comp.setBackground((r % 2 == 0) ? ROW_EVEN : ROW_ODD);
                comp.setForeground(c == 0 ? ACCENT : TEXT);
                if (c == 1) {
                    if ("total".equals(key) || "arith".equals(key)) comp.setForeground(VAL_GREEN);
                    else if ("mem".equals(key))    comp.setForeground(VAL_BLUE);
                    else if ("time".equals(key))   comp.setForeground(VAL_ORANGE);
                }
                if (c == 0) comp.setFont(new Font("SansSerif", Font.PLAIN, 12));
                else        comp.setFont(new Font("Monospaced", Font.BOLD,  13));
                return comp;
            }
        });
        table.getColumnModel().getColumn(0).setPreferredWidth(160);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBackground(BG);
        scroll.getViewport().setBackground(BG);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER, 1));

        // Reset button
        JButton resetBtn = new JButton("Reset Stats");
        resetBtn.setBackground(HDR_BG);
        resetBtn.setForeground(ACCENT);
        resetBtn.setFocusPainted(false);
        resetBtn.setFont(new Font("SansSerif", Font.BOLD, 11));
        resetBtn.addActionListener(e -> {
            initValues();
            model.fireTableDataChanged();
        });

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.setBackground(BG);
        south.add(resetBtn);

        add(title,  BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(south,  BorderLayout.SOUTH);
    }

    // ── Data ───────────────────────────────────────────────────────────
    private void initValues() {
        for (String[] row : ROWS) {
            values.put(row[1], "0");
        }
        values.put("cpi", "1.00");
        values.put("time", "0");
        values.put("ips", "0");
    }

    private void refresh() {
        ExecutionTracker et = ExecutionTracker.getInstance();
        int total  = et.getTotalInstructions();
        long elapsed = et.getElapsedMs();

        values.put("total",  Integer.toString(total));
        values.put("arith",  Integer.toString(et.getArithmeticCount()));
        values.put("mem",    Integer.toString(et.getMemoryCount()));
        values.put("branch", Integer.toString(et.getBranchCount()));
        values.put("jump",   Integer.toString(et.getJumpCount()));
        values.put("cpi",    String.format("%.2f", et.getEstimatedCPI()));
        values.put("time",   Long.toString(elapsed));
        long ips = (elapsed > 0) ? (total * 1000L / elapsed) : 0L;
        values.put("ips",    Long.toString(ips));
        values.put("sep",    "");

        model.fireTableDataChanged();
    }

    // ── ExecutionListener ──────────────────────────────────────────────
    public void instructionExecuted(int addr, int cLine, ProgramStatement stmt) { refresh(); }
    public void programAssembled()  { initValues(); model.fireTableDataChanged(); }
    public void simulationReset()   { initValues(); model.fireTableDataChanged(); }

    // ── Table Model ────────────────────────────────────────────────────
    private class StatsTableModel extends AbstractTableModel {
        public int getRowCount()    { return ROWS.length; }
        public int getColumnCount() { return 2; }
        public Object getValueAt(int r, int c) {
            if (c == 0) return ROWS[r][0];
            return values.getOrDefault(ROWS[r][1], "");
        }
        public boolean isCellEditable(int r, int c) { return false; }
    }
}
