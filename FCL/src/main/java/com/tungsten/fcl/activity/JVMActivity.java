package com.tungsten.fcl.activity;

import android.content.res.Configuration;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.tungsten.fcl.R;
import com.tungsten.fcl.control.GameMenu;
import com.tungsten.fcl.control.JarExecutorMenu;
import com.tungsten.fcl.control.MenuCallback;
import com.tungsten.fcl.control.MenuType;
import com.tungsten.fcl.control.OpenFolderDialog;
import com.tungsten.fcl.control.view.MenuView;
import com.tungsten.fcl.setting.GameOption;
import com.tungsten.fcl.terracotta.Terracotta;
import com.tungsten.fcl.util.AndroidUtils;
import com.tungsten.fclauncher.bridge.FCLBridge;
import com.tungsten.fclauncher.bridge.OpenFolderCallback;
import com.tungsten.fclauncher.keycodes.FCLKeycodes;
import com.tungsten.fclauncher.keycodes.LwjglGlfwKeycode;
import com.tungsten.fclcore.util.Logging;
import com.tungsten.fcllibrary.component.FCLActivity;

import org.lwjgl.glfw.CallbackBridge;

import java.util.Objects;
import java.util.logging.Level;

public class JVMActivity extends FCLActivity
        implements TextureView.SurfaceTextureListener, OpenFolderCallback {

    // =========================================================
    // Constants
    // =========================================================
    private static final String TAG                  = "JVMActivity";
    private static final long   VOLUME_HOLD_MS       = 800L;
    private static final int    SURFACE_OUTPUT_TRIGGER = 1;
    private static final float  PERF_RESOLUTION_SCALE = 0.70f;

    // =========================================================
    // Mobile Game Options
    // =========================================================
    private static final String OPT_FULLSCREEN          = "false";
    private static final String OPT_GRAPHICS            = "0";
    private static final String OPT_RENDER_DISTANCE     = "4";
    private static final String OPT_SIMULATION_DISTANCE = "4";
    private static final String OPT_SMOOTH_LIGHTING     = "false";
    private static final String OPT_PARTICLES           = "2";
    private static final String OPT_CLOUDS              = "false";
    private static final String OPT_ENTITY_SHADOWS      = "false";
    private static final String OPT_FRAMERATE_LIMIT     = "60";
    private static final String OPT_VBO                 = "true";
    private static final String OPT_MIPMAP_LEVELS       = "0";
    private static final String OPT_AO_LEVEL            = "0.0";
    private static final String OPT_BIOME_BLEND         = "0";

    // =========================================================
    // Static State
    // =========================================================
    private static MenuType          menuType;
    private static FCLBridge         fclBridge;
    private static volatile boolean  isRunning = false;

    // =========================================================
    // Instance Fields
    // =========================================================
    private TextureView  textureView;
    private MenuCallback menu;
    private boolean      isTranslated         = false;
    private int          surfaceOutputCounter  = 0;
    private long         volumeDownTime        = 0L;

    // =========================================================
    // Static Setter
    // =========================================================
    public static void setFCLBridge(
            @NonNull FCLBridge bridge,
            @NonNull MenuType type) {
        fclBridge = bridge;
        menuType  = type;
    }

    // =========================================================
    // Lifecycle
    // =========================================================

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FCLBridge.setOpenFolderCallback(this);
        setContentView(R.layout.activity_jvm);

        if (!validateState()) return;

        initMenu();
        initTextureView();
        initWindowFlags();
        initKeyboardAdjust();
    }

    @Override
    protected void onStart() {
        super.onStart();
        setGlfwAttrib(LwjglGlfwKeycode.GLFW_VISIBLE, 1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (menu != null) menu.onResume();
        setWindowFocused(true);
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );
    }

    @Override
    protected void onPause() {
        if (menu != null) menu.onPause();
        setWindowFocused(false);
        super.onPause();
    }

    @Override
    protected void onStop() {
        setGlfwAttrib(LwjglGlfwKeycode.GLFW_VISIBLE, 0);
        super.onStop();
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        refreshSurfaceSize();
    }

    @Override
    protected void onDestroy() {
        Terracotta.setWaiting(this, true);
        Runtime.getRuntime().gc();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        refreshSurfaceSize();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        setWindowFocused(hasFocus);
        if (hasFocus) enableImmersiveMode();
    }

    // =========================================================
    // Init Helpers
    // =========================================================

    private boolean validateState() {
        if (menuType == null || fclBridge == null) {
            Logging.LOG.log(Level.WARNING,
                "FCLBridge or MenuType null - aborting.");
            return false;
        }
        return true;
    }

    private void initMenu() {
        menu = (menuType == MenuType.GAME)
                ? new GameMenu()
                : new JarExecutorMenu();

        menu.setup(this, fclBridge);

        addContentView(
            menu.getLayout(),
            new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        );
    }

    private void initTextureView() {
        textureView = findViewById(R.id.texture_view);
        textureView.setSurfaceTextureListener(this);
        textureView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        if (FCLBridge.FORCE_RESOLUTION) {
            applyForceResolution();
        }
    }

    private void applyForceResolution() {
        float scale = (float) AndroidUtils.getScreenHeight()
                / FCLBridge.FORCE_RESOLUTION_HEIGHT;

        FCLBridge.FORCE_RESOLUTION_SCALE = scale;

        int scaledW = (int) (FCLBridge.FORCE_RESOLUTION_WIDTH  * scale);
        int scaledH = (int) (FCLBridge.FORCE_RESOLUTION_HEIGHT * scale);

        FCLBridge.FORCE_RESOLUTION_START_SIZE =
                (AndroidUtils.getScreenWidth() - scaledW) / 2;

        ViewGroup.LayoutParams params = textureView.getLayoutParams();
        params.width  = scaledW;
        params.height = scaledH;
        textureView.setLayoutParams(params);
        textureView.setX(FCLBridge.FORCE_RESOLUTION_START_SIZE);
    }

    private void initWindowFlags() {
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        );
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
        );
    }

    private void initKeyboardAdjust() {
        getWindow().getDecorView()
            .getViewTreeObserver()
            .addOnGlobalLayoutListener(() -> {

                if (menuType == MenuType.GAME
                        && ((GameMenu) menu).getMenuSetting()
                            .isDisableSoftKeyAdjust()) {
                    return;
                }

                int screenH = getWindow().getDecorView().getHeight();
                Rect rect   = new Rect();
                getWindow().getDecorView()
                    .getWindowVisibleDisplayFrame(rect);

                if (screenH * 2 / 3 > rect.bottom) {
                    textureView.setTranslationY(rect.bottom - screenH);
                    isTranslated = true;
                } else if (isTranslated) {
                    textureView.setTranslationY(0);
                    isTranslated = false;
                }
            });
    }

    // =========================================================
    // Immersive Mode
    // =========================================================

    private void enableImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    // =========================================================
    // Surface Texture Callbacks
    // =========================================================

    @Override
    public void onSurfaceTextureAvailable(
            @NonNull SurfaceTexture surfaceTexture,
            int width, int height) {

        if (isRunning) {
            fclBridge.setSurfaceTexture(surfaceTexture);
            CallbackBridge.setupBridgeWindow(new Surface(surfaceTexture));
            menu.onGraphicOutput();
            return;
        }

        isRunning = true;
        Logging.LOG.log(Level.INFO, "Surface ready - starting JVM.");
        fclBridge.setSurfaceDestroyed(false);

        int resolvedW = resolveWidth(width);
        int resolvedH = resolveHeight(height);

        if (menuType == MenuType.GAME) {
            applyMobileGameOptions(resolvedW, resolvedH);
        }

        surfaceTexture.setDefaultBufferSize(resolvedW, resolvedH);
        fclBridge.execute(
            new Surface(surfaceTexture),
            menu.getCallbackBridge()
        );
        fclBridge.setSurfaceTexture(surfaceTexture);
        fclBridge.pushEventWindow(resolvedW, resolvedH);
    }

    @Override
    public void onSurfaceTextureSizeChanged(
            @NonNull SurfaceTexture surfaceTexture,
            int width, int height) {

        int resolvedW = resolveWidth(width);
        int resolvedH = resolveHeight(height);

        surfaceTexture.setDefaultBufferSize(resolvedW, resolvedH);
        fclBridge.pushEventWindow(resolvedW, resolvedH);
    }

    @Override
    public boolean onSurfaceTextureDestroyed(
            @NonNull SurfaceTexture surfaceTexture) {
        fclBridge.setSurfaceDestroyed(true);
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(
            @NonNull SurfaceTexture surfaceTexture) {
        if (surfaceOutputCounter == SURFACE_OUTPUT_TRIGGER) {
            menu.onGraphicOutput();
            surfaceOutputCounter++;
        } else if (surfaceOutputCounter < SURFACE_OUTPUT_TRIGGER) {
            surfaceOutputCounter++;
        }
    }

    // =========================================================
    // Resolution Helpers
    // =========================================================

    private int resolveWidth(int raw) {
        if (FCLBridge.FORCE_RESOLUTION)
            return FCLBridge.FORCE_RESOLUTION_WIDTH;
        if (menuType == MenuType.GAME) {
            return (int) ((raw + ((GameMenu) menu)
                    .getMenuSetting().getCursorOffset())
                    * fclBridge.getScaleFactor());
        }
        return FCLBridge.DEFAULT_WIDTH;
    }

    private int resolveHeight(int raw) {
        if (FCLBridge.FORCE_RESOLUTION)
            return FCLBridge.FORCE_RESOLUTION_HEIGHT;
        if (menuType == MenuType.GAME) {
            return (int) (raw * fclBridge.getScaleFactor());
        }
        return FCLBridge.DEFAULT_HEIGHT;
    }

    // =========================================================
    // Mobile Game Options
    // =========================================================

    private void applyMobileGameOptions(int width, int height) {
        menu.getInput().initExternalController(textureView);

        GameOption opt = new GameOption(
            Objects.requireNonNull(menu.getBridge()).getGameDir()
        );

        opt.set("fullscreen",          OPT_FULLSCREEN);
        opt.set("overrideWidth",       String.valueOf(width));
        opt.set("overrideHeight",      String.valueOf(height));
        opt.set("graphics",            OPT_GRAPHICS);
        opt.set("renderDistance",      OPT_RENDER_DISTANCE);
        opt.set("simulationDistance",  OPT_SIMULATION_DISTANCE);
        opt.set("smoothLighting",      OPT_SMOOTH_LIGHTING);
        opt.set("particles",           OPT_PARTICLES);
        opt.set("renderClouds",        OPT_CLOUDS);
        opt.set("entityShadows",       OPT_ENTITY_SHADOWS);
        opt.set("maxFps",              OPT_FRAMERATE_LIMIT);
        opt.set("useVbo",              OPT_VBO);
        opt.set("mipmapLevels",        OPT_MIPMAP_LEVELS);
        opt.set("ambientOcclusion",    OPT_AO_LEVEL);
        opt.set("biomeBlendRadius",    OPT_BIOME_BLEND);

        opt.save();

        Logging.LOG.log(Level.INFO,
            "Mobile options applied: " + width + "x" + height);
    }

    // =========================================================
    // Input Handling
    // =========================================================

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (menu == null || menuType != MenuType.GAME) return true;

        boolean handled = menu.getInput().handleKeyEvent(event);

        if (!handled) {
            if (isBackKey(event)) {
                handleBackKey(event);
                return true;
            }
            if (isVolumeKey(event)) {
                handleVolumeKey(event);
                return true;
            }
        }

        return handled;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        if (menu != null
                && menuType == MenuType.GAME
                && menu.getInput().handleGenericMotionEvent(event)) {
            return true;
        }
        return super.dispatchGenericMotionEvent(event);
    }

    // =========================================================
    // Key Helpers
    // =========================================================

    private boolean isBackKey(KeyEvent event) {
        return event.getKeyCode() == KeyEvent.KEYCODE_BACK
                && !((GameMenu) menu).getTouchCharInput().isEnabled();
    }

    private boolean isVolumeKey(KeyEvent event) {
        int code = event.getKeyCode();
        return code == KeyEvent.KEYCODE_VOLUME_DOWN
                || code == KeyEvent.KEYCODE_VOLUME_UP;
    }

    private void handleBackKey(KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_UP) return;
        menu.getInput().sendKeyEvent(FCLKeycodes.KEY_ESC, true);
        menu.getInput().sendKeyEvent(FCLKeycodes.KEY_ESC, false);
    }

    private void handleVolumeKey(KeyEvent event) {
        MenuView menuView = ((GameMenu) menu).getMenuView();
        boolean menuHidden = menuView.getAlpha() == 0f
                || menuView.getVisibility() == View.INVISIBLE;

        if (!menuHidden) return;

        DrawerLayout drawer  = (DrawerLayout) menu.getLayout();
        boolean drawerOpen   = drawer.isDrawerOpen(GravityCompat.START)
                || drawer.isDrawerOpen(GravityCompat.END);

        if (drawerOpen) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                drawer.closeDrawers();
                volumeDownTime = System.currentTimeMillis();
            }
        } else {
            long elapsed = System.currentTimeMillis() - volumeDownTime;
            if (elapsed > VOLUME_HOLD_MS) {
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    drawer.openDrawer(GravityCompat.START, true);
                    drawer.openDrawer(GravityCompat.END, true);
                }
            } else {
                volumeDownTime = System.currentTimeMillis();
            }
        }
    }

    // =========================================================
    // Window Helpers
    // =========================================================

    private void setWindowFocused(boolean focused) {
        int v = focused ? 1 : 0;
        CallbackBridge.nativeSetWindowAttrib(
            LwjglGlfwKeycode.GLFW_FOCUSED, v);
        CallbackBridge.nativeSetWindowAttrib(
            LwjglGlfwKeycode.GLFW_HOVERED, v);
    }

    private void setGlfwAttrib(int attrib, int value) {
        CallbackBridge.nativeSetWindowAttrib(attrib, value);
    }

    private void refreshSurfaceSize() {
        if (textureView != null
                && textureView.getSurfaceTexture() != null) {
            textureView.post(() ->
                onSurfaceTextureSizeChanged(
                    textureView.getSurfaceTexture(),
                    textureView.getWidth(),
                    textureView.getHeight()
                )
            );
        }
    }

    // =========================================================
    // Open Folder
    // =========================================================

    @Override
    public void onBrowse(String path) {
        new OpenFolderDialog(this, path).show();
    }
}
