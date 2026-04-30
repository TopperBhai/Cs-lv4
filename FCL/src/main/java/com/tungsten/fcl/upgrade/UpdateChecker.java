package com.tungsten.fcl.upgrade;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.widget.Toast;

import com.google.gson.reflect.TypeToken;
import com.tungsten.fcl.R;
import com.tungsten.fclcore.task.Schedulers;
import com.tungsten.fclcore.task.Task;
import com.tungsten.fclcore.util.gson.JsonUtils;
import com.tungsten.fclcore.util.io.NetworkUtils;
import com.tungsten.fcllibrary.util.LocaleUtils;

import java.util.ArrayList;

public class UpdateChecker {

    // ✅ Legacy update URLs removed. Firebase implementation pending.
    private static UpdateChecker instance;

    public static UpdateChecker getInstance() {
        if (instance == null) {
            instance = new UpdateChecker();
        }
        return instance;
    }

    private boolean isChecking = false;

    public boolean isChecking() {
        return isChecking;
    }

    public UpdateChecker() {
    }

    // ✅ TODO: Implement Firebase Realtime Database/Firestore update check here
    public Task<?> checkForFirebaseUpdate(Context context, boolean showAlert) {
        return Task.runAsync(() -> {
            isChecking = true;
            if (showAlert) {
                Schedulers.androidUIThread().execute(() ->
                    Toast.makeText(context, "Update check (Firebase) coming soon", Toast.LENGTH_SHORT).show()
                );
            }
            // TODO: Query Firebase Realtime DB or Firestore for latest version
            // - Compare with getCurrentVersionCode(context)
            // - If update available, call showUpdateDialog(context, version)
            isChecking = false;
        });
    }

    public static int getCurrentVersionCode(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo packageInfo = pm.getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
            throw new IllegalStateException("Can't get current version code");
        }
    }

    private void showUpdateDialog(Context context, RemoteVersion version) {
        Schedulers.androidUIThread().execute(() -> {
            UpdateDialog dialog = new UpdateDialog(context, version);
            dialog.show();
        });
    }

    public static boolean isIgnore(Context context, int code) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("launcher", Context.MODE_PRIVATE);
        return sharedPreferences.getInt("ignore_update", -1) == code;
    }

    public static void setIgnore(Context context, int code) {
        SharedPreferences sharedPreferences = context.getSharedPreferences("launcher", Context.MODE_PRIVATE);
        @SuppressLint("CommitPrefEdits") SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("ignore_update", code);
        editor.apply();
    }

}
