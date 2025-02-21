package com.openforge.capacitorgameconnect;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.android.gms.common.images.ImageManager;
import com.google.android.gms.games.PlayGamesSdk;
import com.google.android.gms.games.Player;

import java.io.ByteArrayOutputStream;

@CapacitorPlugin(name = "CapacitorGameConnect")
public class CapacitorGameConnectPlugin extends Plugin {

    private static final String TAG = "CapacitorGameConnectPlg";

    private CapacitorGameConnect implementation;
    private ActivityResultLauncher<Intent> startActivityIntent;

    @Override
    public void load() {
        PlayGamesSdk.initialize(getContext());
        startActivityIntent =
                getActivity()
                        .registerForActivityResult(
                                new ActivityResultContracts.StartActivityForResult(),
                                new ActivityResultCallback<ActivityResult>() {
                                    @Override
                                    public void onActivityResult(ActivityResult result) {
                                        // Add same code that you want to add in onActivityResult method
                                    }
                                }
                        );
        implementation = new CapacitorGameConnect(getActivity());


    /*    String android_id = Secure.getString(getContext().getContentResolver(),
                Secure.ANDROID_ID);
        Log.i("CapacitorGameConnect", "Device ID: "+ android_id); */
    }

    @PluginMethod
    public void signIn(PluginCall call) {
        try {
            implementation.signIn(call, new SignInCallback() {
                @Override
                public void success(boolean isAuthenticated) {
                    if (!isAuthenticated) {
                        Log.e("CapacitorGameConnect", "signIn, isAuthenticated false");
                        call.reject("PLAYER_NOT_AUTH");
                        return;
                    }

                    implementation.fetchUserInformation(new PlayerResultCallback() {
                        @Override
                        public void success(Player player) {
                            if (player == null) {
                                Log.e("CapacitorGameConnect",
                                        "fetchUserInformation inside signIn, null player");
                                call.reject("fetchUserInformation inside signIn, null player");
                            } else {
                                resolvePlayerData(player, call);
                            }
                        }

                        @Override
                        public void error(String message) {
                            Log.e("CapacitorGameConnect", "fetchUserInformation inside signIn failed: " + message);
                            call.reject("fetchUserInformation inside signIn failed: " + message);
                        }
                    });
                }

                @Override
                public void error(String message) {
                    call.reject(message);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed signIn", e);
            call.reject("Failed signIn: " + e.getMessage());
        }
    }

    @PluginMethod
    public void isAuthenticated(PluginCall call) {
        try {
            implementation.isAuthenticated(call, new AuthenticatedCallback() {
                @Override
                public void success(boolean isAuthenticated) {

                    if (!isAuthenticated) { // user is not sign-in, default account will be used
                        JSObject ret = new JSObject();
                        ret.put("player_id", null);
                        ret.put("player_name", null);
                        call.resolve(ret);
                        return;
                    }

                    implementation.fetchUserInformation(new PlayerResultCallback() {
                        @Override
                        public void success(Player player) {
                            if (player == null) {
                                Log.e("CapacitorGameConnect",
                                        "fetchUserInformation inside isAuthenticated, null player");
                                call.reject("fetchUserInformation inside isAuthenticated, null player");
                            } else {
                                resolvePlayerData(player, call);
                            }
                        }

                        @Override
                        public void error(String message) {
                            Log.e("CapacitorGameConnect",
                                    "fetchUserInformation inside isAuthenticated failed: " + message);
                            call.reject("fetchUserInformation inside isAuthenticated failed: " + message);
                        }
                    });
                }

                @Override
                public void error(String message) {
                    Log.e("CapacitorGameConnect",
                            "isAuthenticated failed: " + message);
                    call.reject("isAuthenticated failed: " + message);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed isAuthenticated", e);
            call.reject("Failed isAuthenticated: " + e.getMessage());
        }
    }

    private void resolvePlayerData(Player player, PluginCall call) {
        Log.i("CapacitorGameConnect", "resolvePlayerData called");
        String playerId = player.getPlayerId();
        String playerName = player.getDisplayName();
        Uri imageUri = player.getHiResImageUri();
        if (imageUri == null) {
            imageUri = player.getIconImageUri();
        }

        if (imageUri != null) {
            Handler handler = new Handler(Looper.getMainLooper());
            Runnable timeoutRunnable = () -> {
                call.reject("resolvePlayerData onImageLoaded timed out");
            };

            handler.postDelayed(timeoutRunnable, 8000);
            ImageManager imageManager = ImageManager.create(getContext());
            imageManager.loadImage(new ImageManager.OnImageLoadedListener() {
                @Override
                public void onImageLoaded(Uri uri, Drawable drawable, boolean isImmediate) {
                    try {
                        handler.removeCallbacks(timeoutRunnable); // Cancel timeout
                        Log.i("CapacitorGameConnect", "onImageLoaded called");
                        if (drawable instanceof BitmapDrawable) {
                            Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();

                            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream);
                            byte[] byteArray = outputStream.toByteArray();
                            String base64String = Base64.encodeToString(byteArray, Base64.DEFAULT);

                            JSObject ret = new JSObject();
                            ret.put("player_id", playerId);
                            ret.put("player_name", playerName);
                            ret.put("player_image", base64String);
                            call.resolve(ret);
                        } else {
                            JSObject ret = new JSObject();
                            ret.put("player_id", playerId);
                            ret.put("player_name", playerName);
                            ret.put("player_image", null);
                            call.resolve(ret);
                        }
                        Log.i("CapacitorGameConnect", "onImageLoaded completed");
                    } catch (Exception e) {
                        Log.e(TAG, "Failed onImageLoaded", e);
                        call.reject("Failed onImageLoaded: " + e.getMessage());
                    }
                }
            }, imageUri);
        } else {
            JSObject ret = new JSObject();
            ret.put("player_id", playerId);
            ret.put("player_name", playerName);
            ret.put("player_image", null);
            call.resolve(ret);
        }
        Log.i("CapacitorGameConnect", "resolvePlayerData completed");
    }

    @PluginMethod
    public void showLeaderboard(PluginCall call) {
        try {
            implementation.showLeaderboard(call, this.startActivityIntent);
            call.resolve();
        } catch (Exception e) {
            Log.e(TAG, "Failed showLeaderboard", e);
            call.reject("Failed showLeaderboard: " + e.getMessage());
        }
    }

    @PluginMethod
    public void saveGame(PluginCall call) {
        try {
            implementation.saveGame(call);
            call.resolve();
        } catch (Exception e) {
            Log.e(TAG, "Failed saveGame", e);
            call.reject("Failed saveGame: " + e.getMessage());
        }
    }

    @PluginMethod
    public void loadGame(PluginCall call) {
        try {
            implementation.loadGame(call);
        } catch (Exception e) {
            Log.e(TAG, "Failed loadGame", e);
            call.reject("Failed loadGame: " + e.getMessage());
        }
    }

    @PluginMethod
    public void submitScore(PluginCall call) {
        try {
            implementation.submitScore(call);
            call.resolve();
        } catch (Exception e) {
            Log.e(TAG, "Failed submitScore", e);
            call.reject("Failed submitScore: " + e.getMessage());
        }
    }

    @PluginMethod
    public void showAchievements(PluginCall call) {
        implementation.showAchievements(this.startActivityIntent);
        call.resolve();
    }

    @PluginMethod
    public void unlockAchievement(PluginCall call) {
        implementation.unlockAchievement(call);
        call.resolve();
    }

    @PluginMethod
    public void incrementAchievementProgress(PluginCall call) {
        implementation.incrementAchievementProgress(call);
        call.resolve();
    }

    @PluginMethod
    public void getUserTotalScore(PluginCall call) {
        implementation.getUserTotalScore(call);
    }

    @PluginMethod
    public void calculateRating(PluginCall call) {
        try {
            implementation.calculateRating(call);
        } catch (Exception e) {
            Log.e(TAG, "Failed calculateRating", e);
            call.reject("Failed calculateRating: " + e.getMessage());
        }
    }

}
