package com.tungsten.fclauncher.bridge;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Handler;
import android.os.Process;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.tungsten.fclauncher.keycodes.LwjglGlfwKeycode;
import com.tungsten.fclauncher.utils.FCLPath;

import org.lwjgl.glfw.CallbackBridge;

import java.io.File;
import java.io.Serializable;
import java.util.concurrent.atomic.AtomicBoolean;

public class FCLBridge implements Serializable {

    // =========================================================
    // Constants - Resolution
    // =========================================================
    public static boolean FORCE_RESOLUTION            = false;
    public static float   FORCE_RESOLUTION_SCALE      = -1f;
    public static int     FORCE_RESOLUTION_WIDTH      = 1280;
    public static int     FORCE_RESOLUTION_HEIGHT     = 720;
    public static int     FORCE_RESOLUTION_START_SIZE = -1;

    public static final int DEFAULT_WIDTH  = 1280;
    public static final int DEFAULT_HEIGHT = 720;

    // =========================================================
    // Constants - Hit Result
    // =========================================================
    public static final int HIT_RESULT_TYPE_UNKNOWN = 0;
    public static final int HIT_RESULT_TYPE_MISS    = 1;
    public static final int HIT_RESULT_TYPE_BLOCK   = 2;
    public static final int HIT_RESULT_TYPE_ENTITY  = 3;

    // =========================================================
    // Constants - Injector
    // =========================================================
    public static final int INJECTOR_MODE_ENABLE  = 1;
    public static final int INJECTOR_MODE_DISABLE = 0;

    // =========================================================
    // Constants - Event Types
    // =========================================================
    public static final int KeyPress        = 2;
    public static final int KeyRelease      = 3;
    public static final int ButtonPress     = 4;
    public static final int ButtonRelease   = 5;
    public static final int MotionNotify    = 6;
    public static final int KeyChar         = 7;
    public static final int ConfigureNotify = 22;
    public static final int FCLMessage      = 37;

    // =========================================================
    // Constants - Mouse Buttons
    // =========================================================
    public static final int Button1 = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_1;
    public static final int Button2 = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_2;
    public static final int Button3 = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_3;
    public static final int Button4 = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_4;
    public static final int Button5 = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_5;
    public static final int Button6 = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_6;
    public static final int Button7 = LwjglGlfwKeycode.GLFW_MOUSE_BUTTON_7;

    // =========================================================
    // Constants - Cursor
    // =========================================================
    public static final int CursorEnabled  = 1;
    public static final int CursorDisabled = 0;

    // =========================================================
    // Constants - AWT Renderer
    // =========================================================
    private static final int  AWT_TARGET_FPS    = 60;
    private static final long AWT_FRAME_TIME_MS = 1000L / AWT_TARGET_FPS;

    // =========================================================
    // Constants - Performance JVM Args
    // =========================================================
    public static final String JVM_ARGS_PERFORMANCE =
        "-XX:+UseG1GC "                    +
        "-XX:+UnlockExperimentalVMOptions " +
        "-XX:G1NewSizePercent=20 "          +
        "-XX:G1ReservePercent=20 "          +
        "-XX:MaxGCPauseMillis=20 "          +
        "-XX:G1HeapRegionSize=32M "         +
        "-XX:+DisableExplicitGC "           +
        "-XX:+AlwaysPreTouch "              +
        "-XX:+OptimizeStringConcat "        +
        "-XX:+UseStringDeduplication "      +
        "-Dsun.java2d.opengl=true "         +
        "-Djava.net.preferIPv4Stack=true "  +
        "-Xss1m";

    // =========================================================
    // Instance Fields
    // =========================================================
    private FCLBridgeCallback callback;
    private double            scaleFactor        = 1.0;
    private String            controller         = "Default";
    private String            gameDir;
    private String            logPath;
    private String            renderer;
    private String            java;
    private Surface           surface;
    private Handler           handler;
    private Thread            thread;
    private SurfaceTexture    surfaceTexture;
    private String            modSummary;
    private boolean           hasTouchController = false;

    // ✅ Thread-safe surface flag
    private final AtomicBoolean surfaceDestroyed = new AtomicBoolean(false);

    // =========================================================
    // Static Fields
    // =========================================================
    private static OpenFolderCallback folderCallback = null;

    // =========================================================
    // Native Library Loading
    // =========================================================
    static {
        System.loadLibrary("fcl");
        System.loadLibrary("pojavexec_awt");
    }

    // =========================================================
    // Constructor
    // =========================================================
    public FCLBridge() {}

    // =========================================================
    // Native Methods
    // =========================================================
    public static native void nativeClipboardReceived(String data, String mimeTypeSub);
    public native int[]  renderAWTScreenFrame();
    public native void   nativeSendData(int type, int i1, int i2, int i3, int i4);
    public native void   nativeMoveWindow(int x, int y);
    public native int    redirectStdio(String path);
    public native int    chdir(String path);
    public native void   setenv(String key, String value);
    public native long   dlopen(String path);
    public native void   setLdLibraryPath(String path);
    public native void   setupExitTrap(FCLBridge bridge);
    public native void   refreshHitResultType();
    public native void   setFCLBridge(FCLBridge fclBridge);

    // =========================================================
    // Execute - Game Launch
    // =========================================================

    /**
     * Game launch karo.
     *
     * ✅ ANDROID 15 SAFE:
     * - Priority thread ke ANDAR set hoti hai (myTid() use karke)
     * - try-catch se SecurityException gracefully handle hoti hai
     * - Crash nahi hoga agar OS deny kare
     */
    public void execute(Surface surface, FCLBridgeCallback callback) {
        this.handler  = new Handler();
        this.callback = callback;
        this.surface  = surface;

        setFCLBridge(this);
        CallbackBridge.setFCLBridge(this);

        receiveLog("invoke redirectStdio\n");
        int errorCode = redirectStdio(getLogPath());
        if (errorCode != 0) {
            receiveLog("redirectStdio error: " + errorCode + "\n");
        }

        receiveLog("invoke setLogPipeReady\n");

        if (surface != null) {
            handleWindow();
        }

        receiveLog("invoke setEventPipe\n");

        if (thread != null) {
            startGameThread(thread);
        }
    }

    /**
     * Game thread safely start karo with priority boost.
     *
     * ✅ FIX 1: Priority INSIDE thread (myTid() = Linux TID)
     * ✅ FIX 2: try-catch Android 15 SecurityException ke liye
     * ✅ FIX 3: IllegalArgumentException agar thread ready nahi
     * ✅ FIX 4: Generic Exception - koi bhi error crash nahi karega
     */
    private void startGameThread(Thread originalThread) {
        Thread gameThread = new Thread(() -> {

            // ✅ ANDROID 15 SAFE PRIORITY BOOST
            // myTid() = actual Linux TID jo OS samajhta hai
            // thread.getId() = Java ID jo OS nahi samajhta (CRASH!)
            try {
                Process.setThreadPriority(
                    Process.THREAD_PRIORITY_URGENT_DISPLAY
                );
                receiveLog("CS-Launcher: Priority URGENT_DISPLAY set.\n");

            } catch (SecurityException e) {
                // Android 15 strict mode - deny kiya par crash nahi
                receiveLog("CS-Launcher: OS denied priority boost"
                    + " - running normally.\n");

            } catch (IllegalArgumentException e) {
                // Thread abhi OS mein register nahi hua
                receiveLog("CS-Launcher: Thread not ready"
                    + " for priority boost.\n");

            } catch (Exception e) {
                // Koi bhi aur error - game band nahi hoga
                receiveLog("CS-Launcher: Priority skip - "
                    + e.getMessage() + "\n");
            }

            // Original game thread run karo
            originalThread.run();

        }, "CS-GameThread");

        // Properties copy karo
        gameThread.setDaemon(originalThread.isDaemon());

        // Start karo - priority andar safely set hogi
        gameThread.start();

        // Field update karo
        this.thread = gameThread;

        receiveLog("CS-GameThread started successfully.\n");
    }

    // =========================================================
    // Event Pushers
    // =========================================================

    public void pushEventMouseButton(int button, boolean press) {
        switch (button) {
            case Button4:
                if (press) CallbackBridge.sendScroll(0,  1.0);
                break;
            case Button5:
                if (press) CallbackBridge.sendScroll(0, -1.0);
                break;
            default:
                CallbackBridge.sendMouseButton(button, press);
        }
    }

    public void pushEventPointer(int x, int y) {
        if (FORCE_RESOLUTION && FORCE_RESOLUTION_SCALE > 0) {
            x = (int) ((x - FORCE_RESOLUTION_START_SIZE)
                    / FORCE_RESOLUTION_SCALE);
            y = (int) (y / FORCE_RESOLUTION_SCALE);
        }
        CallbackBridge.sendCursorPos(x, y);
    }

    public void pushEventPointer(float x, float y) {
        CallbackBridge.sendCursorPos(x, y);
    }

    public void pushEventKey(int keyCode, int keyChar, boolean press) {
        CallbackBridge.sendKeycode(
            keyCode,
            (char) keyChar,
            0,
            CallbackBridge.getCurrentMods(),
            press
        );
    }

    public void pushEventChar(char keyChar) {
        CallbackBridge.sendChar(keyChar, 0);
    }

    public void pushEventWindow(int width, int height) {
        CallbackBridge.sendUpdateWindowSize(width, height);
    }

    // =========================================================
    // FCLBridge Callbacks
    // =========================================================

    public void onExit(int code) {
        if (callback != null) {
            callback.onLog("OpenJDK exited: " + code + "\n");
            callback.onExit(code);
        }
        // RAM free on exit
        Runtime.getRuntime().gc();
    }

    public void setHitResultType(int type) {
        if (callback != null) callback.onHitResultTypeChange(type);
    }

    public void setCursorMode(int mode) {
        if (callback != null) callback.onCursorModeChange(mode);
    }

    // =========================================================
    // Clipboard
    // =========================================================

    public void setPrimaryClipString(String string) {
        ClipboardManager clipboard = getClipboard();
        if (clipboard == null) return;
        clipboard.setPrimaryClip(
            ClipData.newPlainText("FCL Clipboard", string)
        );
    }

    @Nullable
    public String getPrimaryClipString() {
        ClipboardManager clipboard = getClipboard();
        if (clipboard == null || !clipboard.hasPrimaryClip()) return null;
        ClipData.Item item = clipboard.getPrimaryClip().getItemAt(0);
        return (item != null) ? item.getText().toString() : null;
    }

    @Nullable
    private ClipboardManager getClipboard() {
        Context ctx = FCLPath.CONTEXT;
        if (ctx == null) return null;
        return (ClipboardManager) ctx.getSystemService(
            Context.CLIPBOARD_SERVICE
        );
    }

    // =========================================================
    // Static Clipboard Methods
    // =========================================================

    public static void setOpenFolderCallback(OpenFolderCallback callback) {
        folderCallback = callback;
    }

    public static void querySystemClipboard() {
        Context ctx = FCLPath.CONTEXT;
        if (ctx == null) {
            nativeClipboardReceived(null, null);
            return;
        }

        ClipboardManager clipboard =
            (ClipboardManager) ctx.getSystemService(
                Context.CLIPBOARD_SERVICE
            );

        ((Activity) ctx).runOnUiThread(() -> {
            ClipData clipData = clipboard.getPrimaryClip();
            if (clipData == null) {
                nativeClipboardReceived(null, null);
                return;
            }

            ClipData.Item item = clipData.getItemAt(0);
            CharSequence  text = (item != null) ? item.getText() : null;

            if (text == null) {
                nativeClipboardReceived(null, null);
            } else {
                nativeClipboardReceived(text.toString(), "plain");
            }
        });
    }

    public static void putClipboardData(String data, String mimeType) {
        Context ctx = FCLPath.CONTEXT;
        if (ctx == null) return;

        ((Activity) ctx).runOnUiThread(() -> {
            ClipboardManager clipboard =
                (ClipboardManager) ctx.getSystemService(
                    Context.CLIPBOARD_SERVICE
                );

            ClipData clipData = null;
            switch (mimeType) {
                case "text/plain":
                    clipData = ClipData.newPlainText("AWT Paste", data);
                    break;
                case "text/html":
                    clipData = ClipData.newHtmlText("AWT Paste", data, data);
                    break;
            }

            if (clipData != null) clipboard.setPrimaryClip(clipData);
        });
    }

    // =========================================================
    // Open Link
    // =========================================================

    public static void openLink(final String link) {
        Context ctx = FCLPath.CONTEXT;
        if (ctx == null) return;

        ((Activity) ctx).runOnUiThread(() -> {
            try {
                String targetLink = link;

                if (link.startsWith("file:")) {
                    targetLink = link.replaceFirst("^file:/+", "/");
                    if (targetLink.endsWith("/")) {
                        if (folderCallback != null) {
                            folderCallback.onBrowse(targetLink);
                        }
                        return;
                    }
                }

                Intent intent = new Intent(Intent.ACTION_VIEW);
                Uri uri;

                if (targetLink.startsWith("http")) {
                    uri = Uri.parse(targetLink);
                } else {
                    uri = FileProvider.getUriForFile(
                        ctx,
                        ((Activity) ctx).getApplication()
                            .getPackageName() + ".provider",
                        new File(targetLink)
                    );
                }

                intent.setDataAndType(uri, "*/*");
                ctx.startActivity(Intent.createChooser(intent, ""));

            } catch (Exception e) {
                Log.e("FCLBridge", "openLink error | link=" + link, e);
            }
        });
    }

    // =========================================================
    // Window Handler
    // =========================================================

    private void handleWindow() {
        if (gameDir != null) {
            receiveLog("invoke setFCLNativeWindow\n");
            CallbackBridge.setupBridgeWindow(surface);
        } else {
            receiveLog("start Android AWT Renderer\n");
            startAWTRendererThread();
        }
    }

    /**
     * AWT Renderer - Optimized for mobile.
     * ✅ 60 FPS cap
     * ✅ Bitmap reuse
     * ✅ Priority boost (with safe try-catch)
     * ✅ AtomicBoolean stop flag
     */
    private void startAWTRendererThread() {
        Thread rendererThread = new Thread(() -> {

            // ✅ Priority inside thread - Android 15 safe
            try {
                Process.setThreadPriority(
                    Process.THREAD_PRIORITY_URGENT_DISPLAY
                );
            } catch (SecurityException e) {
                receiveLog("AWT renderer priority denied - continuing.\n");
            } catch (Exception e) {
                receiveLog("AWT renderer priority skip: "
                    + e.getMessage() + "\n");
            }

            // Bitmap reuse - no GC pressure
            Bitmap bitmap = Bitmap.createBitmap(
                DEFAULT_WIDTH,
                DEFAULT_HEIGHT,
                Bitmap.Config.ARGB_8888
            );

            Paint paint = new Paint();
            paint.setAntiAlias(false);
            paint.setFilterBitmap(false);
            paint.setDither(false);

            long lastFrameTime = 0L;

            try {
                while (!surfaceDestroyed.get() && surface.isValid()) {

                    // 60 FPS cap
                    long now     = System.currentTimeMillis();
                    long elapsed = now - lastFrameTime;
                    if (elapsed < AWT_FRAME_TIME_MS) {
                        Thread.sleep(AWT_FRAME_TIME_MS - elapsed);
                    }
                    lastFrameTime = System.currentTimeMillis();

                    Canvas canvas = surface.lockCanvas(null);
                    if (canvas == null) continue;

                    canvas.drawRGB(0, 0, 0);

                    int[] rgbArray = renderAWTScreenFrame();
                    if (rgbArray != null) {
                        bitmap.setPixels(
                            rgbArray,
                            0, DEFAULT_WIDTH,
                            0, 0,
                            DEFAULT_WIDTH,
                            DEFAULT_HEIGHT
                        );
                        canvas.drawBitmap(bitmap, 0, 0, paint);
                    }

                    surface.unlockCanvasAndPost(canvas);
                }

            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                receiveLog("AWT Renderer interrupted.\n");
            } catch (Throwable t) {
                if (handler != null) {
                    handler.post(() -> receiveLog(t + "\n"));
                }
            } finally {
                if (!bitmap.isRecycled()) bitmap.recycle();
                try {
                    if (surface.isValid()) surface.release();
                } catch (Exception ignored) {}
            }

        }, "CS-AWTRenderer");

        rendererThread.setPriority(Thread.MAX_PRIORITY);
        rendererThread.start();
    }

    // =========================================================
    // Static Utility
    // =========================================================

    public static int getFps() {
        return CallbackBridge.getFps();
    }

    public static String getPerformanceJvmArgs() {
        return JVM_ARGS_PERFORMANCE;
    }

    // =========================================================
    // Getters & Setters
    // =========================================================

    public void setThread(Thread thread)               { this.thread = thread; }
    public Thread getThread()                          { return thread; }

    public SurfaceTexture getSurfaceTexture()          { return surfaceTexture; }
    public void setSurfaceTexture(SurfaceTexture st)   { this.surfaceTexture = st; }

    public FCLBridgeCallback getCallback()             { return callback; }

    public void setScaleFactor(double scaleFactor)     { this.scaleFactor = scaleFactor; }
    public double getScaleFactor()                     { return scaleFactor; }

    public void setController(String controller)       { this.controller = controller; }
    public String getController()                      { return controller; }

    public void setGameDir(String gameDir)             { this.gameDir = gameDir; }
    @Nullable
    public String getGameDir()                         { return gameDir; }

    public void setRenderer(String renderer)           { this.renderer = renderer; }
    public String getRenderer()                        { return renderer; }

    public void setJava(String java)                   { this.java = java; }
    public String getJava()                            { return java; }

    public void setSurfaceDestroyed(boolean destroyed) {
        this.surfaceDestroyed.set(destroyed);
    }
    public boolean isSurfaceDestroyed() {
        return surfaceDestroyed.get();
    }

    @NonNull
    public String getLogPath()                         { return logPath; }
    public void setLogPath(String logPath)             { this.logPath = logPath; }

    public void receiveLog(String log) {
        if (callback != null) callback.onLog(log);
    }

    public String getModSummary()                      { return modSummary; }
    public void setModSummary(String modSummary)       { this.modSummary = modSummary; }

    public boolean hasTouchController()                { return hasTouchController; }
    public void setHasTouchController(boolean v)       { this.hasTouchController = v; }
}
