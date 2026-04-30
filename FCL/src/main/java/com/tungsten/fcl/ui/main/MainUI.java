package com.tungsten.fcl.ui.main;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;

import androidx.appcompat.widget.LinearLayoutCompat;

import com.tungsten.fcl.R;
import com.tungsten.fcl.game.TexturesLoader;
import com.tungsten.fcl.setting.Accounts;
import com.tungsten.fclcore.auth.Account;
import com.tungsten.fclcore.fakefx.beans.property.ObjectProperty;
import com.tungsten.fclcore.fakefx.beans.property.SimpleObjectProperty;
import com.tungsten.fclcore.task.Schedulers;
import com.tungsten.fclcore.task.Task;
import com.tungsten.fcllibrary.component.theme.ThemeEngine;
import com.tungsten.fcllibrary.component.ui.FCLCommonUI;
import com.tungsten.fcllibrary.component.view.FCLButton;
import com.tungsten.fcllibrary.component.view.FCLTextView;
import com.tungsten.fcllibrary.component.view.FCLUILayout;
import com.tungsten.fcllibrary.skin.SkinCanvas;
import com.tungsten.fcllibrary.skin.SkinRenderer;

import android.widget.RelativeLayout;

public class MainUI extends FCLCommonUI implements View.OnClickListener {

    // =========================================================
    // CS Launcher Branding
    // =========================================================
    private static final String LAUNCHER_NAME   = "CS LAUNCHER";
    private static final String LAUNCHER_STATUS = "Powered by Craft Studio";

    private static final String ANNOUNCEMENT_TEXT =
            "Welcome to CS Launcher!\n\n" +
            "• JRE 8 / 17 / 21 / 25 Support\n" +
            "• LWJGL 3 Enabled\n" +
            "• Mobile Optimized Performance\n" +
            "• Cyberpunk UI Active\n\n" +
            "Happy Crafting! 🎮";

    private LinearLayoutCompat announcementContainer;
    private View announcementLayout;
    private FCLTextView titleView, announcementView, dateView;
    private View hideButton;

    private RelativeLayout skinContainer;
    private SkinCanvas skinCanvas;
    private SkinRenderer renderer;

    private ObjectProperty<Account> currentAccount;

    public MainUI(Context context, FCLUILayout parent, int id) {
        super(context, parent, id);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initViews();
        setupTheme();
        setupClickListeners();
        setupAnnouncement();
        setupSkinDisplay();
    }

    private void initViews() {
        announcementContainer = findViewById(R.id.announcement_container);
        announcementLayout    = findViewById(R.id.announcement_layout);
        titleView             = findViewById(R.id.title);
        announcementView      = findViewById(R.id.announcement);
        dateView              = findViewById(R.id.date);
        hideButton            = findViewById(R.id.hide);
        skinContainer         = findViewById(R.id.skin_container);
        renderer              = new SkinRenderer(getContext());
    }

    private void setupTheme() {
        if (announcementLayout != null) {
            announcementLayout.setBackground(null);
        }
    }

    private void setupClickListeners() {
        if (hideButton != null) hideButton.setOnClickListener(this);
    }

    private void setupAnnouncement() {
        if (announcementContainer == null) return;
        announcementContainer.setVisibility(View.VISIBLE);
        if (titleView != null) titleView.setText(LAUNCHER_NAME);
        if (dateView != null) dateView.setText(LAUNCHER_STATUS);
        if (announcementView != null) announcementView.setText(ANNOUNCEMENT_TEXT);
    }

    private void hideAnnouncement() {
        if (announcementContainer != null) announcementContainer.setVisibility(View.GONE);
    }

    // ✅ FIXED 3D SKIN: Automatically loads Alex if no skin found for the account
    private void setupSkinDisplay() {
        currentAccount = new SimpleObjectProperty<Account>() {
            @Override
            protected void invalidated() {
                Account account = get();
                renderer.textureProperty().unbind();

                if (account == null) {
                    loadDefaultTexture();
                } else {
                    TexturesLoader.textureBinding(account).addListener((obs, ob, newBmp) -> {
                        if (newBmp != null) {
                            renderer.updateTexture(newBmp, null);
                        } else {
                            loadDefaultTexture();
                        }
                    });
                    
                    Bitmap current = TexturesLoader.textureBinding(account).getValue();
                    if (current != null) {
                        renderer.updateTexture(current, null);
                    } else {
                        loadDefaultTexture();
                    }
                }
            }
        };
        currentAccount.bind(Accounts.selectedAccountProperty());
    }

    public void refreshSkin(Account account) {
        Schedulers.androidUIThread().execute(() -> {
            if (currentAccount == null || account == null) return;
            if (account.equals(currentAccount.get())) {
                renderer.textureProperty().unbind();
                TexturesLoader.textureBinding(account).addListener((obs, ob, newBmp) -> {
                    if (newBmp != null) {
                        renderer.updateTexture(newBmp, null);
                    } else {
                        loadDefaultTexture();
                    }
                });
                
                Bitmap current = TexturesLoader.textureBinding(account).getValue();
                if (current != null) {
                    renderer.updateTexture(current, null);
                } else {
                    loadDefaultTexture();
                }
            }
        });
    }

    private void loadDefaultTexture() {
        try {
            renderer.updateTexture(BitmapFactory.decodeStream(
                MainUI.class.getResourceAsStream("/assets/img/alex.png")), null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        handleSkinOnStart();
    }

    private void handleSkinOnStart() {
        boolean skinEnabled = !ThemeEngine.getInstance().theme.isCloseSkinModel();
        if (skinEnabled) {
            if (skinCanvas == null) {
                skinCanvas = new SkinCanvas(getContext());
                skinCanvas.setRenderer(renderer, 5f);
            } else {
                skinCanvas.onResume();
                renderer.updateTexture(renderer.getTexture()[0], renderer.getTexture()[1]);
            }
            if (skinCanvas.getParent() == null) skinContainer.addView(skinCanvas);
            skinContainer.setVisibility(View.VISIBLE);
        } else {
            skinContainer.setVisibility(View.GONE);
            pauseSkinCanvas();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isShowing()) if (skinCanvas != null) skinCanvas.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        pauseSkinCanvas();
    }

    @Override
    public void onStop() {
        super.onStop();
        pauseSkinCanvas();
        if (skinContainer != null && skinCanvas != null) skinContainer.removeView(skinCanvas);
    }

    private void pauseSkinCanvas() {
        if (skinCanvas != null) skinCanvas.onPause();
    }

    @Override
    public void onClick(View view) {
        if (view == hideButton) hideAnnouncement();
    }

    @Override
    public Task<?> refresh(Object... param) {
        return Task.runAsync(() -> {});
    }
}
