package mars.visualization;

import mars.ProgramStatement;
import mars.ccompiler.CCompilerConfig;
import mars.ccompiler.CompilerOutput;
import mars.ccompiler.CompilerRunner;
import mars.mapping.ExecutionTracker;
import mars.mapping.SourceMapper;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;

/*
 * CEditorPanel — C source code editor with syntax highlighting, line numbers,
 * current-line highlighting during execution, and a toolbar with compile/run controls.
 * Author: MARS C Extension
 */
public class CEditorPanel extends JPanel {

    // ── Colors (dark theme) ────────────────────────────────────────────
    private static final Color BG          = new Color(0x1E1E2E);
    private static final Color EDITOR_BG   = new Color(0x181825);
    private static final Color GUTTER_BG   = new Color(0x252537);
    private static final Color GUTTER_TEXT = new Color(0x585B70);
    private static final Color TEXT_COLOR  = new Color(0xCDD6F4);
    private static final Color KEYWORD_CLR = new Color(0xCBA6F7); // purple
    private static final Color TYPE_CLR    = new Color(0x89B4FA); // blue
    private static final Color STRING_CLR  = new Color(0xA6E3A1); // green
    private static final Color COMMENT_CLR = new Color(0x6C7086); // grey
    private static final Color NUMBER_CLR  = new Color(0xFAB387); // orange
    private static final Color CURSOR_LINE = new Color(0x313147); // current line bg
    private static final Color EXEC_LINE   = new Color(0x45380A); // executing line bg
    private static final Color BORDER_CLR  = new Color(0x45475A);
    private static final Color ACCENT      = new Color(0x89B4FA);
    private static final Color CONSOLE_BG  = new Color(0x11111B);
    private static final Color SUCCESS_CLR = new Color(0xA6E3A1);
    private static final Color ERROR_CLR   = new Color(0xF38BA8);

    // ── C Keywords ────────────────────────────────────────────────────
    private static final Set<String> KEYWORDS = new HashSet<String>(Arrays.asList(
        "auto","break","case","char","const","continue","default","do","double",
        "else","enum","extern","float","for","goto","if","inline","int","long",
        "register","restrict","return","short","signed","sizeof","static","struct",
        "switch","typedef","union","unsigned","void","volatile","while"
    ));

    // ── GUI Components ─────────────────────────────────────────────────
    private JTextPane    editorPane;
    private JPanel       gutterPanel;
    private JTextArea    consoleArea;
    private JSplitPane   mainSplit;
    private JLabel       statusLabel;

    // Buttons
    private JButton compileBtn, runBtn, stepBtn, pauseBtn, stopBtn, resetBtn;
    private JButton openBtn, saveBtn;

    // ── State ──────────────────────────────────────────────────────────
    private CompilerRunner currentRunner;
    private int    currentExecLine = -1;
    private File   currentFile     = null;
    private boolean highlightingInProgress = false;
    private final SourceMapper sourceMapper;

    // Reference to parent panel for compile callback
    private VisualizationPanel parent;

    // ── Constructor ────────────────────────────────────────────────────
    public CEditorPanel(VisualizationPanel parent, SourceMapper sourceMapper) {
        this.parent       = parent;
        this.sourceMapper = sourceMapper;
        setLayout(new BorderLayout());
        setBackground(BG);
        buildUI();

        // Listen for execution to highlight current C line
        ExecutionTracker.getInstance().addListener(new ExecutionTracker.ExecutionListener() {
            public void instructionExecuted(int addr, int cLine, ProgramStatement stmt) {
                if (cLine > 0) highlightExecLine(cLine);
            }
            public void programAssembled()  { clearExecHighlight(); }
            public void simulationReset()   { clearExecHighlight(); }
        });
    }

    // ── UI Construction ────────────────────────────────────────────────
    private void buildUI() {
        add(buildToolbar(), BorderLayout.NORTH);
        buildEditorArea();
        buildConsole();
        add(mainSplit, BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
        insertDefaultCode();
    }

    private JToolBar buildToolbar() {
        JToolBar tb = new JToolBar();
        tb.setBackground(new Color(0x313147));
        tb.setFloatable(false);
        tb.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_CLR));

        compileBtn = makeBtn("[C] Compile", ACCENT,      e -> doCompile());
        runBtn     = makeBtn("[>] Run",     SUCCESS_CLR,  e -> doCompileAndRun());
        stepBtn    = makeBtn("[->] Step",    new Color(0xFAB387), e -> doStep());
        pauseBtn   = makeBtn("[||] Pause",   new Color(0xF9E2AF), e -> doPause());
        stopBtn    = makeBtn("[X] Stop",    ERROR_CLR,    e -> doStop());
        resetBtn   = makeBtn("[R] Reset",   new Color(0x89DCEB), e -> doReset());
        openBtn    = makeBtn("[O] Open",   TEXT_COLOR,   e -> doOpen());
        saveBtn    = makeBtn("[S] Save",   TEXT_COLOR,   e -> doSave());

        tb.add(openBtn);   tb.add(saveBtn);
        tb.addSeparator();
        tb.add(compileBtn); tb.add(runBtn);
        tb.addSeparator();
        tb.add(stepBtn); tb.add(pauseBtn); tb.add(stopBtn); tb.add(resetBtn);
        return tb;
    }

    private JButton makeBtn(String text, Color fg, ActionListener al) {
        JButton b = new JButton(text);
        b.setForeground(fg);
        b.setBackground(new Color(0x313147));
        b.setFont(new Font("SansSerif", Font.BOLD, 12));
        b.setFocusPainted(false);
        b.setBorderPainted(true);
        b.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_CLR, 1),
            BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        b.addActionListener(al);
        b.addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) { b.setBackground(new Color(0x45475A)); }
            public void mouseExited(MouseEvent e)  { b.setBackground(new Color(0x313147)); }
        });
        return b;
    }

    private void buildEditorArea() {
        // --- Editor ---
        editorPane = new JTextPane(new DefaultStyledDocument());
        editorPane.setBackground(EDITOR_BG);
        editorPane.setForeground(TEXT_COLOR);
        editorPane.setCaretColor(TEXT_COLOR);
        editorPane.setFont(new Font("Monospaced", Font.PLAIN, 14));
        editorPane.setMargin(new Insets(4, 8, 4, 8));

        // Syntax highlighting on document change
        editorPane.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { scheduleSyntaxHighlight(); }
            public void removeUpdate(DocumentEvent e)  { scheduleSyntaxHighlight(); }
            public void changedUpdate(DocumentEvent e) {}
        });

        // --- Gutter (line numbers) ---
        gutterPanel = new LineNumberPanel();
        gutterPanel.setPreferredSize(new Dimension(45, 0));

        JScrollPane editorScroll = new JScrollPane(editorPane);
        editorScroll.setRowHeaderView(gutterPanel);
        editorScroll.setBackground(EDITOR_BG);
        editorScroll.getViewport().setBackground(EDITOR_BG);
        editorScroll.setBorder(BorderFactory.createEmptyBorder());

        // --- Console ---
        consoleArea = new JTextArea(5, 0);
        consoleArea.setBackground(CONSOLE_BG);
        consoleArea.setForeground(TEXT_COLOR);
        consoleArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        consoleArea.setEditable(false);
        consoleArea.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

        JScrollPane consoleScroll = new JScrollPane(consoleArea);
        consoleScroll.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_CLR));

        mainSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, editorScroll, consoleScroll);
        mainSplit.setResizeWeight(0.80);
        mainSplit.setOneTouchExpandable(true);
        mainSplit.setDividerSize(6);
        mainSplit.setBackground(BG);
    }

    private void buildConsole() { /* console already built in buildEditorArea */ }

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(new Color(0x313147));
        bar.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_CLR));
        statusLabel = new JLabel("  Ready - write C code and click Compile");
        statusLabel.setForeground(TEXT_COLOR);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
        statusLabel.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
        bar.add(statusLabel, BorderLayout.CENTER);
        return bar;
    }

    // ── Default code ───────────────────────────────────────────────────
    private void insertDefaultCode() {
        String sample =
            "#include <stdio.h>\n\n" +
            "int factorial(int n) {\n" +
            "    if (n <= 1) return 1;\n" +
            "    return n * factorial(n - 1);\n" +
            "}\n\n" +
            "int main() {\n" +
            "    int result = factorial(5);\n" +
            "    return result;\n" +
            "}\n";
        editorPane.setText(sample);
        SwingUtilities.invokeLater(this::doSyntaxHighlight);
    }

    // ── Actions ────────────────────────────────────────────────────────

    private void doCompile() {
        String src = editorPane.getText();
        if (src.trim().isEmpty()) { setStatus("Editor is empty.", ERROR_CLR); return; }

        cancelCurrentRunner();
        setStatus("Compiling...", ACCENT);
        clearConsole();

        CCompilerConfig config = CCompilerConfig.getInstance();
        currentRunner = new CompilerRunner(src, config, new CompilerRunner.CompileCallback() {
            public void onProgress(String msg) { appendConsole(msg + "\n", TEXT_COLOR); }
            public void onSuccess(CompilerOutput out, String asmPath) {
                appendConsole("\n[OK] Compilation succeeded in " + out.getCompilationTimeMs() + " ms\n", SUCCESS_CLR);
                appendConsole("  Output: " + asmPath + "\n", TEXT_COLOR);
                setStatus("Compiled OK - " + out.getCompilationTimeMs() + " ms", SUCCESS_CLR);
                if (parent != null) parent.onCompileSuccess(out, asmPath);
            }
            public void onFailure(CompilerOutput out) {
                appendConsole("\n[ERR] Compilation FAILED\n", ERROR_CLR);
                appendConsole(out.getFormattedErrorReport(), ERROR_CLR);
                setStatus("Compilation failed. See console.", ERROR_CLR);
            }
        });
        currentRunner.execute();
    }

    private void doCompileAndRun() {
        doCompile();
        // Auto-run is triggered via parent callback after compile succeeds
        if (parent != null) parent.setAutoRunOnCompile(true);
    }

    private void doStep() {
        if (parent != null) parent.requestStep();
    }

    private void doPause() {
        if (parent != null) parent.requestPause();
    }

    private void doStop() {
        cancelCurrentRunner();
        if (parent != null) parent.requestStop();
    }

    private void doReset() {
        cancelCurrentRunner();
        clearExecHighlight();
        if (parent != null) parent.requestReset();
        setStatus("Reset.", TEXT_COLOR);
    }

    private void doOpen() {
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Open C Source File");
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("C source (*.c)", "c"));
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            try {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line).append("\n");
                }
                editorPane.setText(sb.toString());
                currentFile = f;
                setStatus("Opened: " + f.getName(), SUCCESS_CLR);
                SwingUtilities.invokeLater(this::doSyntaxHighlight);
            } catch (IOException ex) {
                setStatus("Error opening file: " + ex.getMessage(), ERROR_CLR);
            }
        }
    }

    private void doSave() {
        File f = currentFile;
        if (f == null) {
            JFileChooser fc = new JFileChooser();
            fc.setDialogTitle("Save C Source File");
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("C source (*.c)", "c"));
            if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
            f = fc.getSelectedFile();
            if (!f.getName().endsWith(".c")) f = new File(f.getAbsolutePath() + ".c");
            currentFile = f;
        }
        try (PrintWriter pw = new PrintWriter(new FileWriter(f))) {
            pw.print(editorPane.getText());
            setStatus("Saved: " + f.getName(), SUCCESS_CLR);
        } catch (IOException ex) {
            setStatus("Error saving: " + ex.getMessage(), ERROR_CLR);
        }
    }

    // ── Execution highlight ────────────────────────────────────────────

    public void highlightExecLine(int lineNumber) {
        currentExecLine = lineNumber;
        gutterPanel.repaint();
        // Scroll editor to that line
        try {
            int lineStart = getLineStart(lineNumber);
            editorPane.setCaretPosition(lineStart);
            editorPane.scrollRectToVisible(editorPane.modelToView(lineStart));
        } catch (Exception ignored) {}
    }

    public void clearExecHighlight() {
        currentExecLine = -1;
        gutterPanel.repaint();
    }

    private int getLineStart(int lineNum) throws BadLocationException {
        String text = editorPane.getText();
        int count = 1;
        for (int i = 0; i < text.length(); i++) {
            if (count == lineNum) return i;
            if (text.charAt(i) == '\n') count++;
        }
        return 0;
    }

    // ── Syntax Highlighting ────────────────────────────────────────────

    private javax.swing.Timer syntaxTimer;

    private void scheduleSyntaxHighlight() {
        if (highlightingInProgress) return;
        if (syntaxTimer != null && syntaxTimer.isRunning()) syntaxTimer.stop();
        syntaxTimer = new javax.swing.Timer(300, e -> doSyntaxHighlight());
        syntaxTimer.setRepeats(false);
        syntaxTimer.start();
    }

    private void doSyntaxHighlight() {
        if (highlightingInProgress) return;
        highlightingInProgress = true;
        String text = editorPane.getText();
        StyledDocument doc = editorPane.getStyledDocument();

        // Base style
        Style base = doc.addStyle("base", null);
        StyleConstants.setForeground(base, TEXT_COLOR);
        StyleConstants.setFontFamily(base, "Monospaced");
        StyleConstants.setFontSize(base, 14);
        doc.setCharacterAttributes(0, text.length(), base, true);

        // Apply specific patterns
        applyPattern(doc, text, "//[^\n]*",           COMMENT_CLR, false, false);
        applyPattern(doc, text, "/\\*[\\s\\S]*?\\*/", COMMENT_CLR, false, false);
        applyPattern(doc, text, "\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\"", STRING_CLR, false, false);
        applyPattern(doc, text, "'[^'\\\\](?:\\\\.)??'", STRING_CLR, false, false);
        applyPattern(doc, text, "\\b\\d+\\.?\\d*[fFdDlL]?\\b", NUMBER_CLR, false, false);
        applyPattern(doc, text, "\\b0x[0-9a-fA-F]+\\b", NUMBER_CLR, false, false);
        applyKeywords(doc, text);

        highlightingInProgress = false;
        gutterPanel.repaint();
    }

    private void applyPattern(StyledDocument doc, String text,
                               String regex, Color color,
                               boolean bold, boolean italic) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex, java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher m = p.matcher(text);
        Style s = doc.addStyle("pat_" + color.hashCode(), null);
        StyleConstants.setForeground(s, color);
        StyleConstants.setBold(s, bold);
        StyleConstants.setItalic(s, italic);
        StyleConstants.setFontFamily(s, "Monospaced");
        StyleConstants.setFontSize(s, 14);
        while (m.find()) {
            doc.setCharacterAttributes(m.start(), m.end() - m.start(), s, false);
        }
    }

    private void applyKeywords(StyledDocument doc, String text) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b");
        java.util.regex.Matcher m = p.matcher(text);
        while (m.find()) {
            String word = m.group(1);
            if (KEYWORDS.contains(word)) {
                Style s = doc.addStyle("kw", null);
                StyleConstants.setForeground(s, KEYWORD_CLR);
                StyleConstants.setBold(s, true);
                StyleConstants.setFontFamily(s, "Monospaced");
                StyleConstants.setFontSize(s, 14);
                doc.setCharacterAttributes(m.start(), m.end() - m.start(), s, false);
            }
        }
    }

    // ── Console helpers ────────────────────────────────────────────────

    private void clearConsole() {
        consoleArea.setText("");
    }

    private void appendConsole(String text, Color color) {
        consoleArea.append(text);
        consoleArea.setCaretPosition(consoleArea.getDocument().getLength());
    }

    private void setStatus(String msg, Color color) {
        statusLabel.setText("  " + msg);
        statusLabel.setForeground(color);
    }

    // ── Misc ───────────────────────────────────────────────────────────

    public String getCSourceCode() { return editorPane.getText(); }

    private void cancelCurrentRunner() {
        if (currentRunner != null && !currentRunner.isDone()) {
            currentRunner.cancelCompile();
        }
    }

    // ── Line Number Gutter ─────────────────────────────────────────────

    private class LineNumberPanel extends JPanel {
        LineNumberPanel() {
            setBackground(GUTTER_BG);
            setFont(new Font("Monospaced", Font.PLAIN, 13));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setFont(getFont());
            FontMetrics fm  = g2.getFontMetrics();
            int lineH = editorPane.getFont().getSize() + 4;
            String text = editorPane.getText();
            String[] lines = text.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                int lineNum = i + 1;
                int y = (i * lineH) + fm.getAscent() + editorPane.getInsets().top + 4;
                // Highlight current exec line
                if (lineNum == currentExecLine) {
                    g2.setColor(new Color(0xF9E2AF, false));
                    g2.fillRect(0, y - fm.getAscent(), getWidth(), lineH);
                    g2.setColor(new Color(0xF9E2AF));
                    g2.drawString("→", 2, y);
                }
                g2.setColor(GUTTER_TEXT);
                String numStr = String.valueOf(lineNum);
                int x = getWidth() - fm.stringWidth(numStr) - 6;
                g2.drawString(numStr, x, y);
            }
        }
    }
}
