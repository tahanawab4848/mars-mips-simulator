package mars.mapping;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/*
 * SourceMapper — parses GCC/Clang debug .loc directives from the CLEANED assembly
 * (stored as comments: "# .loc <fileIdx> <line> <col>") to build a bidirectional
 * map between C source line numbers and MIPS instruction addresses.
 *
 * The mapping is built from the .asm text BEFORE assembly runs in MARS.
 * After assembly, real addresses are determined by iterating the assembled
 * ProgramStatement list and correlating with the line-number sequence.
 *
 * Author: MARS C Extension
 */
public class SourceMapper {

    // Matches:  # .loc 1 <line> [col] ...
    private static final Pattern LOC_PATTERN =
        Pattern.compile("^#\\s+\\.loc\\s+\\d+\\s+(\\d+)(?:\\s+\\d+)?.*$");

    // Matches a MIPS instruction line (starts with whitespace + opcode)
    // used when counting instruction lines post-compilation
    private static final Pattern INSTR_PATTERN =
        Pattern.compile("^\\s+[a-zA-Z].*");

    // Maps C source line  → list of MIPS assembly line numbers (1-based)
    private final Map<Integer, List<Integer>> cLineToAsmLines;

    // Maps MIPS assembly line number → C source line number
    private final Map<Integer, Integer> asmLineToCLine;

    // After loading into MARS: maps MIPS PC address → C source line
    private final Map<Integer, Integer> addressToCLine;

    // Reverse: C source line → list of MIPS PC addresses
    private final Map<Integer, List<Integer>> cLineToAddresses;

    private boolean mappingBuilt = false;

    public SourceMapper() {
        cLineToAsmLines  = new LinkedHashMap<Integer, List<Integer>>();
        asmLineToCLine   = new LinkedHashMap<Integer, Integer>();
        addressToCLine   = new LinkedHashMap<Integer, Integer>();
        cLineToAddresses = new LinkedHashMap<Integer, List<Integer>>();
    }

    // ── Phase 1: parse .loc comments from .asm file ───────────────────

    /**
     * Parse the cleaned .asm file text and populate the C-line → asm-line maps.
     * Call this BEFORE MARS assembles the file.
     *
     * @param asmText full text of the cleaned .asm file
     */
    public void parseAsmText(String asmText) {
        cLineToAsmLines.clear();
        asmLineToCLine.clear();

        String[] lines = asmText.split("\n", -1);
        int currentCLine = -1;
        int asmLineNum   = 0;

        for (String line : lines) {
            asmLineNum++;
            Matcher m = LOC_PATTERN.matcher(line.trim());
            if (m.matches()) {
                currentCLine = Integer.parseInt(m.group(1));
                continue;
            }
            // If this is an instruction line and we have a current C line, record it
            if (currentCLine > 0 && INSTR_PATTERN.matcher(line).matches()
                    && !line.trim().startsWith("#")) {
                // Record the asm line → C line
                asmLineToCLine.put(asmLineNum, currentCLine);
                List<Integer> list = cLineToAsmLines.get(currentCLine);
                if (list == null) {
                    list = new ArrayList<Integer>();
                    cLineToAsmLines.put(currentCLine, list);
                }
                list.add(asmLineNum);
            }
        }
    }

    // ── Phase 2: correlate asm lines with MARS ProgramStatement addresses ─

    /**
     * After MARS has assembled the program, call this with the ordered list of
     * PC addresses (one per instruction, in program order) to build the final
     * address↔C-line maps.
     *
     * @param instructionAddresses ordered list of MIPS PC addresses from MARS assembler
     * @param firstInstructionAsmLine asm text line number of the first instruction
     *        in the .text section (usually the line just after ".text" or ".globl main")
     */
    public void correlateAddresses(List<Integer> instructionAddresses, int firstInstructionAsmLine) {
        addressToCLine.clear();
        cLineToAddresses.clear();

        // Build a sorted list of (asmLine → cLine) pairs for lookup
        List<int[]> sorted = new ArrayList<int[]>(); // [asmLine, cLine]
        for (Map.Entry<Integer, Integer> entry : asmLineToCLine.entrySet()) {
            sorted.add(new int[]{ entry.getKey(), entry.getValue() });
        }
        Collections.sort(sorted, new Comparator<int[]>() {
            public int compare(int[] a, int[] b) { return a[0] - b[0]; }
        });

        // Walk the instruction address list and assign C lines
        // by matching each instruction (indexed from firstInstructionAsmLine) to
        // the nearest .loc entry
        int sortedIdx = 0;
        int currentCLine = -1;
        // Compute a relative offset counter for instructions
        int instrCount = 0;

        for (int addr : instructionAddresses) {
            instrCount++;
            int approxAsmLine = firstInstructionAsmLine + instrCount; // rough estimate

            // Advance the sorted .loc pointer while the loc line is before our current position
            while (sortedIdx < sorted.size() - 1 &&
                   sorted.get(sortedIdx + 1)[0] <= approxAsmLine) {
                sortedIdx++;
            }
            if (sortedIdx < sorted.size()) {
                currentCLine = sorted.get(sortedIdx)[1];
            }

            if (currentCLine > 0) {
                addressToCLine.put(addr, currentCLine);
                List<Integer> addrs = cLineToAddresses.get(currentCLine);
                if (addrs == null) {
                    addrs = new ArrayList<Integer>();
                    cLineToAddresses.put(currentCLine, addrs);
                }
                addrs.add(addr);
            }
        }

        mappingBuilt = !addressToCLine.isEmpty();
    }

    // ── Query API ─────────────────────────────────────────────────────

    /**
     * Returns the C source line number for a given MIPS instruction address,
     * or -1 if no mapping exists.
     */
    public int getCLineForAddress(int mipsAddress) {
        Integer cLine = addressToCLine.get(mipsAddress);
        return (cLine != null) ? cLine : -1;
    }

    /**
     * Returns the list of MIPS instruction addresses for a given C source line,
     * or an empty list if no mapping exists.
     */
    public List<Integer> getAddressesForCLine(int cLine) {
        List<Integer> result = cLineToAddresses.get(cLine);
        return (result != null) ? result : Collections.<Integer>emptyList();
    }

    /** Returns true if the address↔line map has been populated. */
    public boolean isMappingBuilt() { return mappingBuilt; }

    /** Resets all mapping data (call when a new file is opened). */
    public void reset() {
        cLineToAsmLines.clear();
        asmLineToCLine.clear();
        addressToCLine.clear();
        cLineToAddresses.clear();
        mappingBuilt = false;
    }
}
