package mars.ccompiler;

import mars.ccompiler.CompilerOutput.CompilerError;

import javax.swing.*;
import java.io.File;
import java.util.List;

/*
 * CompilerRunner — executes CCompiler on a background SwingWorker so the MARS
 * GUI stays responsive during compilation.
 *
 * Usage:
 *   CompilerRunner runner = new CompilerRunner(sourceCode, config, callback);
 *   runner.execute();        // starts background compilation
 *   runner.cancelCompile();  // can be called from EDT to abort
 *
 * Author: MARS C Extension
 */
public class CompilerRunner extends SwingWorker<CompilerOutput, String> {

    /** Callback interface — all methods called on the Swing EDT. */
    public interface CompileCallback {
        /** Called when compilation successfully produces a .asm file. */
        void onSuccess(CompilerOutput output, String cleanedAsmPath);

        /** Called when compilation fails or the compiler is not found. */
        void onFailure(CompilerOutput output);

        /** Called periodically with a status message during compilation. */
        void onProgress(String message);
    }

    private final String         cSourceCode;
    private final CCompilerConfig config;
    private final CompileCallback callback;

    public CompilerRunner(String cSourceCode, CCompilerConfig config,
                          CompileCallback callback) {
        this.cSourceCode = cSourceCode;
        this.config      = config;
        this.callback    = callback;
    }

    /** Cancel the running compilation. */
    public void cancelCompile() {
        cancel(true);
    }

    // ── SwingWorker implementation ─────────────────────────────────────

    @Override
    protected CompilerOutput doInBackground() throws Exception {
        publish("Resolving compiler...");

        String compilerExe = CCompiler.resolveCompiler(config);
        if (compilerExe == null) {
            publish("No external MIPS cross-compiler found.");
            publish("Falling back to built-in C-to-MIPS transpiler...");
        } else {
            publish("Compiling with: " + compilerExe + " ...");
        }

        CompilerOutput result = CCompiler.compile(cSourceCode, config);

        if (result.isSuccess()) {
            publish("Compilation complete. Cleaning assembly for MARS...");
        } else {
            publish("Compilation FAILED. Check errors below.");
        }

        return result;
    }

    @Override
    protected void process(List<String> chunks) {
        // Called on EDT with each publish() message
        for (String msg : chunks) {
            callback.onProgress(msg);
        }
    }

    @Override
    protected void done() {
        if (isCancelled()) {
            callback.onProgress("Compilation cancelled.");
            return;
        }
        try {
            CompilerOutput result = get();
            if (result.isSuccess()) {
                // Verify the generated .asm file exists
                File asmFile = new File(result.getGeneratedAsmPath());
                if (asmFile.exists()) {
                    callback.onSuccess(result, result.getGeneratedAsmPath());
                } else {
                    result.setStatusMessage("Generated .asm file not found: " +
                                            result.getGeneratedAsmPath());
                    callback.onFailure(result);
                }
            } else {
                callback.onFailure(result);
            }
        } catch (Exception e) {
            // Build a synthetic failure output
            CompilerOutput failure = CompilerOutput.failure(
                null, "", "Internal error: " + e.getMessage(),
                -1, 0L, new java.util.ArrayList<CompilerError>(),
                "Internal error during compilation.");
            callback.onFailure(failure);
        }
    }
}
