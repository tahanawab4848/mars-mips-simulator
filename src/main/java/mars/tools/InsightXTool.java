package mars.tools;
import java.io.IOException;

import mars.insightx.*;
import mars.insightx.ui.*;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.util.List;
// Unused imports (IOException) removed – the tool no longer reads files directly.

/**
 * InsightXTool — MARS Tool plugin implementing the InsightX Pipeline & Datapath Visualizer.
 *
 * Appears in the MARS Tools menu as "InsightX – Pipeline & Datapath Visualizer".
 * Opens a standalone JFrame containing:
 *   • File chooser / path bar
 *   • Forwarding toggle + Analyze button
 *   • Tab 1: Scrollable Gantt pipeline diagram with statistics summary
 *   • Tab 2: Animated single-cycle datapath with instruction selector
 *   • Tab 3: Hazard summary report
 */
public class InsightXTool implements MarsTool {

    private static final String TOOL_NAME = "InsightX \u2013 Pipeline & Datapath Visualizer";

    // ── Colours & fonts ──────────────────────────────────────────────────────
    private static final Color BG       = new Color(0x12121E);
    private static final Color PANEL_BG = new Color(0x1A1A2E);
    private static final Color ACC      = new Color(0x4A90D9);
    private static final Color TEXT     = new Color(0xECF0F1);
    private static final Color GREEN    = new Color(0x2ECC71);
    private static final Color RED      = new Color(0xE74C3C);
    private static final Color ORANGE   = new Color(0xE67E22);
    private static final Font  MONO     = new Font("Monospaced", Font.PLAIN, 12);
    private static final Font  BOLD14   = new Font("SansSerif", Font.BOLD, 14);
    private static final Font  PLAIN12  = new Font("SansSerif", Font.PLAIN, 12);

    // ── State ────────────────────────────────────────────────────────────────
    private JFrame frame;
    private JTextField pathField;
    private JCheckBox  forwardingBox;
    private JLabel     cpiLabel, stallLabel, speedupLabel, instrCountLabel;
    private GanttPanel ganttPanel;
    private mars.insightx.ui.PipelineFlowPanel flowPanel;
    private mars.visualization.DatapathPanel datapathPanel;
    private JList<String>     instrList;
    private DefaultListModel<String> instrListModel;
    private JTextArea  hazardReport;

    private List<Instruction> instructions;
    private List<Schedule>    schedules;

    // File watcher
    private javax.swing.Timer fileWatcher;
    private long lastModified = 0L;
    private File watchedFile  = null;

    // ── MarsTool interface ───────────────────────────────────────────────────

    @Override
    public String getName() { return TOOL_NAME; }

    @Override
    public void action() {
        if (frame != null && frame.isVisible()) { frame.toFront(); return; }
        // Ensure UI creation runs on the Event Dispatch Thread
        SwingUtilities.invokeLater(this::buildAndShowUI);
    }

    // ── UI construction ──────────────────────────────────────────────────────

    private void buildAndShowUI() {
        frame = new JFrame(TOOL_NAME);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.setSize(1100, 700);
        frame.setMinimumSize(new Dimension(900, 600));
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(BG);
        frame.setLayout(new BorderLayout(0, 0));

        frame.add(buildTopBar(),    BorderLayout.NORTH);
        frame.add(buildTabbedArea(),BorderLayout.CENTER);
        frame.add(buildStatsBar(),  BorderLayout.SOUTH);

        // Start the background file‑watcher after the UI exists
        startFileWatcher();

        // Ensure the watcher stops when the window is closed
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent e) { stopFileWatcher(); }
        });

        frame.setVisible(true);
    }

    // ── Top bar (file picker + controls) ─────────────────────────────────────

    private JPanel buildTopBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        bar.setBackground(new Color(0x0D0D1A));
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ACC));

        JLabel fileLabel = styledLabel("File:", BOLD14);
        pathField = new JTextField(40);
        styleTextField(pathField);
        pathField.setEditable(false);

        JButton browseBtn = accentButton("Browse…");
        browseBtn.addActionListener(e -> browseFile());

        forwardingBox = new JCheckBox("Forwarding", true);
        forwardingBox.setForeground(TEXT);
        forwardingBox.setBackground(new Color(0x0D0D1A));
        forwardingBox.setFont(PLAIN12);

        JButton analyzeBtn = accentButton("▶ Analyze");
        analyzeBtn.setBackground(GREEN);
        analyzeBtn.addActionListener(e -> runAnalysis());

        // Try to auto-load from MARS globals
        tryAutoLoadCurrentFile();

        bar.add(fileLabel);
        bar.add(pathField);
        bar.add(browseBtn);
        bar.add(Box.createHorizontalStrut(12));
        bar.add(forwardingBox);
        bar.add(analyzeBtn);
        return bar;
    }

    // ── Tabbed area ──────────────────────────────────────────────────────────

    private JTabbedPane buildTabbedArea() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(PANEL_BG);
        tabs.setForeground(TEXT);
        tabs.setFont(BOLD14);

        // Tab 1: Pipeline Gantt + Flow split
        ganttPanel = new GanttPanel();
        JScrollPane ganttScroll = new JScrollPane(ganttPanel);
        ganttScroll.setBackground(new Color(0x1E1E2E));
        ganttScroll.getViewport().setBackground(new Color(0x1E1E2E));
        ganttScroll.setBorder(BorderFactory.createEmptyBorder());

        flowPanel = new mars.insightx.ui.PipelineFlowPanel();
        flowPanel.setPreferredSize(new Dimension(0, 180));

        // Connect Gantt selection to Flow panel state – guard against null schedules
        ganttPanel.setCycleSelectionListener(cycle -> {
            if (schedules != null) {
                flowPanel.setCycleState(cycle, schedules);
            }
        });

        JSplitPane pipelineSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, ganttScroll, flowPanel);
        pipelineSplit.setDividerLocation(360);
        pipelineSplit.setResizeWeight(0.75);
        pipelineSplit.setBackground(BG);
        pipelineSplit.setBorder(BorderFactory.createEmptyBorder());

        tabs.addTab("  Pipeline  ", pipelineSplit);

        // Tab 2: Datapath animation + instruction selector
        datapathPanel = new mars.visualization.DatapathPanel();
        instrListModel = new DefaultListModel<>();
        instrList = new JList<>(instrListModel);
        instrList.setBackground(new Color(0x1A1A2E));
        instrList.setForeground(TEXT);
        instrList.setFont(MONO);
        instrList.setSelectionBackground(ACC);
        instrList.setFixedCellHeight(22);
        instrList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && instructions != null) {
                int idx = instrList.getSelectedIndex();
                if (idx >= 0) datapathPanel.setSelectedInstruction(instructions.get(idx));
            }
        });
        JScrollPane listScroll = new JScrollPane(instrList);
        listScroll.setPreferredSize(new Dimension(260, 0));
        listScroll.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(ACC), "Instructions",
            TitledBorder.LEFT, TitledBorder.TOP, PLAIN12, ACC));

        JSplitPane datapathSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, listScroll, datapathPanel);
        datapathSplit.setDividerLocation(270);
        datapathSplit.setBackground(BG);
        tabs.addTab("  Datapath  ", datapathSplit);

        // Tab 3: Hazard report
        hazardReport = new JTextArea();
        hazardReport.setEditable(false);
        hazardReport.setBackground(new Color(0x0D0D1A));
        hazardReport.setForeground(TEXT);
        hazardReport.setFont(MONO);
        hazardReport.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        JScrollPane reportScroll = new JScrollPane(hazardReport);
        reportScroll.setBorder(BorderFactory.createEmptyBorder());
        tabs.addTab("  Hazard Report  ", reportScroll);

        return tabs;
    }

    // ── Stats bar ────────────────────────────────────────────────────────────

    private JPanel buildStatsBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 6));
        bar.setBackground(new Color(0x0D0D1A));
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, ACC));

        instrCountLabel = statLabel("Instructions: —");
        cpiLabel        = statLabel("CPI: —");
        stallLabel      = statLabel("Stalls: —");
        speedupLabel    = statLabel("Speedup: —");

        bar.add(instrCountLabel);
        bar.add(sep());
        bar.add(cpiLabel);
        bar.add(sep());
        bar.add(stallLabel);
        bar.add(sep());
        bar.add(speedupLabel);
        return bar;
    }

    // ── Analysis ─────────────────────────────────────────────────────────────

    private void browseFile() {
        JFileChooser fc = new JFileChooser();
        fc.setFileFilter(new FileNameExtensionFilter("MIPS Assembly (*.asm)", "asm"));
        if (fc.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            loadFile(fc.getSelectedFile());
        }
    }

    private void loadFile(File f) {
        pathField.setText(f.getAbsolutePath());
        watchedFile  = f;
        lastModified = f.lastModified();
        runAnalysis();
    }

    private void runAnalysis() {
        String path = pathField.getText().trim();
        if (path.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Please select a .asm file first.", TOOL_NAME, JOptionPane.WARNING_MESSAGE);
            return;
        }
        File f = new File(path);
        if (!f.exists()) {
            JOptionPane.showMessageDialog(frame, "File not found:\n" + path, TOOL_NAME, JOptionPane.ERROR_MESSAGE);
            return;
        }

        try {
            instructions = MipsParser.parse(f);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(frame, "Error reading file:\n" + ex.getMessage(), TOOL_NAME, JOptionPane.ERROR_MESSAGE);
            return;
        }

        // All UI updates must happen on the EDT – the analysis may be triggered from a button action (already on EDT),
        // but we guard against accidental off‑EDT calls.
        SwingUtilities.invokeLater(() -> {
            boolean fwd = forwardingBox.isSelected();
            PipelineEngine engine = new PipelineEngine(fwd);
            schedules = engine.schedule(instructions);

            // Update Gantt and Flow view (guard against null schedules)
            if (ganttPanel != null) ganttPanel.setSchedules(schedules);
            if (flowPanel != null) flowPanel.setCycleState(1, schedules);

            // Refresh instruction list for the datapath tab
            instrListModel.clear();
            for (int i = 0; i < instructions.size(); i++) {
                instrListModel.addElement(String.format("%3d: %s", i + 1, instructions.get(i).display));
            }
            if (!instructions.isEmpty()) {
                instrList.setSelectedIndex(0);
                if (datapathPanel != null) datapathPanel.setSelectedInstruction(instructions.get(0));
            }

            // Update statistics labels
            int    n       = instructions.size();
            double cpi     = PipelineEngine.computeCPI(schedules);
            int    stalls  = PipelineEngine.totalStalls(schedules);
            double speedup = PipelineEngine.speedup(schedules);
            instrCountLabel.setText(String.format("Instructions: %d", n));
            cpiLabel.setText(String.format("CPI: %.3f", cpi));
            stallLabel.setText(String.format("Stalls: %d", stalls));
            speedupLabel.setText(String.format("Speedup: %.2fx", speedup));

            // Hazard report
            updateHazardReport(fwd);
        });
    }

    private void updateHazardReport(boolean forwarding) {
        if (schedules == null || schedules.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        sb.append("=== InsightX Hazard Analysis Report ===\n");
        sb.append("Forwarding: ").append(forwarding ? "ENABLED" : "DISABLED").append("\n");
        sb.append(String.format("Total instructions : %d\n", instructions.size()));
        sb.append(String.format("Total stall cycles : %d\n", PipelineEngine.totalStalls(schedules)));
        sb.append(String.format("CPI                : %.4f\n", PipelineEngine.computeCPI(schedules)));
        sb.append(String.format("Ideal CPI          : %.4f\n", PipelineEngine.idealCPI(instructions.size())));
        sb.append(String.format("Pipeline speedup   : %.4fx\n\n", PipelineEngine.speedup(schedules)));

        int rawCount  = 0, luCount = 0, ctrlCount = 0;
        sb.append("── Per-Instruction Detail ──────────────────────────────────────\n");
        for (int i = 0; i < schedules.size(); i++) {
            Schedule sc = schedules.get(i);
            sb.append(String.format("[%3d] %-30s  IF=%-3d EX=%-3d WB=%-3d stalls=%d\n",
                i+1, sc.instruction.display,
                sc.stageCycle[Schedule.IF], sc.stageCycle[Schedule.EX], sc.stageCycle[Schedule.WB],
                sc.stallsBefore));
            for (Hazard h : sc.hazards) {
                sb.append("       ▸ ").append(h).append("\n");
                if (h.kind == Hazard.Kind.RAW)      rawCount++;
                if (h.kind == Hazard.Kind.LOAD_USE) luCount++;
                if (h.kind == Hazard.Kind.CONTROL)  ctrlCount++;
            }
        }

        sb.append("\n── Hazard Summary ──────────────────────────────────────────────\n");
        sb.append(String.format("RAW hazards     : %d\n", rawCount));
        sb.append(String.format("Load-use hazards: %d\n", luCount));
        sb.append(String.format("Control hazards : %d\n", ctrlCount));

        hazardReport.setText(sb.toString());
        hazardReport.setCaretPosition(0);
    }

    // ── File watcher (auto-refresh on save) ──────────────────────────────────

    private void startFileWatcher() {
        fileWatcher = new javax.swing.Timer(1000, e -> {
            if (watchedFile == null) return;
            long cur = watchedFile.lastModified();
            if (cur != lastModified) {
                lastModified = cur;
                runAnalysis();
            }
        });
        fileWatcher.setRepeats(true);
        fileWatcher.start();
    }

    // ── MARS integration — auto-load currently open file ────────────────────

    private void tryAutoLoadCurrentFile() {
        try {
            // Globals.program holds the currently loaded MIPSprogram; getFilename() returns its path.
            if (mars.Globals.program != null) {
                String path = mars.Globals.program.getFilename();
                if (path != null && !path.isEmpty()) {
                    pathField.setText(path);
                    watchedFile  = new File(path);
                    lastModified = watchedFile.lastModified();
                }
            }
        } catch (Exception ignored) {}
    }

    // ── UI helpers ───────────────────────────────────────────────────────────

    private static JLabel styledLabel(String text, Font font) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT);
        l.setFont(font);
        return l;
    }

    private static JLabel statLabel(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT);
        l.setFont(new Font("Monospaced", Font.PLAIN, 13));
        return l;
    }

    private static JSeparator sep() {
        JSeparator s = new JSeparator(SwingConstants.VERTICAL);
        s.setPreferredSize(new Dimension(1, 18));
        s.setForeground(new Color(0x3A3A5A));
        return s;
    }

    private static JButton accentButton(String text) {
        JButton b = new JButton(text);
        b.setBackground(ACC);
        b.setForeground(Color.WHITE);
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    private void stopFileWatcher() {
            if (fileWatcher != null) {
                fileWatcher.stop();
                fileWatcher = null;
            }
    }

    private static void styleTextField(JTextField tf) {
        tf.setBackground(new Color(0x1A1A2E));
        tf.setForeground(TEXT);
        tf.setCaretColor(TEXT);
        tf.setFont(new Font("Monospaced", Font.PLAIN, 12));
        tf.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ACC, 1),
            BorderFactory.createEmptyBorder(4, 8, 4, 8)));
    }

    private static final Color ACC2 = new Color(0x4A90D9);
}
