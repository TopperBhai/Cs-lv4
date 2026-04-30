package com.tungsten.fcl.ui.account

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.util.LruCache
import android.webkit.URLUtil
import com.tungsten.fclauncher.utils.FCLPath
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class CSSkinManager(
    private val context:   Context,
    private val scope:     CoroutineScope,
    private val onSuccess: (String) -> Unit,
    private val onError:   (String) -> Unit,
    private val onLoading: (Boolean) -> Unit
) {

    // =========================================================
    // ✅ COMPANION OBJECT
    // Yeh block class ke andar sabse upar hona ZARURI hai
    // Iske bina TAG, skinCache etc. "Unresolved reference" denge
    // =========================================================
    companion object {

        // Logging
        private const val TAG = "CSSkinManager"

        // Firebase paths
        private const val DB_USERS    = "users"
        private const val DB_SKIN_URL = "skinUrl"

        // Network settings
        private const val CONNECT_TIMEOUT = 5000
        private const val READ_TIMEOUT    = 8000

        // Max skin file size 64KB
        private const val MAX_SKIN_BYTES = 64 * 1024L

        // Default skin URLs
        const val URL_STEVE = "https://minotar.net/skin/MHF_Steve"
        const val URL_ALEX  = "https://minotar.net/skin/MHF_Alex"

        // Allowed skin domains
        private val ALLOWED_DOMAINS = listOf(
            "minotar.net",
            "crafatar.com",
            "mc-heads.net",
            "i.imgur.com",
            "textures.minecraft.net",
            "skin.minecraftservices.com"
        )

        // ✅ LruCache 4MB - Smart memory cache
        private val skinCache = object : LruCache<String, Bitmap>(
            4 * 1024 * 1024
        ) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return value.byteCount
            }
        }

        // Cache clear karo
        fun clearCache() {
            skinCache.evictAll()
            Log.d(TAG, "Skin cache cleared")
        }
    }

    // =========================================================
    // Firebase References
    // =========================================================
    // Local storage for skin URL (replaces Firebase)
    private val prefs: SharedPreferences = context.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private val skinDir = java.io.File(FCLPath.FILES_DIR, "skins").apply { if (!exists()) mkdirs() }

    // =========================================================
    // METHOD 1: URL se Skin Update
    // =========================================================

    fun updateSkinByUrl(username: String, newUrl: String) {

        if (username.isBlank()) {
            onError("Username missing - please login again")
            return
        }

        if (newUrl.isBlank()) {
            onError("URL daalo pehle!")
            return
        }

        val validationError = validateSkinUrl(newUrl)
        if (validationError != null) {
            onError(validationError)
            return
        }

        onLoading(true)

        scope.launch {
            try {
                // URL accessible check
                val accessible = checkUrlAccessible(newUrl)
                if (!accessible) {
                    withContext(Dispatchers.Main) {
                        onLoading(false)
                        onError("URL accessible nahi hai!")
                    }
                    return@launch
                }

                // File size check
                val fileSize = getUrlContentSize(newUrl)
                if (fileSize > MAX_SKIN_BYTES) {
                    withContext(Dispatchers.Main) {
                        onLoading(false)
                        onError(
                            "Skin bahut badi hai!\n" +
                            "Max ${MAX_SKIN_BYTES / 1024}KB allowed."
                        )
                    }
                    return@launch
                }

                // Save locally (prefs)
                prefs.edit()
                    .putString("skin_url_" + username, newUrl)
                    .putLong("skin_last_update_" + username, System.currentTimeMillis())
                    .apply()

                // Cache invalidate
                skinCache.remove(username)

                withContext(Dispatchers.Main) {
                    onLoading(false)
                    onSuccess("✅ Skin update ho gaya!\nGame mein rejoin karo.")
                }

            } catch (e: Exception) {
                Log.e(TAG, "updateSkinByUrl failed", e)
                withContext(Dispatchers.Main) {
                    onLoading(false)
                    onError("Failed: ${e.message}")
                }
            }
        }
    }

    // =========================================================
    // METHOD 2: Local File Upload
    // =========================================================

    fun uploadSkinFromFile(username: String, imageUri: Uri) {

        if (username.isBlank()) {
            onError("Username missing")
            return
        }

        onLoading(true)

        scope.launch {
            try {
                val bitmap = loadBitmapFromUri(imageUri)
                    ?: run {
                        withContext(Dispatchers.Main) {
                            onLoading(false)
                            onError("Image load nahi hua!")
                        }
                        return@launch
                    }

                if (!isValidSkinDimensions(bitmap)) {
                    bitmap.recycle()
                    withContext(Dispatchers.Main) {
                        onLoading(false)
                        onError(
                            "Invalid skin size!\n" +
                            "64x64 ya 64x32 honi chahiye."
                        )
                    }
                    return@launch
                }

                val bytes = bitmapToBytes(bitmap)
                bitmap.recycle()

                val outFile = java.io.File(skinDir, "$username.png")
                outFile.outputStream().use { it.write(bytes) }
                val downloadUrl = "file://${outFile.absolutePath}"

                prefs.edit()
                    .putString("skin_url_" + username, downloadUrl)
                    .putLong("skin_last_update_" + username, System.currentTimeMillis())
                    .apply()

                skinCache.remove(username)

                withContext(Dispatchers.Main) {
                    onLoading(false)
                    onSuccess("✅ Skin upload ho gaya!")
                }

            } catch (e: Exception) {
                Log.e(TAG, "uploadSkinFromFile failed", e)
                withContext(Dispatchers.Main) {
                    onLoading(false)
                    onError("Upload failed: ${e.message}")
                }
            }
        }
    }

    // =========================================================
    // METHOD 3: MC Player se Copy
    // =========================================================

    fun copySkinFromPlayer(csUsername: String, mcUsername: String) {

        if (mcUsername.isBlank()) {
            onError("Minecraft username daalo")
            return
        }

        val skinUrl = "https://minotar.net/skin/$mcUsername"
        onLoading(true)

        scope.launch {
            try {
                val accessible = checkUrlAccessible(skinUrl)
                if (!accessible) {
                    withContext(Dispatchers.Main) {
                        onLoading(false)
                        onError("Player '$mcUsername' nahi mila!")
                    }
                    return@launch
                }

                prefs.edit()
                    .putString("skin_url_" + csUsername, skinUrl)
                    .putLong("skin_last_update_" + csUsername, System.currentTimeMillis())
                    .apply()

                skinCache.remove(csUsername)

                withContext(Dispatchers.Main) {
                    onLoading(false)
                    onSuccess("✅ $mcUsername ka skin copy ho gaya!")
                }

            } catch (e: Exception) {
                Log.e(TAG, "copySkinFromPlayer failed", e)
                withContext(Dispatchers.Main) {
                    onLoading(false)
                    onError("Copy failed: ${e.message}")
                }
            }
        }
    }

    // =========================================================
    // METHOD 4: Default Skin Reset
    // =========================================================

    fun resetToDefault(username: String, useAlex: Boolean = false) {

        val url  = if (useAlex) URL_ALEX else URL_STEVE
        val name = if (useAlex) "Alex"   else "Steve"

        onLoading(true)

        scope.launch {
            try {
                prefs.edit()
                    .putString("skin_url_" + username, url)
                    .apply()

                skinCache.remove(username)

                withContext(Dispatchers.Main) {
                    onLoading(false)
                    onSuccess("✅ Default $name skin restore ho gaya!")
                }

            } catch (e: Exception) {
                Log.e(TAG, "resetToDefault failed", e)
                withContext(Dispatchers.Main) {
                    onLoading(false)
                    onError("Reset failed: ${e.message}")
                }
            }
        }
    }

    // =========================================================
    // METHOD 5: Current Skin Fetch
    // =========================================================

    fun getCurrentSkin(username: String, onResult: (String) -> Unit) {
        scope.launch {
            try {
                val url = prefs.getString("skin_url_" + username, URL_STEVE) ?: URL_STEVE
                withContext(Dispatchers.Main) {
                    onResult(url)
                }

            } catch (e: Exception) {
                Log.e(TAG, "getCurrentSkin failed", e)
                withContext(Dispatchers.Main) {
                    onResult(URL_STEVE)
                }
            }
        }
    }

    // =========================================================
    // Validation
    // =========================================================

    private fun validateSkinUrl(url: String): String? {
        if (!URLUtil.isValidUrl(url))
            return "Valid URL nahi hai!"
        if (!url.startsWith("https://"))
            return "Sirf HTTPS URLs allowed hain!"
        if (ALLOWED_DOMAINS.none { url.contains(it) })
            return "Yeh domain allowed nahi!\n" +
                   "Allowed: ${ALLOWED_DOMAINS.joinToString(", ")}"
        return null
    }

    private suspend fun checkUrlAccessible(url: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = CONNECT_TIMEOUT
                conn.readTimeout    = READ_TIMEOUT
                conn.requestMethod  = "HEAD"
                val code = conn.responseCode
                conn.disconnect()
                code in 200..299
            } catch (e: Exception) {
                Log.w(TAG, "URL check failed: $url", e)
                false
            }
        }
    }

    private suspend fun getUrlContentSize(url: String): Long {
        return withContext(Dispatchers.IO) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = CONNECT_TIMEOUT
                val size = conn.contentLengthLong
                conn.disconnect()
                size
            } catch (e: Exception) {
                0L
            }
        }
    }

    // =========================================================
    // Image Helpers
    // =========================================================

    private fun isValidSkinDimensions(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        return (w == 64 && h == 64) || (w == 64 && h == 32)
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadBitmapFromUri failed", e)
            null
        }
    }

    private fun bitmapToBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }
}
