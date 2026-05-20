package mars.insightx;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * MipsParser — reads a MIPS .asm file and extracts a supported instruction subset.
 *
 * Supported mnemonics: add, sub, and, or, nor, slt, addi, lw, sw, beq, bne, j, jal, jr, nop
 * Everything else (directives, labels, .data section, comments, blanks) is skipped silently.
 */
public class MipsParser {

    // ── Supported instruction table ──────────────────────────────────────────
    // Format: mnemonic -> (type, dest_field, src1_field, src2_field)
    //   field index: 0=rd, 1=rs, 2=rt, 3=none
    private static final Map<String, InstrDef> SUPPORTED = new LinkedHashMap<String, InstrDef>();

    static {
        // R-type: op rd, rs, rt
        SUPPORTED.put("add",  new InstrDef(Instruction.Type.R,        0, 1, 2));
        SUPPORTED.put("sub",  new InstrDef(Instruction.Type.R,        0, 1, 2));
        SUPPORTED.put("and",  new InstrDef(Instruction.Type.R,        0, 1, 2));
        SUPPORTED.put("or",   new InstrDef(Instruction.Type.R,        0, 1, 2));
        SUPPORTED.put("nor",  new InstrDef(Instruction.Type.R,        0, 1, 2));
        SUPPORTED.put("slt",  new InstrDef(Instruction.Type.R,        0, 1, 2));
        SUPPORTED.put("sll",  new InstrDef(Instruction.Type.R,        0, 2, -1)); // rd, rt, shamt
        SUPPORTED.put("srl",  new InstrDef(Instruction.Type.R,        0, 2, -1));
        SUPPORTED.put("sra",  new InstrDef(Instruction.Type.R,        0, 2, -1));
        SUPPORTED.put("mul",  new InstrDef(Instruction.Type.R,        0, 1, 2));
        // I-type arithmetic: op rt, rs, imm  (dest=rt, src=rs)
        SUPPORTED.put("addi", new InstrDef(Instruction.Type.R,        1, 0, -1));
        SUPPORTED.put("andi", new InstrDef(Instruction.Type.R,        1, 0, -1));
        SUPPORTED.put("ori",  new InstrDef(Instruction.Type.R,        1, 0, -1));
        SUPPORTED.put("slti", new InstrDef(Instruction.Type.R,        1, 0, -1));
        SUPPORTED.put("li",   new InstrDef(Instruction.Type.R,        0, -1,-1));
        SUPPORTED.put("lui",  new InstrDef(Instruction.Type.R,        0, -1,-1));
        SUPPORTED.put("move", new InstrDef(Instruction.Type.R,        0, 1, -1));
        // Load: lw rt, offset(rs)  (dest=rt, src=rs)
        SUPPORTED.put("lw",   new InstrDef(Instruction.Type.I_LOAD,   1, 0, -1));
        SUPPORTED.put("lb",   new InstrDef(Instruction.Type.I_LOAD,   1, 0, -1));
        SUPPORTED.put("lh",   new InstrDef(Instruction.Type.I_LOAD,   1, 0, -1));
        // Store: sw rt, offset(rs)  (no dest, src=rs,rt)
        SUPPORTED.put("sw",   new InstrDef(Instruction.Type.I_STORE, -1, 0, 1));
        SUPPORTED.put("sb",   new InstrDef(Instruction.Type.I_STORE, -1, 0, 1));
        SUPPORTED.put("sh",   new InstrDef(Instruction.Type.I_STORE, -1, 0, 1));
        // Branch: beq rs, rt, label  (no dest, src=rs,rt)
        SUPPORTED.put("beq",  new InstrDef(Instruction.Type.I_BRANCH,-1, 0, 1));
        SUPPORTED.put("bne",  new InstrDef(Instruction.Type.I_BRANCH,-1, 0, 1));
        SUPPORTED.put("blt",  new InstrDef(Instruction.Type.I_BRANCH,-1, 0, 1));
        SUPPORTED.put("bgt",  new InstrDef(Instruction.Type.I_BRANCH,-1, 0, 1));
        SUPPORTED.put("ble",  new InstrDef(Instruction.Type.I_BRANCH,-1, 0, 1));
        SUPPORTED.put("bge",  new InstrDef(Instruction.Type.I_BRANCH,-1, 0, 1));
        SUPPORTED.put("bltz", new InstrDef(Instruction.Type.I_BRANCH,-1, 0,-1));
        SUPPORTED.put("bgtz", new InstrDef(Instruction.Type.I_BRANCH,-1, 0,-1));
        // Jump: j / jal label
        SUPPORTED.put("j",    new InstrDef(Instruction.Type.J,       -1,-1,-1));
        SUPPORTED.put("jal",  new InstrDef(Instruction.Type.J,       31,-1,-1)); // writes $ra=31
        SUPPORTED.put("jr",   new InstrDef(Instruction.Type.J,       -1, 0,-1));
        SUPPORTED.put("jalr", new InstrDef(Instruction.Type.J,       31, 0,-1));
        // NOP
        SUPPORTED.put("nop",  new InstrDef(Instruction.Type.NOP,     -1,-1,-1));
        SUPPORTED.put("syscall", new InstrDef(Instruction.Type.NOP,  -1,-1,-1));
    }

    // ── Regex helpers ────────────────────────────────────────────────────────

    // Matches   "reg, reg, reg"   or   "reg, offset(reg)"   operand lists
    private static final Pattern REG_PAT    = Pattern.compile("\\$?([a-zA-Z0-9]+)");
    private static final Pattern OFFSET_PAT = Pattern.compile("[-]?\\d*\\s*\\(\\s*(\\$?[a-zA-Z0-9]+)\\s*\\)");

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Parse the given file and return a list of recognized instructions.
     */
    public static List<Instruction> parse(File file) throws IOException {
        List<Instruction> result = new ArrayList<Instruction>();
        boolean inData    = false;
        boolean inText    = false;
        boolean hasSection = false;

        BufferedReader br = new BufferedReader(new FileReader(file));
        try {
            String line;
            while ((line = br.readLine()) != null) {
                // Strip comments
                int commentIdx = line.indexOf('#');
                if (commentIdx >= 0) line = line.substring(0, commentIdx);
                line = line.trim();
                if (line.isEmpty()) continue;

                // Section directives
                if (line.equals(".data") || line.startsWith(".data ")) { inData = true;  inText = false; hasSection = true; continue; }
                if (line.equals(".text") || line.startsWith(".text ")) { inData = false; inText = true;  hasSection = true; continue; }

                // Skip .data section entirely
                if (inData) continue;

                // If no sections declared, treat everything as .text
                if (hasSection && !inText) continue;

                // Skip labels (lines ending with ':' possibly with code after)
                // Remove inline label prefix if present
                if (line.contains(":")) {
                    int colon = line.indexOf(':');
                    line = line.substring(colon + 1).trim();
                    if (line.isEmpty()) continue;
                }

                // Skip assembler directives
                if (line.startsWith(".")) continue;

                // Parse the mnemonic and operands
                Instruction instr = parseLine(line);
                if (instr != null) result.add(instr);
            }
        } finally {
            br.close();
        }
        return result;
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private static Instruction parseLine(String line) {
        // Split into mnemonic + operand string
        String[] parts = line.split("\\s+", 2);
        String mnemonic = parts[0].toLowerCase();
        String operands = parts.length > 1 ? parts[1].trim() : "";

        InstrDef def = SUPPORTED.get(mnemonic);
        if (def == null) return null;

        // Tokenize registers from operands
        // Handle offset(reg) notation: extract the base register
        String baseReg = "";
        Matcher offM = OFFSET_PAT.matcher(operands);
        if (offM.find()) {
            baseReg = offM.group(1);
            // Replace offset(...) with just the base reg token for uniform splitting
            operands = operands.substring(0, offM.start()) + " " + baseReg + operands.substring(offM.end());
        }

        // Extract all register tokens
        List<String> regs = new ArrayList<String>();
        Matcher regM = REG_PAT.matcher(operands);
        while (regM.find()) {
            String tok = regM.group(1);
            // Skip pure numeric tokens (immediates, shamt, offsets)
            if (!tok.matches("\\d+")) regs.add(tok);
        }

        String dest = pick(regs, def.destIdx);
        String src1 = pick(regs, def.src1Idx);
        String src2 = pick(regs, def.src2Idx);

        // Special handling for jal: dest is $ra (register 31)
        if ("jal".equals(mnemonic)) dest = "ra";

        return new Instruction(mnemonic, def.type, dest, src1, src2, line);
    }

    private static String pick(List<String> regs, int idx) {
        if (idx < 0 || idx >= regs.size()) return "";
        return regs.get(idx);
    }

    // ── InstrDef helper ──────────────────────────────────────────────────────

    private static class InstrDef {
        final Instruction.Type type;
        final int destIdx, src1Idx, src2Idx; // indices into parsed register token list; -1 = none
        InstrDef(Instruction.Type t, int d, int s1, int s2) {
            type = t; destIdx = d; src1Idx = s1; src2Idx = s2;
        }
    }
}
