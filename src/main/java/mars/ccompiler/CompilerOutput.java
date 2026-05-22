package mars.ccompiler;

import java.util.ArrayList;
import java.util.List;

/*
 * CompilerOutput — data object representing the result of one compilation attempt.
 *
 * On success:  success==true, generatedAsmPath points to cleaned .asm file.
 * On failure:  success==false, errorMessages list populated from stderr parsing.
 *
 * Author: MARS C Extension
 */
public class CompilerOutput {

    // ── Inner type: per-error record ───────────────────────────────────

    /** One compiler error or warning message with source location. */
    public static class CompilerError {
        private final String  filename;
        private final int     line;      // 1-based; 0 if unknown
        private final int     column;    // 1-based; 0 if unknown
        private final String  message;
        private final boolean isWarning;

        public CompilerError(String filename, int line, int column,
                             String message, boolean isWarning) {
            this.filename  = filename;
            this.line      = line;
            this.column    = column;
            this.message   = message;
            this.isWarning = isWarning;
        }

        public String  getFilename()  { return filename; }
        public int     getLine()      { return line; }
        public int     getColumn()    { return column; }
        public String  getMessage()   { return message; }
        public boolean isWarning()    { return isWarning; }

        @Override
        public String toString() {
            String prefix = isWarning ? "Warning" : "Error";
            if (line > 0) {
                return prefix + " (line " + line + (column > 0 ? ", col " + column : "") + "): " + message;
            }
            return prefix + ": " + message;
        }
    }

    // ── Fields ─────────────────────────────────────────────────────────

    private final boolean     success;
    private final String      generatedAsmPath;   // valid only when success==true
    private final String      rawCPath;            // temp .c file written
    private final String      stdout;
    private final String      stderr;
    private final int         exitCode;
    private final long        compilationTimeMs;
    private final List<CompilerError> errorMessages;
    private String            statusMessage;      // user-friendly summary

    // ── Factory constructors ───────────────────────────────────────────

    /** Build a successful result. */
    public static CompilerOutput success(String cPath, String asmPath,
                                         String stdout, String stderr,
                                         long timeMs) {
        return new CompilerOutput(true, cPath, asmPath, stdout, stderr, 0, timeMs,
                                  new ArrayList<CompilerError>(),
                                  "Compilation succeeded in " + timeMs + " ms.");
    }

    /** Build a failure result. */
    public static CompilerOutput failure(String cPath, String stdout, String stderr,
                                          int exitCode, long timeMs,
                                          List<CompilerError> errors,
                                          String statusMessage) {
        return new CompilerOutput(false, cPath, null, stdout, stderr, exitCode, timeMs,
                                  errors, statusMessage);
    }

    // ── Private constructor ────────────────────────────────────────────

    private CompilerOutput(boolean success, String cPath, String asmPath,
                           String stdout, String stderr, int exitCode, long timeMs,
                           List<CompilerError> errors, String statusMessage) {
        this.success          = success;
        this.rawCPath         = cPath;
        this.generatedAsmPath = asmPath;
        this.stdout           = stdout;
        this.stderr           = stderr;
        this.exitCode         = exitCode;
        this.compilationTimeMs = timeMs;
        this.errorMessages    = errors;
        this.statusMessage    = statusMessage;
    }

    // ── Accessors ──────────────────────────────────────────────────────

    public boolean isSuccess()                          { return success; }
    public String  getGeneratedAsmPath()                { return generatedAsmPath; }
    public String  getRawCPath()                        { return rawCPath; }
    public String  getStdout()                          { return stdout; }
    public String  getStderr()                          { return stderr; }
    public int     getExitCode()                        { return exitCode; }
    public long    getCompilationTimeMs()               { return compilationTimeMs; }
    public List<CompilerError> getErrorMessages()       { return errorMessages; }
    public String  getStatusMessage()                   { return statusMessage; }

    public void setStatusMessage(String msg)            { this.statusMessage = msg; }

    /**
     * Returns a formatted summary of all errors suitable for display in the console.
     */
    public String getFormattedErrorReport() {
        if (errorMessages.isEmpty() && !stderr.isEmpty()) {
            return stderr;
        }
        StringBuilder sb = new StringBuilder();
        for (CompilerError err : errorMessages) {
            sb.append(err.toString()).append("\n");
        }
        if (!stderr.isEmpty() && errorMessages.isEmpty()) {
            sb.append(stderr);
        }
        return sb.toString();
    }
}
