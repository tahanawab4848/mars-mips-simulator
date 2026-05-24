package mars.visualization;

import mars.ProgramStatement;
import mars.mapping.ExecutionTracker;
import mars.mips.hardware.Memory;
import mars.mips.hardware.MemoryAccessNotice;
import mars.mips.hardware.RegisterFile;
import mars.visualization.ExecutionAnimator.AnimationTarget;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/*
 * MemoryVisualizer — displays memory segments (Stack, Data, Heap) in real-time
 * with address/value columns and animated highlight when values change.
 *
 * Author: MARS C Extension
 */
public class MemoryVisualizer extends JPanel
    implements ExecutionTracker.ExecutionListener, AnimationTarget {

    // ── Color constants ────────────────────────────────────────────────
    private static final Color BG          = new Color(0x1E1E2E);
    private static final Color ROW_ODD     = new Color(0x252537);
    private static final Color ROW_EVEN    = new Color(0x1E1E2E);
    private static final Color HEADER_BG   = new Color(0x313147);
    private static final Color TEXT_COLOR  = new Color(0xCDD6F4);
    private static final Color ADDR_COLOR  = new Color(0x89B4FA);
    private static final Color BORDER      = new Color(0x45475A);
    private static final Color HIT_COLOR   = new Color(0x3498DB); // blue for memory writes

    // ── Display rows ───────────────────────────────────────────────────
    private static final int ROWS_SHOWN = 32;    // words shown per tab
    private static final int BYTES_PER_WORD = 4;

    // ── State ──────────────────────────────────────────────────────────
    private int lastAccessedAddress = -1;
    private final Map<Integer, Color> highlightMap = new LinkedHashMap<Integer, Color>();

    // ── GUI ────────────────────────────────────────────────────────────
    private JTabbedPane tabs;
    private MemTable   stackTable;
    private MemTable   dataTable;
    private MemTable   heapTable;

    // ── Constructor ────────────────────────────────────────────────────
    public MemoryVisualizer() {
        setLayout(new BorderLayout());
        setBackground(BG);
        setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        buildUI();
        ExecutionTracker.getInstance().addListener(this);
        ExecutionAnimator.getInstance().addTarget(this);
    }

    // ── UI ─────────────────────────────────────────────────────────────
    private void buildUI() {
        JLabel title = new JLabel("  Memory Viewer", JLabel.LEFT);
        title.setFont(new Font("SansSerif", Font.BOLD, 13));
        title.setForeground(ADDR_COLOR);
        title.setBackground(HEADER_BG);
        title.setOpaque(true);
        title.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        tabs = new JTabbedPane(JTabbedPane.TOP);
        tabs.setBackground(BG);
        tabs.setForeground(TEXT_COLOR);

        stackTable = new MemTable();
        dataTable  = new MemTable();
        heapTable  = new MemTable();

        tabs.addTab("Stack",      wrapScroll(stackTable));
        tabs.addTab("Data Seg",   wrapScroll(dataTable));
        tabs.addTab("Heap",       wrapScroll(heapTable));

        add(title, BorderLayout.NORTH);
        add(tabs,  BorderLayout.CENTER);
    }

    private JScrollPane wrapScroll(JTable t) {
        JScrollPane sp = new JScrollPane(t);
        sp.setBackground(BG);
        sp.getViewport().setBackground(BG);
        sp.setBorder(BorderFactory.createLineBorder(BORDER, 1));
        return sp;
    }

    // ── Data loading ───────────────────────────────────────────────────
    private void reloadAll() {
        try {
            int sp = RegisterFile.getValue(29); // $sp
            loadTable(stackTable, sp - (ROWS_SHOWN/2)*4, ROWS_SHOWN);

            loadTable(dataTable,  Memory.dataBaseAddress, ROWS_SHOWN);
            loadTable(heapTable,  Memory.heapBaseAddress, ROWS_SHOWN);
        } catch (Exception ignored) {}
    }

    private void loadTable(MemTable t, int startAddr, int rows) {
        // Align to word boundary
        startAddr = startAddr & ~3;
        List<int[]> data = new ArrayList<int[]>(); // [address, value]
        for (int i = 0; i < rows; i++) {
            int addr = startAddr + i * BYTES_PER_WORD;
            int val  = 0;
            try {
                val = Memory.getInstance().getWord(addr);
            } catch (Exception ignored) {}
            data.add(new int[]{addr, val});
        }
        t.setData(data);
    }

    // ── ExecutionListener ──────────────────────────────────────────────
    public void instructionExecuted(int mipsAddress, int cSourceLine, ProgramStatement stmt) {
        reloadAll();
        // Highlight last accessed address if memory instruction
        String mn = (stmt != null && stmt.getInstruction() != null)
            ? stmt.getInstruction().getName().toLowerCase() : "";
        if (mn.startsWith("lw") || mn.startsWith("sw") ||
            mn.startsWith("lb") || mn.startsWith("sb") ||
            mn.startsWith("lh") || mn.startsWith("sh")) {
            // We don't know the exact address here without deep inspection;
            // use the data segment start as a refresh trigger
            ExecutionAnimator.getInstance().startAnimation(
                ExecutionAnimator.classify(mn));
        }
    }

    public void programAssembled() {
        highlightMap.clear();
        reloadAll();
    }

    public void simulationReset() {
        highlightMap.clear();
        reloadAll();
    }

    // ── AnimationTarget ────────────────────────────────────────────────
    public void animationFrame(float progress) {
        // Fade all highlighted addresses
        if (progress >= 1.0f) {
            highlightMap.clear();
        }
        dataTable.repaint();
        stackTable.repaint();
        heapTable.repaint();
    }

    // ── Inner Table ────────────────────────────────────────────────────
    private class MemTable extends JTable {
        private List<int[]> data = new ArrayList<int[]>();
        private final String[] COLS = {"Address", "Hex Value", "Dec Value", "ASCII"};

        MemTable() {
            setModel(new AbstractTableModel() {
                public int    getRowCount()    { return data.size(); }
                public int    getColumnCount() { return 4; }
                public String getColumnName(int c) { return COLS[c]; }
                public Object getValueAt(int r, int c) {
                    int[] row = data.get(r);
                    int addr  = row[0];
                    int val   = row[1];
                    switch (c) {
                        case 0: return String.format("0x%08X", addr);
                        case 1: return String.format("0x%08X", val);
                        case 2: return Integer.toString(val);
                        case 3: return toAscii(val);
                        default: return "";
                    }
                }
                public boolean isCellEditable(int r, int c) { return false; }
            });

            setBackground(BG);
            setForeground(TEXT_COLOR);
            setGridColor(BORDER);
            setRowHeight(19);
            setFont(new Font("Monospaced", Font.PLAIN, 11));
            getTableHeader().setBackground(HEADER_BG);
            getTableHeader().setForeground(ADDR_COLOR);
            getTableHeader().setFont(new Font("SansSerif", Font.BOLD, 11));
            getTableHeader().setReorderingAllowed(false);

            setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
                public Component getTableCellRendererComponent(
                    JTable table, Object val, boolean sel, boolean focus, int r, int c) {
                    Component comp = super.getTableCellRendererComponent(
                        table, val, sel, focus, r, c);
                    Color bg = (r % 2 == 0) ? ROW_EVEN : ROW_ODD;
                    if (data.size() > r) {
                        Integer addr = data.get(r)[0];
                        Color hc = highlightMap.get(addr);
                        if (hc != null) bg = hc;
                    }
                    comp.setBackground(sel ? new Color(0x45475A) : bg);
                    comp.setForeground(c == 0 ? ADDR_COLOR : TEXT_COLOR);
                    return comp;
                }
            });
        }

        void setData(List<int[]> d) {
            this.data = d;
            ((AbstractTableModel)getModel()).fireTableDataChanged();
        }

        private String toAscii(int word) {
            StringBuilder sb = new StringBuilder();
            for (int i = 3; i >= 0; i--) {
                byte b = (byte)((word >> (i*8)) & 0xFF);
                char ch = (char)(b & 0xFF);
                sb.append((ch >= 32 && ch < 127) ? ch : '.');
            }
            return sb.toString();
        }
    }
}
