package mars.ccompiler;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/*
 * CSourceCleaner — post-processes raw GCC/Clang MIPS assembly output into
 * MARS-compatible assembly.
 *
 * GCC emits various GAS-specific directives that MARS does not understand.
 * This class strips or rewrites those directives while preserving the
 * meaningful instruction and data sections.
 *
 * It also adds a MARS-compatible exit syscall wrapper if main() returns
 * via `jr $ra` without a prior syscall 10.
 *
 * Author: MARS C Extension
 */
public class CSourceCleaner {

    // Directives that are safe to drop entirely for MARS
    private static final String[] DROP_PREFIXES = {
        ".option",      // RISC-V/other arches, sometimes emitted
        ".module",      // MIPS module specifiers
        ".gnu_attribute",
        ".ident",
        ".frame",       // MIPS ABI frame descriptor (debuginfo only)
        ".mask",        // register save mask (debuginfo only)
        ".fmask",       // float register save mask
        ".cprestore",   // PIC $gp save
        ".cpload",      // PIC $gp setup
        ".cfi_",        // call-frame info (dwarf)
        "#APP",         // asm inline markers
        "#NO_APP",
        ".nan",         // NaN encoding
        ".abicalls",    // PIC ABI marker  ← also strip
    };

    // Lines that can be kept/rewritten
    private static final String[] KEEP_DIRECTIVES = {
        ".text",
        ".data",
        ".globl",
        ".global",
        ".align",
        ".word",
        ".half",
        ".byte",
        ".space",
        ".ascii",
        ".asciiz",
        ".set",         // handled specially below
        ".type",        // strip the GAS ELF type but keep label
    };

    /**
     * Clean {@code rawAsm} (the full text of a GCC/Clang .s file) and return
     * MARS-compatible assembly text.
     *
     * @param rawAsm     raw assembly text from compiler
     * @param sourceFile original .c filename (used in comments)
     */
    public static String clean(String rawAsm, String sourceFile) {
        String[] lines = rawAsm.split("\n", -1);
        List<String> out = new ArrayList<String>();

        // Header comment
        out.add("# Auto-generated MIPS assembly from: " + sourceFile);
        out.add("# Cleaned for MARS compatibility by CSourceCleaner");
        out.add("");

        boolean inTextSection = false;
        boolean addedExitSyscall = false;

        for (String rawLine : lines) {
            String line = rawLine; // preserve original indentation for instructions

            // Blank / comment-only pass-through
            if (line.trim().isEmpty() || line.trim().startsWith("#")) {
                out.add(line);
                continue;
            }

            String trimmed = line.trim();

            // ── Drop directives ────────────────────────────────────────
            if (shouldDrop(trimmed)) {
                continue;
            }

            // ── .set directive handling ────────────────────────────────
            // .set reorder / .set noreorder / .set macro / .set nomacro → drop
            if (trimmed.startsWith(".set")) {
                String rest = trimmed.substring(4).trim();
                if (rest.startsWith("reorder")  || rest.startsWith("noreorder") ||
                    rest.startsWith("macro")     || rest.startsWith("nomacro")   ||
                    rest.startsWith("noat")      || rest.startsWith("at")) {
                    continue; // drop
                }
                // Other .set directives: pass through (e.g., .set gp=64)
            }

            // ── .type directive ────────────────────────────────────────
            // GAS:  .type main, @function  → MARS doesn't need it; drop
            if (trimmed.startsWith(".type")) {
                continue;
            }

            // ── .size directive ────────────────────────────────────────
            if (trimmed.startsWith(".size")) {
                continue;
            }

            // ── .loc debugging info ────────────────────────────────────
            // Keep .loc lines — SourceMapper reads them to build C↔MIPS mapping
            // Format:  .loc <file-index> <line> [<column>]
            // Pass through as comments so MARS ignores but SourceMapper can read
            if (trimmed.startsWith(".loc ")) {
                out.add("# " + trimmed);   // comment-ified .loc
                continue;
            }

            // ── .file directive ────────────────────────────────────────
            if (trimmed.startsWith(".file")) {
                out.add("# " + trimmed);   // keep as comment for SourceMapper
                continue;
            }

            // ── Section markers ────────────────────────────────────────
            if (trimmed.startsWith(".text")) {
                inTextSection = true;
                out.add("\t.text");
                continue;
            }
            if (trimmed.startsWith(".data")) {
                inTextSection = false;
                out.add("\t.data");
                continue;
            }

            // ── Insert MARS exit syscall before end of main ────────────
            // GCC typically ends main with:  jr $ra  or  jr $31
            // We intercept that and add syscall 10 first.
            if (!addedExitSyscall && inTextSection &&
                (trimmed.equals("jr\t$ra") || trimmed.equals("jr $ra") ||
                 trimmed.equals("jr\t$31") || trimmed.equals("jr $31"))) {
                // Add exit syscall before the jr (MARS convention)
                out.add("\t# MARS exit syscall injected by CSourceCleaner");
                out.add("\tli\t$v0, 10");
                out.add("\tsyscall");
                addedExitSyscall = true;
                // Still emit the jr $ra in case it's inside a sub-function
            }

            out.add(line);
        }

        // Safety net: if we never saw a jr $ra (rare), append exit at end of .text
        if (!addedExitSyscall) {
            out.add("");
            out.add("\t# MARS exit syscall appended by CSourceCleaner");
            out.add("\tli\t$v0, 10");
            out.add("\tsyscall");
        }

        StringBuilder sb = new StringBuilder();
        for (String l : out) {
            sb.append(l).append("\n");
        }
        return sb.toString();
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private static boolean shouldDrop(String trimmedLine) {
        for (String prefix : DROP_PREFIXES) {
            if (trimmedLine.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Write the cleaned assembly to a file.
     *
     * @param cleanedAsm cleaned assembly text
     * @param outputPath target .asm file path
     * @throws IOException if writing fails
     */
    public static void writeToFile(String cleanedAsm, String outputPath) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(outputPath))) {
            pw.print(cleanedAsm);
        }
    }
}
