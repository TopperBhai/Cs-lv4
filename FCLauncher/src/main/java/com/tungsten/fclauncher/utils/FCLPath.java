package com.tungsten.fclauncher.utils;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.File;

public class FCLPath {

    // =========================================================
    // Constants
    // =========================================================
    private static final String TAG = "FCLPath";

    // ✅ CS Launcher Branding
    private static final String LAUNCHER_FOLDER = "CSLauncher";

    // =========================================================
    // Context
    // =========================================================
    public static Context CONTEXT;

    // =========================================================
    // System Paths
    // =========================================================
    public static String NATIVE_LIB_DIR;

    // =========================================================
    // Cache & Log Paths
    // =========================================================
    public static String LOG_DIR;
    public static String CACHE_DIR;

    // ✅ NEW: Temp dir for optimization
    public static String TEMP_DIR;
    public static String CRASH_DIR;

    // =========================================================
    // Runtime Paths
    // =========================================================
    public static String RUNTIME_DIR;
    public static String MOD_RUNTIME_DIR;

    // Java Versions
    public static String JAVA_PATH;
    public static String JAVA_8_PATH;
    public static String JAVA_17_PATH;
    public static String JAVA_21_PATH;
    public static String JAVA_25_PATH;

    // Libraries
    public static String JNA_PATH;
    public static String LWJGL_DIR;
    public static String CACIOCAVALLO_8_DIR;
    public static String CACIOCAVALLO_17_DIR;

    // =========================================================
    // App File Paths
    // =========================================================
    public static String FILES_DIR;
    public static String PLUGIN_DIR;
    public static String BACKGROUND_DIR;
    public static String CONTROLLER_DIR;

    // ✅ NEW: Performance & Config dirs
    public static String CONFIG_DIR;
    public static String SHADER_DIR;

    // =========================================================
    // Minecraft Directories
    // =========================================================
    public static String PRIVATE_COMMON_DIR;

    // ✅ CS Launcher: Shared dir renamed
    public static String SHARED_COMMON_DIR =
        Environment.getExternalStorageDirectory()
            .getAbsolutePath() + "/" + LAUNCHER_FOLDER + "/.minecraft";

    // =========================================================
    // Plugin Paths
    // =========================================================
    public static String AUTHLIB_INJECTOR_PATH;
    public static String LIB_PATCHER_PATH;
    public static String MIO_LAUNCH_WRAPPER;

    // =========================================================
    // Background Paths
    // =========================================================
    public static String LT_BACKGROUND_PATH;
    public static String DK_BACKGROUND_PATH;
    public static String LIVE_BACKGROUND_PATH;

    // =========================================================
    // Init Flag
    // =========================================================
    private static volatile boolean initialized = false;

    // =========================================================
    // Load Paths - Main Entry Point
    // =========================================================

    /**
     * Sab paths initialize karo.
     * ✅ BOOST 1: Double-check init (Thread safe)
     */
    public static synchronized void loadPaths(Context context) {
        if (initialized) {
            Log.d(TAG, "Paths already initialized, skipping.");
            return;
        }

        CONTEXT = context.getApplicationContext();

        setupSystemPaths(context);
        setupRuntimePaths(context);
        setupAppPaths(context);
        setupMinecraftPaths(context);
        setupPluginPaths();
        setupBackgroundPaths();
        createDirectories();

        initialized = true;

        Log.i(TAG, "CS Launcher paths initialized successfully.");
        dumpPaths();
    }

    // =========================================================
    // Setup Helpers
    // =========================================================

    /**
     * System level paths.
     */
    private static void setupSystemPaths(Context context) {
        NATIVE_LIB_DIR = context.getApplicationInfo().nativeLibraryDir;

        // ✅ BOOST 2: Use app-specific cache (No storage permission needed)
        CACHE_DIR = context.getCacheDir().getAbsolutePath()
                  + "/fclauncher";

        // ✅ BOOST 3: Internal temp dir (Fast read/write)
        TEMP_DIR  = context.getCacheDir().getAbsolutePath()
                  + "/temp";

        // Crash reports
        CRASH_DIR = context.getFilesDir().getAbsolutePath()
                  + "/crashes";

        // Logs on external (easy access)
        LOG_DIR = Environment.getExternalStorageDirectory()
                    .getAbsolutePath()
                  + "/" + LAUNCHER_FOLDER + "/log";
    }

    /**
     * Java runtime paths.
     */
    private static void setupRuntimePaths(Context context) {
        // ✅ BOOST 4: Internal storage for runtime = faster access
        RUNTIME_DIR     = context.getDir("runtime", Context.MODE_PRIVATE)
                            .getAbsolutePath();
        MOD_RUNTIME_DIR = context.getDir("runtime_mod", Context.MODE_PRIVATE)
                            .getAbsolutePath();

        // Java versions
        JAVA_PATH       = RUNTIME_DIR + "/java";
        JAVA_8_PATH     = RUNTIME_DIR + "/java/jre8";
        JAVA_17_PATH    = RUNTIME_DIR + "/java/jre17";
        JAVA_21_PATH    = RUNTIME_DIR + "/java/jre21";
        JAVA_25_PATH    = RUNTIME_DIR + "/java/jre25";

        // Libraries
        JNA_PATH           = RUNTIME_DIR + "/jna";
        LWJGL_DIR          = RUNTIME_DIR + "/lwjgl";
        CACIOCAVALLO_8_DIR  = RUNTIME_DIR + "/caciocavallo";
        CACIOCAVALLO_17_DIR = RUNTIME_DIR + "/caciocavallo17";
    }

    /**
     * App internal file paths.
     */
    private static void setupAppPaths(Context context) {
        FILES_DIR      = context.getFilesDir().getAbsolutePath();
        PLUGIN_DIR     = FILES_DIR + "/plugins";
        BACKGROUND_DIR = FILES_DIR + "/background";

        // ✅ BOOST 5: Config dir for launcher settings
        CONFIG_DIR     = FILES_DIR + "/config";

        // ✅ BOOST 6: Shader dir for future shader support
        SHADER_DIR     = FILES_DIR + "/shaders";

        // Controller dir (External for user access)
        CONTROLLER_DIR = Environment.getExternalStorageDirectory()
                            .getAbsolutePath()
                         + "/" + LAUNCHER_FOLDER + "/control";
    }

    /**
     * Minecraft game directory paths.
     */
    private static void setupMinecraftPaths(Context context) {
        // Private .minecraft (App-specific external)
        File externalFilesDir = context.getExternalFilesDir(null);

        if (externalFilesDir == null) {
            // Fallback if external unavailable
            externalFilesDir = new File(
                Environment.getExternalStorageDirectory(),
                "Android/data/" + context.getPackageName() + "/files"
            );
            Log.w(TAG, "External files dir null, using fallback: "
                + externalFilesDir.getAbsolutePath());
        }

        PRIVATE_COMMON_DIR = new File(externalFilesDir, ".minecraft")
                                .getAbsolutePath();

        // Shared .minecraft (CS Launcher branded)
        SHARED_COMMON_DIR = Environment.getExternalStorageDirectory()
                                .getAbsolutePath()
                            + "/" + LAUNCHER_FOLDER + "/.minecraft";
    }

    /**
     * Plugin JAR paths.
     */
    private static void setupPluginPaths() {
        AUTHLIB_INJECTOR_PATH = PLUGIN_DIR + "/authlib-injector.jar";
        LIB_PATCHER_PATH      = PLUGIN_DIR + "/MioLibPatcher.jar";
        MIO_LAUNCH_WRAPPER    = PLUGIN_DIR + "/MioLaunchWrapper.jar";
    }

    /**
     * Background asset paths.
     */
    private static void setupBackgroundPaths() {
        LT_BACKGROUND_PATH   = BACKGROUND_DIR + "/lt.png";
        DK_BACKGROUND_PATH   = BACKGROUND_DIR + "/dk.png";
        LIVE_BACKGROUND_PATH = BACKGROUND_DIR + "/live.mp4";
    }

    // =========================================================
    // Directory Creation
    // =========================================================

    /**
     * Sab zaroori directories create karo.
     * ✅ BOOST 7: Grouped creation with logging
     */
    private static void createDirectories() {
        // Cache & Logs
        initDir(LOG_DIR);
        initDir(CACHE_DIR);
        initDir(TEMP_DIR);
        initDir(CRASH_DIR);

        // Runtime
        initDir(RUNTIME_DIR);
        initDir(MOD_RUNTIME_DIR);
        initDir(JAVA_8_PATH);
        initDir(JAVA_17_PATH);
        initDir(JAVA_21_PATH);
        initDir(JAVA_25_PATH);
        initDir(LWJGL_DIR);
        initDir(CACIOCAVALLO_8_DIR);
        initDir(CACIOCAVALLO_17_DIR);

        // App Files
        initDir(FILES_DIR);
        initDir(PLUGIN_DIR);
        initDir(BACKGROUND_DIR);
        initDir(CONFIG_DIR);
        initDir(SHADER_DIR);
        initDir(CONTROLLER_DIR);

        // Minecraft
        initDir(PRIVATE_COMMON_DIR);
        initDir(SHARED_COMMON_DIR);
    }

    /**
     * Directory safely create karo.
     * ✅ BOOST 8: Better logging + return value
     */
    private static boolean initDir(String path) {
        if (path == null || path.isEmpty()) {
            Log.w(TAG, "initDir: null or empty path skipped.");
            return false;
        }

        File dir = new File(path);

        if (dir.exists()) {
            if (!dir.isDirectory()) {
                Log.w(TAG, "Path exists but is not directory: " + path);
                return false;
            }
            return true;
        }

        boolean created = dir.mkdirs();
        if (created) {
            Log.d(TAG, "Created dir: " + path);
        } else {
            Log.e(TAG, "Failed to create dir: " + path);
        }
        return created;
    }

    // =========================================================
    // Utility Methods
    // =========================================================

    /**
     * Check karo ki path accessible hai.
     */
    public static boolean isPathAccessible(String path) {
        if (path == null) return false;
        File f = new File(path);
        return f.exists() && f.canRead() && f.canWrite();
    }

    /**
     * ✅ BOOST 9: Temp cache clear karo - RAM/Storage free karo
     * Game launch ke baad call karo.
     */
    public static void clearTempCache() {
        if (TEMP_DIR == null) return;

        File tempDir = new File(TEMP_DIR);
        if (!tempDir.exists()) return;

        File[] files = tempDir.listFiles();
        if (files == null) return;

        int deleted = 0;
        for (File f : files) {
            if (f.delete()) deleted++;
        }

        Log.i(TAG, "Temp cache cleared: " + deleted + " files removed.");
    }

    /**
     * ✅ BOOST 10: Get best available Java path for given version.
     */
    @androidx.annotation.Nullable
    public static String getJavaPath(int version) {
        switch (version) {
            case 8:  return isPathAccessible(JAVA_8_PATH)  ? JAVA_8_PATH  : null;
            case 17: return isPathAccessible(JAVA_17_PATH) ? JAVA_17_PATH : null;
            case 21: return isPathAccessible(JAVA_21_PATH) ? JAVA_21_PATH : null;
            case 25: return isPathAccessible(JAVA_25_PATH) ? JAVA_25_PATH : null;
            default:
                Log.w(TAG, "Unknown Java version: " + version);
                return null;
        }
    }

    /**
     * Storage info log karo.
     */
    public static void logStorageInfo() {
        if (RUNTIME_DIR == null) return;

        File runtime = new File(RUNTIME_DIR);
        long totalMB = runtime.getTotalSpace()     / (1024 * 1024);
        long freeMB  = runtime.getFreeSpace()      / (1024 * 1024);
        long usedMB  = totalMB - freeMB;

        Log.i(TAG, "Storage | Total: " + totalMB + "MB"
            + " | Used: " + usedMB + "MB"
            + " | Free: " + freeMB + "MB");
    }

    /**
     * Debug: Sab paths print karo.
     */
    private static void dumpPaths() {
        Log.d(TAG, "=== CS Launcher Paths ===");
        Log.d(TAG, "NATIVE_LIB_DIR:      " + NATIVE_LIB_DIR);
        Log.d(TAG, "LOG_DIR:             " + LOG_DIR);
        Log.d(TAG, "CACHE_DIR:           " + CACHE_DIR);
        Log.d(TAG, "TEMP_DIR:            " + TEMP_DIR);
        Log.d(TAG, "RUNTIME_DIR:         " + RUNTIME_DIR);
        Log.d(TAG, "JAVA_8_PATH:         " + JAVA_8_PATH);
        Log.d(TAG, "JAVA_17_PATH:        " + JAVA_17_PATH);
        Log.d(TAG, "JAVA_21_PATH:        " + JAVA_21_PATH);
        Log.d(TAG, "JAVA_25_PATH:        " + JAVA_25_PATH);
        Log.d(TAG, "LWJGL_DIR:           " + LWJGL_DIR);
        Log.d(TAG, "PRIVATE_COMMON_DIR:  " + PRIVATE_COMMON_DIR);
        Log.d(TAG, "SHARED_COMMON_DIR:   " + SHARED_COMMON_DIR);
        Log.d(TAG, "CONTROLLER_DIR:      " + CONTROLLER_DIR);
        Log.d(TAG, "CONFIG_DIR:          " + CONFIG_DIR);
        Log.d(TAG, "=========================");
    }
}
