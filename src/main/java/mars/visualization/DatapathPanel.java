package mars.visualization;

import mars.ProgramStatement;
import mars.insightx.Instruction;
import mars.mapping.ExecutionTracker;
import mars.visualization.ExecutionAnimator.AnimationTarget;
import mars.visualization.ExecutionAnimator.InstructionType;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.*;
import java.util.*;

/**
 * DatapathPanel — custom Graphics2D animated MIPS single-cycle datapath visualization.
 * Renders the classic datapath with glowing components, highlighted buses, 
 * and traveling signal pulse animations.
 */
public class DatapathPanel extends JPanel
    implements ExecutionTracker.ExecutionListener, AnimationTarget {

    // ── Design System (Vibrant & Dark theme) ─────────────────────────────────
    private static final Color BG           = new Color(0x0E0E1B);
    private static final Color COMPONENT_BG = new Color(0x1B1B2F);
    private static final Color COMPONENT_BD = new Color(0x3E3E5C);
    private static final Color TEXT_COLOR   = new Color(0xE2E8F0);
    private static final Color WIRE_COLOR   = new Color(0x2A2B3D);
    private static final Color WIRE_ACTIVE  = new Color(0x38BDF8); // Cyan/Sky blue
    private static final Color PULSE_COLOR  = new Color(0xFBBF24); // Glowing Gold

    // Component definition
    private static class DPComp {
        String name, label;
        int x, y, w, h;
        boolean active;
        Color activeColor;
        DPComp(String n, String l, int x, int y, int w, int h) {
            name = n; label = l; this.x = x; this.y = y; this.w = w; this.h = h;
        }
    }

    private final Map<String, DPComp> comps = new LinkedHashMap<>();
    private InstructionType currentType = InstructionType.OTHER;
    private float animProgress = 1.0f; // from ExecutionAnimator fade-in

    // Pulse animation state (continuous)
    private float pulseT = 0f;
    private javax.swing.Timer pulseTimer;

    // Optional labels for manual selection mode
    private String instrLabel = "";
    private String signalLabel = "";

    // Active component maps
    private static final Map<InstructionType, String[]> ACTIVE_MAP;
    static {
        ACTIVE_MAP = new EnumMap<>(InstructionType.class);
        ACTIVE_MAP.put(InstructionType.ARITHMETIC, new String[]{"pc","imem","regfile","alu","wb"});
        ACTIVE_MAP.put(InstructionType.LOAD,       new String[]{"pc","imem","regfile","alu","dmem","wb","signext"});
        ACTIVE_MAP.put(InstructionType.STORE,      new String[]{"pc","imem","regfile","alu","dmem","signext"});
        ACTIVE_MAP.put(InstructionType.BRANCH,     new String[]{"pc","imem","regfile","alu","control","adder","signext"});
        ACTIVE_MAP.put(InstructionType.JUMP,       new String[]{"pc","imem","control"});
        ACTIVE_MAP.put(InstructionType.SYSCALL,    new String[]{"pc","imem","control"});
        ACTIVE_MAP.put(InstructionType.OTHER,      new String[]{"pc","imem"});
    }

    public DatapathPanel() {
        setBackground(BG);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        setPreferredSize(new Dimension(840, 420));
        
        // Define blocks
        reg("pc",      "PC",           15,  170, 60,  60);
        reg("imem",    "Instruction\nMemory", 95, 130, 100, 140);
        reg("control", "Control\nUnit", 215,  20,  90,  80);
        reg("regfile", "Register\nFile", 225, 130, 110, 140);
        reg("signext", "Sign\nExt",     225, 300,  80,  50);
        reg("alu",     "ALU",           385, 150,  80, 110);
        reg("adder",   "Branch\nAdder", 385,  50,  70,  60);
        reg("dmem",    "Data\nMemory",  515, 130, 100, 140);
        reg("wb",      "WB\nMux",       675, 170,  55,  80);

        // Register listeners
        ExecutionTracker.getInstance().addListener(this);
        ExecutionAnimator.getInstance().addTarget(this);

        // Build continuous pulse timer (30 fps)
        pulseTimer = new javax.swing.Timer(33, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                pulseT += 0.02f;
                if (pulseT > 1.0f) pulseT = 0f;
                repaint();
            }
        });
    }

    private void reg(String n, String l, int x, int y, int w, int h) {
        comps.put(n, new DPComp(n, l, x, y, w, h));
    }

    /**
     * Set instruction manually for standalone mode.
     */
    public void setSelectedInstruction(Instruction instr) {
        if (instr == null) {
            currentType = InstructionType.OTHER;
            instrLabel = "";
            signalLabel = "";
        } else {
            currentType = ExecutionAnimator.classify(instr.mnemonic);
            instrLabel = instr.display;
            switch (currentType) {
                case ARITHMETIC: signalLabel = "Arithmetic/R-type: ALU computes result, writes to Register File."; break;
                case LOAD:       signalLabel = "Load: address computed, memory read, loaded value written to Register File."; break;
                case STORE:      signalLabel = "Store: address computed, source register value written to Data Memory."; break;
                case BRANCH:     signalLabel = "Branch: registers compared in ALU, PC updated based on branch condition."; break;
                case JUMP:       signalLabel = "Jump: PC updated immediately from instruction immediate/target."; break;
                case SYSCALL:    signalLabel = "Syscall: execution control transferred to operating system."; break;
                default:         signalLabel = "Other: instruction executed."; break;
            }
        }
        setActive(currentType);
        repaint();
    }

    // Lifecycle hooks to start/stop animations automatically
    @Override
    public void addNotify() {
        super.addNotify();
        if (pulseTimer != null) pulseTimer.start();
    }

    @Override
    public void removeNotify() {
        super.removeNotify();
        if (pulseTimer != null) pulseTimer.stop();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        int pw = getWidth() - 16, ph = getHeight() - 16;
        g2.translate(8, 8);
        g2.scale(pw / 760.0, ph / 380.0);

        // 1. Draw Wires and pulses
        drawWires(g2);
        
        // 2. Draw components
        for (DPComp c : comps.values()) {
            drawComp(g2, c);
        }
        
        // 3. Draw labels/status
        drawStatus(g2);
        
        g2.dispose();
    }

    private void drawComp(Graphics2D g2, DPComp c) {
        Color borderCol = COMPONENT_BD;
        Color bg = COMPONENT_BG;

        if (c.active) {
            Color actCol = c.activeColor != null ? c.activeColor : WIRE_ACTIVE;
            borderCol = WIRE_ACTIVE;
            
            // Calculate intensity curve
            float intensity;
            if (animProgress < 0.3f) {
                intensity = animProgress / 0.3f;
            } else if (animProgress < 0.7f) {
                intensity = 1.0f;
            } else {
                intensity = 1.0f - (animProgress - 0.7f) / 0.3f;
            }
            intensity = Math.max(0f, Math.min(1f, intensity));
            
            // Create a subtle active color mix (25% active, 75% dark background)
            int r = (int) (actCol.getRed()   * 0.25f + COMPONENT_BG.getRed()   * 0.75f);
            int g = (int) (actCol.getGreen() * 0.25f + COMPONENT_BG.getGreen() * 0.75f);
            int b = (int) (actCol.getBlue()  * 0.25f + COMPONENT_BG.getBlue()  * 0.75f);
            Color targetBg = new Color(r, g, b);
            
            // Fade between targetBg and COMPONENT_BG based on progress intensity
            int fr = (int) (targetBg.getRed()   * intensity + COMPONENT_BG.getRed()   * (1f - intensity));
            int fg = (int) (targetBg.getGreen() * intensity + COMPONENT_BG.getGreen() * (1f - intensity));
            int fb = (int) (targetBg.getBlue()  * intensity + COMPONENT_BG.getBlue()  * (1f - intensity));
            bg = new Color(fr, fg, fb);
        }

        // Drop shadow
        g2.setColor(new Color(0, 0, 0, 100));
        g2.fillRoundRect(c.x + 3, c.y + 3, c.w, c.h, 12, 12);

        // Component body
        g2.setColor(bg);
        g2.fillRoundRect(c.x, c.y, c.w, c.h, 12, 12);

        // Component border (glow if active)
        g2.setColor(borderCol);
        g2.setStroke(new BasicStroke(c.active ? 2.2f : 1.2f));
        g2.drawRoundRect(c.x, c.y, c.w, c.h, 12, 12);

        // Text labels
        g2.setColor(TEXT_COLOR);
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        FontMetrics fm = g2.getFontMetrics();
        String[] lines = c.label.split("\n");
        int totalH = lines.length * fm.getHeight();
        int startY = c.y + (c.h - totalH) / 2 + fm.getAscent();
        for (String line : lines) {
            int tw = fm.stringWidth(line);
            g2.drawString(line, c.x + (c.w - tw) / 2, startY);
            startY += fm.getHeight();
        }
    }

    private void drawWires(Graphics2D g2) {
        g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        
        // Setup wires
        wire(g2, rx("pc"),    my("pc",0.5),    lx("imem"),   my("imem",0.5),  act("pc","imem"));
        wire(g2, rx("imem"),  my("imem",0.35), lx("regfile"),my("regfile",0.4),act("imem","regfile"));
        wire(g2, rx("imem"),  my("imem",0.2),  lx("control"),my("control",0.5),act("imem","control"));
        wire(g2, rx("regfile"),my("regfile",0.3),lx("alu"),  my("alu",0.3),   act("regfile","alu"));
        wire(g2, rx("regfile"),my("regfile",0.7),lx("alu"),  my("alu",0.7),   act("regfile","alu"));
        wire(g2, rx("signext"),my("signext",0.5),lx("alu"),  my("alu",0.8),   act("signext","alu"));
        wire(g2, rx("alu"),   my("alu",0.5),   lx("dmem"),  my("dmem",0.4),  act("alu","dmem"));
        wire(g2, rx("dmem"),  my("dmem",0.4),  lx("wb"),    my("wb",0.3),    act("dmem","wb"));
        wire(g2, rx("alu"),   my("alu",0.25),  lx("adder"), my("adder",0.7), act("alu","adder"));
        wire(g2, rx("imem"),  my("imem",0.85), lx("signext"),my("signext",0.5),act("imem","signext"));

        // WB feedback arc
        boolean feedbackActive = act("wb", "regfile");
        g2.setColor(feedbackActive ? WIRE_ACTIVE : WIRE_COLOR);
        int wbx = rx("wb"); 
        int rfy = my("regfile",0.15);
        int wby = my("wb", 0.5);
        
        g2.drawLine(wbx, wby, wbx+20, wby);
        g2.drawLine(wbx+20, wby, wbx+20, 38);
        g2.drawLine(wbx+20, 38, lx("regfile")-15, 38);
        g2.drawLine(lx("regfile")-15, 38, lx("regfile")-15, rfy);
        g2.drawLine(lx("regfile")-15, rfy, lx("regfile"), rfy);

        if (feedbackActive) {
            drawPulseOnArc(g2, wbx, wby, rfy, pulseT);
        }
    }

    private void wire(Graphics2D g2, int x1, int y1, int x2, int y2, boolean active) {
        g2.setColor(active ? WIRE_ACTIVE : WIRE_COLOR);
        int mx = (x1 + x2) / 2;
        if (x1 == x2 || y1 == y2) {
            g2.drawLine(x1, y1, x2, y2);
        } else {
            g2.drawLine(x1, y1, mx, y1);
            g2.drawLine(mx, y1, mx, y2);
            g2.drawLine(mx, y2, x2, y2);
        }

        // Draw animated traveling pulse
        if (active) {
            drawPulse(g2, x1, y1, x2, y2, pulseT);
            drawPulse(g2, x1, y1, x2, y2, (pulseT + 0.5f) % 1.0f); // dual pulses
        }
    }

    private void drawPulse(Graphics2D g2, int x1, int y1, int x2, int y2, float t) {
        int px, py;
        if (x1 == x2) {
            px = x1;
            py = y1 + (int)(t * (y2 - y1));
        } else if (y1 == y2) {
            px = x1 + (int)(t * (x2 - x1));
            py = y1;
        } else {
            int mx = (x1 + x2) / 2;
            int l1 = Math.abs(mx - x1);
            int l2 = Math.abs(y2 - y1);
            int l3 = Math.abs(x2 - mx);
            int total = l1 + l2 + l3;
            int d = (int)(t * total);

            if (d < l1) {
                px = x1 + (mx > x1 ? d : -d);
                py = y1;
            } else if (d < l1 + l2) {
                px = mx;
                py = y1 + (y2 > y1 ? (d - l1) : -(d - l1));
            } else {
                int rem = d - l1 - l2;
                px = mx + (x2 > mx ? rem : -rem);
                py = y2;
            }
        }

        // Glow circle
        g2.setColor(new Color(PULSE_COLOR.getRed(), PULSE_COLOR.getGreen(), PULSE_COLOR.getBlue(), 60));
        g2.fillOval(px - 6, py - 6, 12, 12);
        g2.setColor(PULSE_COLOR);
        g2.fillOval(px - 3, py - 3, 6, 6);
    }

    private void drawPulseOnArc(Graphics2D g2, int wbx, int wby, int rfy, float t) {
        int wby_mid = wby;
        int l1 = 20;
        int l2 = Math.abs(38 - wby_mid);
        int l3 = Math.abs((lx("regfile") - 15) - (wbx + 20));
        int l4 = Math.abs(rfy - 38);
        int l5 = 15;
        int total = l1 + l2 + l3 + l4 + l5;
        int d = (int)(t * total);

        int px, py;
        if (d < l1) {
            px = wbx + d;
            py = wby_mid;
        } else if (d < l1 + l2) {
            px = wbx + 20;
            py = wby_mid + (38 > wby_mid ? (d - l1) : -(d - l1));
        } else if (d < l1 + l2 + l3) {
            px = (wbx + 20) - (d - l1 - l2);
            py = 38;
        } else if (d < l1 + l2 + l3 + l4) {
            px = lx("regfile") - 15;
            py = 38 + (rfy > 38 ? (d - l1 - l2 - l3) : -(d - l1 - l2 - l3));
        } else {
            px = (lx("regfile") - 15) + (d - l1 - l2 - l3 - l4);
            py = rfy;
        }

        g2.setColor(new Color(PULSE_COLOR.getRed(), PULSE_COLOR.getGreen(), PULSE_COLOR.getBlue(), 60));
        g2.fillOval(px - 6, py - 6, 12, 12);
        g2.setColor(PULSE_COLOR);
        g2.fillOval(px - 3, py - 3, 6, 6);
    }

    private void drawStatus(Graphics2D g2) {
        // Status bar container
        g2.setColor(new Color(0x06060F));
        g2.fillRect(0, 345, 760, 35);
        g2.setColor(COMPONENT_BD);
        g2.drawRect(0, 345, 760, 35);

        // Labels
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.setColor(ExecutionAnimator.getTypeColor(currentType));
        String typeLabel = currentType.name();
        if (!instrLabel.isEmpty()) {
            typeLabel += " (" + instrLabel + ")";
        }
        g2.drawString("Active: " + typeLabel, 12, 367);

        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        g2.setColor(TEXT_COLOR);
        g2.drawString(signalLabel, 260, 367);
    }

    private int lx(String n)              { return comps.get(n).x; }
    private int rx(String n)              { return comps.get(n).x + comps.get(n).w; }
    private int my(String n, double f)    { DPComp c=comps.get(n); return c.y+(int)(c.h*f); }

    private boolean act(String a, String b) {
        DPComp ca=comps.get(a), cb=comps.get(b);
        return ca!=null && cb!=null && ca.active && cb.active;
    }

    private void setActive(InstructionType type) {
        for (DPComp c : comps.values()) { c.active=false; c.activeColor=null; }
        String[] active = ACTIVE_MAP.get(type);
        if (active == null) return;
        Color col = ExecutionAnimator.getTypeColor(type);
        for (String n : active) {
            DPComp c = comps.get(n);
            if (c != null) { c.active=true; c.activeColor=col; }
        }
    }

    // ── Execution Tracker Listeners ──────────────────────────────────────────

    @Override
    public void instructionExecuted(int addr, int cLine, ProgramStatement stmt) {
        String mn = (stmt!=null&&stmt.getInstruction()!=null) ? stmt.getInstruction().getName() : "";
        currentType = ExecutionAnimator.classify(mn);
        setActive(currentType);
        
        // Setup details text
        instrLabel = (stmt != null) ? stmt.getSource().trim() : mn;
        switch (currentType) {
            case ARITHMETIC: signalLabel = "ALU computed result -> WB mux selects ALU -> writes to Register File."; break;
            case LOAD:       signalLabel = "ALU computes memory address -> Memory read -> WB mux selects Data Memory -> writes to Register File."; break;
            case STORE:      signalLabel = "ALU computes memory address -> writes Register File value to Data Memory."; break;
            case BRANCH:     signalLabel = "ALU compares registers -> computes branch target -> updates PC."; break;
            case JUMP:       signalLabel = "Control unit extracts jump target -> updates PC immediately."; break;
            case SYSCALL:    signalLabel = "Syscall triggered -> processor yields execution control to OS."; break;
            default:         signalLabel = "Default instruction step."; break;
        }

        animProgress = 0f;
        ExecutionAnimator.getInstance().startAnimation(currentType);
        repaint();
    }

    @Override
    public void programAssembled() {
        currentType = InstructionType.OTHER;
        instrLabel = "";
        signalLabel = "";
        for (DPComp c : comps.values()) { c.active=false; c.activeColor=null; }
        repaint();
    }

    @Override
    public void simulationReset() { programAssembled(); }

    @Override
    public void animationFrame(float progress) {
        this.animProgress = progress;
        repaint();
    }
}
