package com.tungsten.fcl.game;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

// Firebase removed — use Mojang / direct HTTP fallback
import com.tungsten.fclcore.auth.Account;
import com.tungsten.fclcore.auth.yggdrasil.TextureModel;
import com.tungsten.fclcore.fakefx.beans.property.ObjectProperty;
import com.tungsten.fclcore.fakefx.beans.property.SimpleObjectProperty;
import com.tungsten.fclcore.fakefx.beans.value.ObservableValue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TexturesLoader {

    // =========================================================
    // Constants
    // =========================================================
    private static final String TAG             = "TexturesLoader";
    private static final String DB_USERS        = "users";
    private static final String DB_SKIN_URL     = "skinUrl";
    private static final int    CONNECT_TIMEOUT = 5000;
    private static final int    READ_TIMEOUT    = 8000;

    // =========================================================
    // Default Skin Paths
    // =========================================================
    private static final String PATH_STEVE =
        "static/textures/steve.png";
    private static final String PATH_ALEX  =
        "static/textures/alex.png";

    // Default fallback URLs
    private static final String URL_STEVE_FALLBACK =
        "https://minotar.net/skin/MHF_Steve";
    private static final String URL_ALEX_FALLBACK  =
        "https://minotar.net/skin/MHF_Alex";

    // =========================================================
    // ✅ BOOST 1: LruCache - Smart Memory Cache
    // Simple HashMap se zyada efficient
    // Max 8MB cache
    // =========================================================
    private static final int CACHE_SIZE_BYTES = 8 * 1024 * 1024;
    private static final LruCache<String, Bitmap> memoryCache =
        new LruCache<String, Bitmap>(CACHE_SIZE_BYTES) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount();
            }
        };

    // =========================================================
    // ✅ BOOST 2: Thread Pool
    // Ek hi thread nahi, pool use karo
    // =========================================================
    private static final ExecutorService executor =
        Executors.newFixedThreadPool(3);

    // =========================================================
    // Static Skin Cache (Default skins)
    // =========================================================
    private static Bitmap cachedSteve = null;
    private static Bitmap cachedAlex  = null;

    // =========================================================
    // Constructor - Private (Utility class)
    // =========================================================
    private TexturesLoader() {}

    // =========================================================
    // ✅ 1. DEFAULT SKIN - Steve / Alex
    // =========================================================

    /**
     * Default skin load karo (Steve ya Alex).
     * Static cache use karta hai - sirf ek baar load hota hai.
     */
    @Nullable
    public static Bitmap getDefaultSkin(@NonNull TextureModel model) {
        boolean isAlex = (model == TextureModel.ALEX);
        String  path   = isAlex ? PATH_ALEX : PATH_STEVE;

        // Memory cache check
        Bitmap cached = memoryCache.get(path);
        if (cached != null) return cached;

        // Static cache check
        if (isAlex && cachedAlex  != null) return cachedAlex;
        if (!isAlex && cachedSteve != null) return cachedSteve;

        // Load from assets
        try (InputStream is = TexturesLoader.class
                .getClassLoader()
                .getResourceAsStream(path)) {

            if (is == null) {
                Log.w(TAG, "Default skin not found: " + path);
                return null;
            }

            Bitmap bitmap = BitmapFactory.decodeStream(is);
            if (bitmap != null) {
                memoryCache.put(path, bitmap);
                if (isAlex) cachedAlex  = bitmap;
                else        cachedSteve = bitmap;
            }
            return bitmap;

        } catch (Exception e) {
            Log.e(TAG, "Failed to load default skin: " + path, e);
            return null;
        }
    }

    // =========================================================
    // ✅ 2. AVATAR CROP
    // =========================================================
    // =========================================================
    // ✅ 3. FIREBASE SKIN BINDING
    // Main method - avatarBinding with Firebase support
    // =========================================================

    /**
     * Account ka avatar load karo with Firebase skin support.
     *
     * Fallback Chain:
     * Firebase skinUrl → Mojang API → Default Steve/Alex
     */
    @NonNull
    public static ObservableValue<Bitmap> avatarBinding(
            @Nullable Account account,
            int size) {

        // Default avatar property
        Bitmap defaultSkin   = getDefaultSkin(TextureModel.STEVE);
        Bitmap defaultAvatar = toAvatar(defaultSkin, size);

        ObjectProperty<Bitmap> property =
            new SimpleObjectProperty<>(defaultAvatar);

        if (account == null) return property;

        String username = account.getUsername();
        if (username == null || username.isBlank()) return property;

        // ✅ STEP 1: Cache check karo pehle
        String cacheKey = "avatar_" + username + "_" + size;
        Bitmap cachedAvatar = memoryCache.get(cacheKey);
            if (cachedAvatar != null) {
            property.set(cachedAvatar);
            // Background mein refresh bhi karo
            loadFromMojang(username, size, property, cacheKey);
            return property;
        }

        // ✅ STEP 2: Directly try Mojang (no Firebase)
        loadFromMojang(username, size, property, cacheKey);

        return property;
    }

    /**
     * Firebase se skin URL fetch karo aur load karo.
     */
    // Firebase removed — we directly query Mojang as primary source

    /**
     * Mojang API se skin load karo.
     * Fallback: Firebase fail hone ke baad
     */
    private static void loadFromMojang(
            @NonNull String username,
            int size,
            @NonNull ObjectProperty<Bitmap> property,
            @NonNull String cacheKey) {

        String mojangUrl = "https://minotar.net/skin/" + username;

        loadSkinFromUrl(mojangUrl, size, property, cacheKey,
            // Final fallback: Default Steve
            () -> {
                Bitmap defaultSkin   = getDefaultSkin(TextureModel.STEVE);
                Bitmap defaultAvatar = toAvatar(defaultSkin, size);
                if (defaultAvatar != null) {
                    property.set(defaultAvatar);
                }
                Log.d(TAG, "Using default Steve skin for: " + username);
            }
        );
    }

    // =========================================================
    // ✅ 4. URL SE SKIN LOAD KARO
    // =========================================================

    /**
     * URL se skin bitmap load karo.
     * Background thread mein chalega.
     */
    private static void loadSkinFromUrl(
            @NonNull String url,
            int size,
            @NonNull ObjectProperty<Bitmap> property,
            @NonNull String cacheKey,
            @Nullable Runnable onFail) {

        executor.execute(() -> {
            try {
                // Memory cache check
                Bitmap cached = memoryCache.get(url);
                if (cached != null) {
                    Bitmap avatar = toAvatar(cached, size);
                    if (avatar != null) {
                        property.set(avatar);
                        memoryCache.put(cacheKey, avatar);
                    }
                    return;
                }

                // URL se download karo
                Bitmap skin = downloadBitmap(url);

                if (skin != null) {
                    // Cache mein save karo
                    memoryCache.put(url, skin);

                    Bitmap avatar = toAvatar(skin, size);
                    if (avatar != null) {
                        memoryCache.put(cacheKey, avatar);
                        property.set(avatar);
                    }

                    Log.d(TAG, "Skin loaded from: " + url);
                } else {
                    // Download fail - fallback
                    Log.w(TAG, "Skin download failed: " + url);
                    if (onFail != null) onFail.run();
                }

            } catch (Exception e) {
                Log.e(TAG, "loadSkinFromUrl error: " + url, e);
                if (onFail != null) onFail.run();
            }
        });
    }

    // =========================================================
    // ✅ 5. BITMAP DOWNLOAD
    // =========================================================

    /**
     * URL se Bitmap download karo.
     */
    @Nullable
    private static Bitmap downloadBitmap(@NonNull String urlString) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setRequestMethod("GET");
            connection.connect();

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "HTTP " + responseCode + " for: " + urlString);
                return null;
            }

            try (InputStream is = connection.getInputStream()) {
                return BitmapFactory.decodeStream(is);
            }

        } catch (Exception e) {
            Log.e(TAG, "Download failed: " + urlString, e);
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    // =========================================================
    // ✅ 6. TEXTURE BINDING (Game Integration)
    // =========================================================

    /**
     * Account ka texture binding - game engine ke liye.
     * Skin file game directory mein save karta hai.
     */
    @NonNull
    public static ObservableValue<Bitmap> textureBinding(
            @Nullable Account account) {

        Bitmap defaultSkin = getDefaultSkin(TextureModel.STEVE);
        ObjectProperty<Bitmap> property =
            new SimpleObjectProperty<>(defaultSkin);

        if (account == null) return property;

        String username = account.getUsername();
        if (username == null) return property;

        // Try Mojang directly (Firebase removed)
        executor.execute(() -> {
            Bitmap skin = null;
            String mojangUrl = "https://minotar.net/skin/" + username;
            skin = downloadBitmap(mojangUrl);
            if (skin == null) skin = getDefaultSkin(TextureModel.STEVE);
            if (skin != null) property.set(skin);
        });

        return property;
    }

    // =========================================================
    // ✅ 7. MANUAL REFRESH
    // =========================================================

    /**
     * Specific user ka skin force refresh karo.
     * Login ke baad ya skin change ke baad call karo.
     */
    public static void refreshSkin(
            @NonNull String username,
            int size,
            @NonNull ObjectProperty<Bitmap> property) {

        // Cache invalidate karo
        String cacheKey = "avatar_" + username + "_" + size;
        memoryCache.remove(cacheKey);

        // Fresh load karo
        loadFromMojang(username, size, property, cacheKey);

        Log.d(TAG, "Skin refresh triggered for: " + username);
    }

    // =========================================================
    // ✅ 8. CACHE MANAGEMENT
    // =========================================================

    /**
     * Pura cache clear karo.
     * Memory pressure pe call hota hai.
     */
    public static void clearCache() {
        memoryCache.evictAll();
        cachedSteve = null;
        cachedAlex  = null;
        Log.d(TAG, "Texture cache cleared");
    }

    /**
     * Cache size get karo (debug ke liye).
     */
    public static int getCacheSize() {
        return memoryCache.size();
    }

    /**
     * Specific entry remove karo cache se.
     */
    public static void removeCacheEntry(@NonNull String key) {
        memoryCache.remove(key);
    }

    // =========================================================
    // ✅ 9. SAVE SKIN TO FILE (Game Directory)
    // =========================================================

    /**
     * Skin bitmap ko game directory mein save karo.
     * Minecraft launcher ke liye zaroori hai.
     */
    public static void saveSkinToFile(
            @NonNull Bitmap skin,
            @NonNull String filePath) {

        executor.execute(() -> {
            try {
                File file = new File(filePath);
                File parent = file.getParentFile();

                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }

                try (FileOutputStream fos = new FileOutputStream(file)) {
                    skin.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.flush();
                    Log.d(TAG, "Skin saved to: " + filePath);
                }

            } catch (Exception e) {
                Log.e(TAG, "Failed to save skin: " + filePath, e);
            }
        });
    }
}