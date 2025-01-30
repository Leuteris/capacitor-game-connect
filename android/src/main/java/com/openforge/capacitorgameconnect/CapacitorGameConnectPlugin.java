package com.openforge.capacitorgameconnect;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
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
import java.io.IOException;
import java.io.InputStream;

@CapacitorPlugin(name = "CapacitorGameConnect")
public class CapacitorGameConnectPlugin extends Plugin {

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
    }

    @PluginMethod
    public void signIn(PluginCall call) {
        implementation.signIn(call, new SignInCallback() {
            @Override
            public void success(boolean isAuthenticated) {
                if (!isAuthenticated) {
                    JSObject ret = new JSObject();
                    ret.put("player_id", null);
                    ret.put("player_name", null);
                    call.resolve(ret);
                }

                implementation.fetchUserInformation(new PlayerResultCallback() {
                    @Override
                    public void success(Player player) {
                        if (player == null) {
                            JSObject ret = new JSObject();
                            ret.put("player_id", null);
                            ret.put("player_name", null);
                            ret.put("player_image", null);
                            call.resolve(ret);
                        } else {
                            resolvePlayerData(player, call);
                        }
                    }

                    @Override
                    public void error(String message) {
                        call.reject(message);
                    }
                });
            }

            @Override
            public void error(String message) {
                call.reject(message);
            }
        });
    }

    @PluginMethod
    public void isAuthenticated(PluginCall call) {
        implementation.isAuthenticated(call, new AuthenticatedCallback() {
            @Override
            public void success(boolean isAuthenticated) {

                if (!isAuthenticated) {
                    JSObject ret = new JSObject();
                    ret.put("player_id", null);
                    ret.put("player_name", null);
                    call.resolve(ret);
                }

                implementation.fetchUserInformation(new PlayerResultCallback() {
                    @Override
                    public void success(Player player) {
                        if (player == null) {
                            JSObject ret = new JSObject();
                            ret.put("player_id", null);
                            ret.put("player_name", null);
                            ret.put("player_image", null);
                            call.resolve(ret);
                        } else {

                            resolvePlayerData(player, call);

                        }
                    }

                    @Override
                    public void error(String message) {
                        Log.i("CapacitorGameConnect", "fetchUserInformation error: " + message);
                        call.reject(message);
                    }
                });
            }

            @Override
            public void error(String message) {
                call.reject(message);
            }
        });
    }

    private void resolvePlayerData(Player player, PluginCall call) {
        String playerId = player.getPlayerId();
        String playerName = player.getDisplayName();
        Uri imageUri = player.getHiResImageUri();
        if (imageUri == null) {
            imageUri = player.getIconImageUri();
        }

        if (imageUri != null) {
            ImageManager imageManager = ImageManager.create(getContext());
            imageManager.loadImage(new ImageManager.OnImageLoadedListener() {
                @Override
                public void onImageLoaded(Uri uri, Drawable drawable, boolean isImmediate) {
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
                    //TODO timeout?
                }
            }, imageUri);
        } else {
            JSObject ret = new JSObject();
            ret.put("player_id", playerId);
            ret.put("player_name", playerName);
            ret.put("player_image", null);
            call.resolve(ret);
        }
    }

    @PluginMethod
    public void showLeaderboard(PluginCall call) {
        implementation.showLeaderboard(call, this.startActivityIntent);
        call.resolve();
    }

    @PluginMethod
    public void saveGame(PluginCall call) {
        implementation.saveGame(call);
        call.resolve();
    }

    @PluginMethod
    public void loadGame(PluginCall call) {
        implementation.loadGame(call);
    }

    @PluginMethod
    public void submitScore(PluginCall call) {
        implementation.submitScore(call);
        call.resolve();
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
        implementation.calculateRating(call);
    }

}
