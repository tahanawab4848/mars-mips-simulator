package mars.ccompiler;

import java.util.prefs.Preferences;

/*
 * CCompilerConfig — stores and persists compiler settings for the C-to-MIPS integration.
 *
 * Settings are stored using Java Preferences API (backed by the OS registry on Windows,
 * ~/.java on Linux/macOS) so they survive across MARS sessions without modifying MARS's
 * own Settings infrastructure.
 *
 * Author: MARS C Extension
 */
public class CCompilerConfig {

    /** Compiler backend choices */
    public enum CompilerType {
        GCC("mips-linux-gnu-gcc"),
        CLANG("clang"),
        AUTO_DETECT("auto");

        private final String executable;

        CompilerType(String exe) { this.executable = exe; }

        public String getExecutable() { return executable; }
    }

    // ── Preference keys ────────────────────────────────────────────────
    private static final String PREF_NODE       = "mars/ccompiler";
    private static final String KEY_TYPE        = "compilerType";
    private static final String KEY_PATH        = "compilerPath";
    private static final String KEY_FLAGS       = "extraFlags";
    private static final String KEY_TEMP_DIR    = "tempDir";
    private static final String KEY_TIMEOUT     = "timeoutSeconds";

    // ── Defaults ───────────────────────────────────────────────────────
    private static final String DEFAULT_FLAGS   = "-O0 -mips32r2 -mabi=32 -g";
    private static final int    DEFAULT_TIMEOUT = 30;

    // ── Instance fields ────────────────────────────────────────────────
    private CompilerType compilerType;
    private String       compilerPath;   // explicit path; empty = resolve from PATH
    private String       extraFlags;
    private String       tempDir;
    private int          timeoutSeconds;

    // ── Singleton ──────────────────────────────────────────────────────
    private static CCompilerConfig instance;

    private CCompilerConfig() {
        load();
    }

    /** Returns the shared instance, loading from persistent storage on first call. */
    public static synchronized CCompilerConfig getInstance() {
        if (instance == null) {
            instance = new CCompilerConfig();
        }
        return instance;
    }

    // ── Persistence ────────────────────────────────────────────────────

    /** Load settings from Java Preferences. */
    public void load() {
        Preferences prefs = Preferences.userRoot().node(PREF_NODE);
        String typeName = prefs.get(KEY_TYPE, CompilerType.AUTO_DETECT.name());
        try {
            compilerType = CompilerType.valueOf(typeName);
        } catch (IllegalArgumentException e) {
            compilerType = CompilerType.AUTO_DETECT;
        }
        compilerPath   = prefs.get(KEY_PATH,     "");
        extraFlags     = prefs.get(KEY_FLAGS,    DEFAULT_FLAGS);
        tempDir        = prefs.get(KEY_TEMP_DIR, System.getProperty("java.io.tmpdir"));
        timeoutSeconds = prefs.getInt(KEY_TIMEOUT, DEFAULT_TIMEOUT);
    }

    /** Persist current settings. */
    public void save() {
        try {
            Preferences prefs = Preferences.userRoot().node(PREF_NODE);
            prefs.put(KEY_TYPE,     compilerType.name());
            prefs.put(KEY_PATH,     compilerPath);
            prefs.put(KEY_FLAGS,    extraFlags);
            prefs.put(KEY_TEMP_DIR, tempDir);
            prefs.putInt(KEY_TIMEOUT, timeoutSeconds);
            prefs.flush();
        } catch (Exception e) {
            System.err.println("[CCompilerConfig] Could not save preferences: " + e.getMessage());
        }
    }

    // ── Accessors ──────────────────────────────────────────────────────

    public CompilerType getCompilerType()           { return compilerType; }
    public void setCompilerType(CompilerType t)     { this.compilerType = t; }

    public String getCompilerPath()                 { return compilerPath; }
    public void setCompilerPath(String p)           { this.compilerPath = p; }

    public String getExtraFlags()                   { return extraFlags; }
    public void setExtraFlags(String f)             { this.extraFlags = f; }

    public String getTempDir()                      { return tempDir; }
    public void setTempDir(String d)                { this.tempDir = d; }

    public int getTimeoutSeconds()                  { return timeoutSeconds; }
    public void setTimeoutSeconds(int t)            { this.timeoutSeconds = t; }

    /**
     * Returns true if the config is in AUTO_DETECT mode and no explicit path is set.
     */
    public boolean isAutoDetect() {
        return compilerType == CompilerType.AUTO_DETECT ||
               (compilerPath == null || compilerPath.trim().isEmpty());
    }
}
