package mars.ccompiler;

import mars.ccompiler.CompilerOutput.CompilerError;
import mars.ccompiler.builtin.*;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.TimeUnit;

/*
 * CCompiler — core compilation engine.
 *
 * Workflow:
 *   1. Write C source to a temp file.
 *   2. Detect or use configured compiler.
 *   3. Execute compiler via ProcessBuilder.
 *   4. Capture stdout / stderr with timeout.
 *   5. On success, invoke CSourceCleaner to produce a MARS-compatible .asm.
 *   6. Return a CompilerOutput describing the result.
 *
 * Author: MARS C Extension
 */
public class CCompiler {

    // Regex to parse GCC/Clang error lines:
    //   filename:line:col: error: message
    //   filename:line:col: warning: message
    private static final Pattern ERROR_PATTERN =
        Pattern.compile("^(.+?):(\\d+):(\\d+):\\s+(error|warning):\\s+(.+)$");

    // Candidates for auto-detection on PATH
    private static final String[] GCC_CANDIDATES = {
        "mips-linux-gnu-gcc",
        "mips-elf-gcc",
        "mips64-linux-gnu-gcc"
    };
    private static final String[] CLANG_CANDIDATES = {
        "clang",
        "clang-14", "clang-15", "clang-16", "clang-17"
    };

    // ── Public API ─────────────────────────────────────────────────────

    /**
     * Compile the given C source code using the provided configuration.
     *
     * @param cSourceCode    C source as a String
     * @param config         compiler settings
     * @return               a CompilerOutput describing success or failure
     */
    public static CompilerOutput compile(String cSourceCode, CCompilerConfig config) {
        long startTime = System.currentTimeMillis();

        // 1. Resolve compiler executable
        String compilerPath = resolveCompiler(config);
        if (compilerPath == null) {
            // No external compiler found — use built-in transpiler
            try {
                CLexer lexer = new CLexer(cSourceCode);
                List<CToken> tokens = lexer.tokenize();
                CParser parser = new CParser(tokens);
                CASTNode.Program prog = parser.parse();
                CCodeGen cg = new CCodeGen(prog);
                String asm = cg.generate();
                
                File tempDir = new File(config.getTempDir());
                if (!tempDir.exists()) tempDir.mkdirs();
                File asmFile = new File(tempDir, "mars_compiled.s");
                try (PrintWriter pw = new PrintWriter(new FileWriter(asmFile))) {
                    pw.print(asm);
                }
                long timeMs = System.currentTimeMillis() - startTime;
                return CompilerOutput.success("", asmFile.getAbsolutePath(), "", "", timeMs);
            } catch (Exception e) {
                return CompilerOutput.failure(null, "", 
                    "Built-in transpiler failed:\n" + e.getMessage() + "\n\n" +
                    "To use full C features, install mips-linux-gnu-gcc or clang with MIPS support.",
                    -1, System.currentTimeMillis() - startTime, new ArrayList<CompilerError>(),
                    "Built-in transpiler error.");
            }
        }

        // 2. Write source to temp file
        File cFile;
        File asmFile;
        File marsAsmFile;
        try {
            String tempDir = config.getTempDir();
            cFile    = File.createTempFile("mars_csrc_", ".c",   new File(tempDir));
            asmFile  = new File(tempDir, cFile.getName().replace(".c", ".s"));
            marsAsmFile = new File(tempDir, cFile.getName().replace(".c", ".asm"));

            try (PrintWriter pw = new PrintWriter(new FileWriter(cFile))) {
                pw.print(cSourceCode);
            }
        } catch (IOException e) {
            return CompilerOutput.failure(null, "", "Failed to create temp file: " + e.getMessage(),
                -1, 0L, new ArrayList<CompilerError>(),
                "IO Error: " + e.getMessage());
        }

        // 3. Build compiler command
        List<String> cmd = buildCommand(compilerPath, config, cFile.getAbsolutePath(),
                                        asmFile.getAbsolutePath());

        // 4. Execute
        String stdout = "";
        String stderr = "";
        int exitCode  = -1;
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(false);
            pb.directory(new File(config.getTempDir()));

            Process proc = pb.start();

            // Capture stdout and stderr in separate threads to avoid blocking
            StringBuffer sbOut = new StringBuffer();
            StringBuffer sbErr = new StringBuffer();
            Thread tOut = captureStream(proc.getInputStream(), sbOut);
            Thread tErr = captureStream(proc.getErrorStream(), sbErr);
            tOut.start();
            tErr.start();

            long timeoutMs = config.getTimeoutSeconds() * 1000L;
            long deadline  = System.currentTimeMillis() + timeoutMs;

            // Wait for process with timeout
            boolean finished = false;
            while (System.currentTimeMillis() < deadline) {
                try {
                    exitCode = proc.exitValue(); // throws if not done
                    finished = true;
                    break;
                } catch (IllegalThreadStateException ex) {
                    Thread.sleep(50);
                }
            }
            if (!finished) {
                proc.destroy();
                return CompilerOutput.failure(cFile.getAbsolutePath(), "", 
                    "Compilation timed out after " + config.getTimeoutSeconds() + " seconds.",
                    -1, System.currentTimeMillis() - startTime,
                    new ArrayList<CompilerError>(),
                    "Compilation timed out.");
            }

            tOut.join(500);
            tErr.join(500);
            stdout = sbOut.toString();
            stderr = sbErr.toString();

        } catch (Exception e) {
            return CompilerOutput.failure(cFile.getAbsolutePath(), "", 
                "Failed to execute compiler: " + e.getMessage(),
                -1, System.currentTimeMillis() - startTime,
                new ArrayList<CompilerError>(),
                "Execution error: " + e.getMessage());
        }

        long elapsed = System.currentTimeMillis() - startTime;

        // 5. Handle failure
        if (exitCode != 0 || !asmFile.exists()) {
            List<CompilerError> errors = parseErrors(stderr, cFile.getName());
            return CompilerOutput.failure(cFile.getAbsolutePath(), stdout, stderr,
                exitCode, elapsed, errors,
                "Compilation failed (exit code " + exitCode + ").");
        }

        // 6. Clean the assembly and write .asm
        try {
            String rawAsm = readFile(asmFile);
            String cleaned = CSourceCleaner.clean(rawAsm, cFile.getName());
            CSourceCleaner.writeToFile(cleaned, marsAsmFile.getAbsolutePath());
        } catch (IOException e) {
            return CompilerOutput.failure(cFile.getAbsolutePath(), stdout, stderr,
                exitCode, elapsed, new ArrayList<CompilerError>(),
                "Failed to clean assembly: " + e.getMessage());
        }

        return CompilerOutput.success(cFile.getAbsolutePath(),
            marsAsmFile.getAbsolutePath(), stdout, stderr, elapsed);
    }

    // ── Auto-detect compiler ───────────────────────────────────────────

    /**
     * Returns the resolved compiler executable string (may include full path),
     * or null if none found.
     */
    public static String resolveCompiler(CCompilerConfig config) {
        // If an explicit path is configured, use it
        if (!config.isAutoDetect()) {
            String path = config.getCompilerPath().trim();
            if (!path.isEmpty()) {
                return path;
            }
        }

        // Try GCC candidates first
        for (String candidate : GCC_CANDIDATES) {
            if (isOnPath(candidate)) {
                return candidate;
            }
        }
        // Try Clang candidates
        for (String candidate : CLANG_CANDIDATES) {
            if (isOnPath(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    /** Returns true if the given executable can be found on the system PATH. */
    public static boolean isOnPath(String executable) {
        try {
            ProcessBuilder pb = new ProcessBuilder(executable, "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            // Drain output
            InputStream is = p.getInputStream();
            byte[] buf = new byte[512];
            while (is.read(buf) != -1) {}
            int code = p.waitFor();
            return true; // if we got here without exception, it exists
        } catch (Exception e) {
            return false;
        }
    }

    // ── Command builder ────────────────────────────────────────────────

    private static List<String> buildCommand(String compiler, CCompilerConfig config,
                                              String inputFile, String outputFile) {
        List<String> cmd = new ArrayList<String>();
        cmd.add(compiler);
        cmd.add("-S");          // output assembly, not object code
        cmd.add("-g");          // generate debug info (.loc directives)

        // Add extra flags from config (split on whitespace)
        String flags = config.getExtraFlags().trim();
        if (!flags.isEmpty()) {
            for (String flag : flags.split("\\s+")) {
                if (!flag.isEmpty()) cmd.add(flag);
            }
        }

        // For clang, add the MIPS target triple
        if (compiler.contains("clang")) {
            // Only add target if not already in extra flags
            if (!flags.contains("-target")) {
                cmd.add("-target");
                cmd.add("mips-linux-gnu");
            }
        }

        cmd.add("-o");
        cmd.add(outputFile);
        cmd.add(inputFile);
        return cmd;
    }

    // ── Error parsing ──────────────────────────────────────────────────

    private static List<CompilerError> parseErrors(String stderr, String sourceFilename) {
        List<CompilerError> errors = new ArrayList<CompilerError>();
        if (stderr == null || stderr.isEmpty()) return errors;

        for (String line : stderr.split("\n")) {
            Matcher m = ERROR_PATTERN.matcher(line.trim());
            if (m.matches()) {
                int lineNo = 0;
                int colNo  = 0;
                try { lineNo = Integer.parseInt(m.group(2)); } catch (NumberFormatException ignored) {}
                try { colNo  = Integer.parseInt(m.group(3)); } catch (NumberFormatException ignored) {}
                boolean isWarn = "warning".equalsIgnoreCase(m.group(4));
                errors.add(new CompilerError(m.group(1), lineNo, colNo, m.group(5), isWarn));
            }
        }
        return errors;
    }

    // ── Utilities ──────────────────────────────────────────────────────

    private static Thread captureStream(final InputStream is, final StringBuffer sb) {
        return new Thread(new Runnable() {
            public void run() {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                } catch (IOException ignored) {}
            }
        });
    }

    private static String readFile(File f) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }
}
