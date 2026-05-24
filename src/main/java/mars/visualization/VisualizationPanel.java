package mars.visualization;

import mars.Globals;
import mars.ccompiler.CompilerOutput;
import mars.mapping.ExecutionTracker;
import mars.mapping.SourceMapper;
import mars.venus.*;

import javax.swing.*;
import java.awt.*;
import java.io.*;

/*
 * VisualizationPanel — top-level container for the "C Programming" tab in MARS.
 *
 * Layout:
 *   ┌─────────────────┬────────────────────────────────────────────────┐
 *   │   CEditorPanel  │  JTabbedPane (right side)                      │
 *   │  (C source code)│  ┌──────────┬──────────┬──────────┬─────────┐ │
 *   │                 │  │ Register │ Memory   │Datapath  │Pipeline │ │
 *   │                 │  ├──────────┴──────────┴──────────┴─────────┤ │
 *   │                 │  │ InstructionFlow   │  Statistics           │ │
 *   └─────────────────┴──┴────────────────────────────────────────────┘
 *
 * Author: MARS C Extension
 */
public class VisualizationPanel extends JPanel {

    private static final Color BG     = new Color(0x1E1E2E);
    private static final Color ACCENT = new Color(0x89B4FA);
    private static final Color BORDER = new Color(0x45475A);

    // Sub-panels
    private CEditorPanel       cEditor;
    private RegisterVisualizer registerViz;
    private MemoryVisualizer   memoryViz;
    private DatapathPanel      datapathPanel;
    private PipelinePanel      pipelinePanel;
    private InstructionFlowPanel flowPanel;
    private StatisticsPanel    statsPanel;

    // Source mapping
    private final SourceMapper sourceMapper = new SourceMapper();

    // State flags
    private boolean autoRunOnCompile = false;

    // ── Constructor ────────────────────────────────────────────────────
    public VisualizationPanel() {
        setLayout(new BorderLayout());
        setBackground(BG);
        buildUI();
        ExecutionTracker.getInstance().setSourceMapper(sourceMapper);
    }

    // ── UI Construction ────────────────────────────────────────────────
    private void buildUI() {
        // Left: C editor
        cEditor = new CEditorPanel(this, sourceMapper);
        cEditor.setMinimumSize(new Dimension(300, 200));
        cEditor.setPreferredSize(new Dimension(420, 0));

        // Right: tabbed visualization
        JTabbedPane rightTabs = buildRightTabs();
        rightTabs.setMinimumSize(new Dimension(200, 200));

        // Bottom row: flow + stats
        JPanel bottomRow = buildBottomRow();

        // Vertical split: top (editor | viz tabs) / bottom (flow | stats)
        JSplitPane topSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, cEditor, rightTabs);
        topSplit.setResizeWeight(0.45);
        topSplit.setOneTouchExpandable(true);
        topSplit.setDividerSize(6);
        topSplit.setBackground(BG);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topSplit, bottomRow);
        mainSplit.setResizeWeight(0.60);
        mainSplit.setOneTouchExpandable(true);
        mainSplit.setDividerSize(6);
        mainSplit.setBackground(BG);

        add(buildHeader(), BorderLayout.NORTH);
        add(mainSplit,     BorderLayout.CENTER);
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(0x313147));
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));

        JLabel title = new JLabel("  [C-MIPS] Integrated Visualization Interface", JLabel.LEFT);
        title.setFont(new Font("SansSerif", Font.BOLD, 14));
        title.setForeground(ACCENT);
        title.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        JLabel hint = new JLabel("Write C -> Compile -> Step -> Watch execution unfold  ");
        hint.setFont(new Font("SansSerif", Font.ITALIC, 12));
        hint.setForeground(new Color(0x6C7086));
        hint.setBorder(BorderFactory.createEmptyBorder(6, 0, 6, 10));

        header.add(title, BorderLayout.WEST);
        header.add(hint,  BorderLayout.EAST);
        return header;
    }

    private JTabbedPane buildRightTabs() {
        registerViz   = new RegisterVisualizer();
        memoryViz     = new MemoryVisualizer();
        datapathPanel = new DatapathPanel();
        pipelinePanel = new PipelinePanel();

        JTabbedPane tabs = new JTabbedPane(JTabbedPane.TOP);
        styleTabPane(tabs);
        tabs.addTab("Registers", registerViz);
        tabs.addTab("Memory",    memoryViz);
        tabs.addTab("Datapath",  datapathPanel);
        tabs.addTab("Pipeline",  pipelinePanel);
        return tabs;
    }

    private JPanel buildBottomRow() {
        flowPanel  = new InstructionFlowPanel();
        statsPanel = new StatisticsPanel();

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, flowPanel, statsPanel);
        split.setResizeWeight(0.65);
        split.setOneTouchExpandable(true);
        split.setDividerSize(6);
        split.setBackground(BG);

        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(BG);
        p.add(split, BorderLayout.CENTER);
        return p;
    }

    private void styleTabPane(JTabbedPane tabs) {
        tabs.setBackground(new Color(0x252537));
        tabs.setForeground(new Color(0xCDD6F4));
        tabs.setFont(new Font("SansSerif", Font.BOLD, 12));
    }

    // ── Callbacks from CEditorPanel ────────────────────────────────────

    /**
     * Called by CEditorPanel after successful compilation.
     * Loads the generated .asm into MARS, assembles, and optionally runs.
     */
    public void onCompileSuccess(CompilerOutput output, String asmPath) {
        // Parse .loc directives for source mapping
        try {
            File asmFile = new File(asmPath);
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(asmFile))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
            }
            sourceMapper.parseAsmText(sb.toString());
        } catch (IOException ignored) {}

        // Load the .asm file into MARS via the existing FileOpen + Assemble mechanism
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                loadAndAssembleInMARS(asmPath);
            }
        });
    }

    private void loadAndAssembleInMARS(String asmPath) {
        try {
            VenusUI gui = Globals.getGui();
            if (gui == null) return;

            // Open the file in MARS editor
            EditTabbedPane etp = (EditTabbedPane) gui.getMainPane().getEditTabbedPane();
            try {
                String canonicalPath = new File(asmPath).getCanonicalPath();
                EditPane existing = etp.getEditPaneForFile(canonicalPath);
                if (existing != null) {
                    etp.remove(existing); // Force close so MARS reloads from disk
                }
            } catch (IOException ignored) {}
            etp.openFile(new File(asmPath));

            // Trigger assemble action
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    gui.getMainPane().setSelectedIndex(0); // go to Edit tab briefly
                    // Fire the existing RunAssembleAction
                    for (java.awt.event.ActionListener al :
                         ((JButton) findAssembleButton(gui)).getActionListeners()) {
                        al.actionPerformed(new java.awt.event.ActionEvent(this, 0, "Assemble"));
                    }
                    
                    // Switch back to C Programming tab (since RunAssembleAction switches to Execute)
                    SwingUtilities.invokeLater(new Runnable() {
                        public void run() {
                            gui.getMainPane().setSelectedComponent(VisualizationPanel.this);
                            if (autoRunOnCompile) {
                                autoRunOnCompile = false;
                                // RunGoAction will be triggered after assemble completes via FileStatus
                            }
                        }
                    });
                }
            });
        } catch (Exception e) {
            System.err.println("[VisualizationPanel] Failed to load ASM: " + e.getMessage());
        }
    }

    /** Finds the Assemble toolbar button by iterating toolbar components. */
    private java.awt.Component findAssembleButton(VenusUI gui) {
        // Walk the content pane to find the JToolBar and then the Assemble button
        try {
            JPanel center = (JPanel) gui.getContentPane().getComponent(0);
            JPanel north  = (JPanel) center.getComponent(0);
            JToolBar tb   = (JToolBar) north.getComponent(0);
            for (int i = 0; i < tb.getComponentCount(); i++) {
                java.awt.Component c = tb.getComponent(i);
                if (c instanceof JButton) {
                    JButton btn = (JButton) c;
                    if (btn.getToolTipText() != null &&
                        btn.getToolTipText().toLowerCase().contains("assemble")) {
                        return btn;
                    }
                }
            }
        } catch (Exception ignored) {}
        return new JButton(); // fallback (no-op)
    }

    // ── Execution control passthrough ──────────────────────────────────

    public void setAutoRunOnCompile(boolean v) { this.autoRunOnCompile = v; }

    public void requestStep() {
        VenusUI gui = Globals.getGui();
        if (gui != null) gui.getMainPane().setSelectedIndex(1); // switch to Execute
        // Delegate to MARS RunStepAction via keyboard shortcut F7
        KeyboardFocusManager.getCurrentKeyboardFocusManager().dispatchKeyEvent(
            new java.awt.event.KeyEvent(this, java.awt.event.KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(), 0, java.awt.event.KeyEvent.VK_F7, '\0'));
    }

    public void requestPause() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().dispatchKeyEvent(
            new java.awt.event.KeyEvent(this, java.awt.event.KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(), 0, java.awt.event.KeyEvent.VK_F9, '\0'));
    }

    public void requestStop() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().dispatchKeyEvent(
            new java.awt.event.KeyEvent(this, java.awt.event.KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(), 0, java.awt.event.KeyEvent.VK_F11, '\0'));
    }

    public void requestReset() {
        KeyboardFocusManager.getCurrentKeyboardFocusManager().dispatchKeyEvent(
            new java.awt.event.KeyEvent(this, java.awt.event.KeyEvent.KEY_PRESSED,
                System.currentTimeMillis(), 0, java.awt.event.KeyEvent.VK_F12, '\0'));
        ExecutionTracker.getInstance().onSimulationReset();
    }

    // ── Public accessors ───────────────────────────────────────────────
    public CEditorPanel       getCEditor()      { return cEditor; }
    public SourceMapper       getSourceMapper() { return sourceMapper; }
}
