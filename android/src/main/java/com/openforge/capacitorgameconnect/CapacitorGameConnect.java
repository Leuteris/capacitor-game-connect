package com.openforge.capacitorgameconnect;

import android.content.Intent;
import android.util.Log;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import com.google.android.gms.games.AnnotatedData;
import com.google.android.gms.games.GamesSignInClient;
import com.google.android.gms.games.PlayGames;
import com.google.android.gms.games.SnapshotsClient;
import com.google.android.gms.games.SnapshotsClient.DataOrConflict;
import com.google.android.gms.games.leaderboard.LeaderboardScore;
import com.google.android.gms.games.leaderboard.LeaderboardVariant;
import com.google.android.gms.games.snapshot.SnapshotMetadata;
import com.google.android.gms.games.snapshot.SnapshotMetadataChange;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import  com.google.android.gms.games.snapshot.Snapshot;
import java.io.IOException;

public class CapacitorGameConnect {

    private AppCompatActivity activity;
    private static final String TAG = "CapacitorGameConnect";

    public CapacitorGameConnect(AppCompatActivity activity) {
        this.activity = activity;
    }

    /**
     * * Method to sign-in a user to Google Play Services
     *
     * @param call as PluginCall
     * @param resultCallback as SignInCallback
     */
    public void signIn(PluginCall call, final SignInCallback resultCallback) {
        Log.i(TAG, "SignIn method called");
        GamesSignInClient gamesSignInClient = PlayGames.getGamesSignInClient(this.activity);

        gamesSignInClient
            .isAuthenticated()
            .addOnCompleteListener(
                isAuthenticatedTask -> {
                    boolean isAuthenticated = (isAuthenticatedTask.isSuccessful() && isAuthenticatedTask.getResult().isAuthenticated());

                    if (isAuthenticated) {
                        Log.i(TAG, "User is already authenticated");
                        resultCallback.success();
                    } else {
                        gamesSignInClient
                            .signIn()
                            .addOnCompleteListener(
                                data -> {
                                    Log.i(TAG, "Sign-in completed successful");
                                    resultCallback.success();
                                }
                            )
                            .addOnFailureListener(e -> resultCallback.error(e.getMessage()));
                    }
                }
            )
            .addOnFailureListener(e -> resultCallback.error(e.getMessage()));
    }

  public void saveGame(PluginCall call) {
    Log.i(TAG, "saveGame method called");

    String data = call.getString("data");
    String snapshotId = call.getString("snapshotID");

    byte[] byteArray = data.getBytes(java.nio.charset.StandardCharsets.UTF_8);

    SnapshotsClient snapshotsClient = PlayGames.getSnapshotsClient(this.activity);

    snapshotsClient.open(snapshotId, true)
        .addOnCompleteListener(task -> {
          Snapshot snapshot = task.getResult().getData();

          if (snapshot != null) {
            //call of the method writeSnapshot params : the snapshot and the data we
            //want to save with a description
            writeSnapshot(snapshot, byteArray, "description")
                .addOnCompleteListener(t -> {
                  if (t.isSuccessful()) {
                    Log.i(TAG, "saveGame completed successful");
                  } else {
                    Log.e("ERR", "saveGame failed " + t.getException());
                  }
                });
          }
        });
  }

  public void loadGame(PluginCall call) {
    Log.i(TAG, "load game called");

    String snapshotId = call.getString("snapshotID");
    loadSnapshot(snapshotId)
        .addOnSuccessListener(data -> {
          Log.i(TAG, "load game completed successfully: " + new String(data));
          JSObject result = new JSObject();
          result.put("snapshot_data", new String(data));
          call.resolve(result);
        })
        .addOnFailureListener(
            new OnFailureListener() {
              @Override
              public void onFailure(@NonNull Exception e) {
                Log.e("ERR", "saveGame failed " + e.getMessage());
                call.reject("Error loading game" + e.getMessage());
              }
            }
        );
  }

    /**
     * * Method to fetch the logged in Player
     *
     * @param resultCallback as PlayerResultCallback
     */
    public void fetchUserInformation(final PlayerResultCallback resultCallback) {
        PlayGames
            .getPlayersClient(this.activity)
            .getCurrentPlayer()
            .addOnSuccessListener(
                player -> {
                    resultCallback.success(player);
                }
            )
            .addOnFailureListener(e -> resultCallback.error(e.getMessage()));
    }

    /**
     * * Method to display the Leaderboards view from Google Play Services SDK
     *
     * @param call as PluginCall
     * @param startActivityIntent as ActivityResultLauncher<Intent>
     */
    public void showLeaderboard(PluginCall call, ActivityResultLauncher<Intent> startActivityIntent) {
        Log.i(TAG, "showLeaderboard has been called");
        var leaderboardID = call.getString("leaderboardID");
        PlayGames
            .getLeaderboardsClient(this.activity)
            .getLeaderboardIntent(leaderboardID)
            .addOnSuccessListener(
                new OnSuccessListener<Intent>() {
                    @Override
                    public void onSuccess(Intent intent) {
                        startActivityIntent.launch(intent);
                    }
                }
            );
    }

    /**
     * * Method to submit a score to the Google Play Services SDK
     *
     * @param call as PluginCall
     */
    public void submitScore(PluginCall call) {
        Log.i(TAG, "submitScore has been called");
        var leaderboardID = call.getString("leaderboardID");
        var totalScoreAmount = call.getInt("totalScoreAmount");
        PlayGames.getLeaderboardsClient(this.activity).submitScore(leaderboardID, totalScoreAmount);
    }

    /**
     * * Method to display the Achievements view from Google Play SDK
     *
     * @param startActivityIntent as ActivityResultLauncher<Intent>
     */
    public void showAchievements(ActivityResultLauncher<Intent> startActivityIntent) {
        Log.i(TAG, "showAchievements has been called");
        PlayGames
            .getAchievementsClient(this.activity)
            .getAchievementsIntent()
            .addOnSuccessListener(
                new OnSuccessListener<Intent>() {
                    @Override
                    public void onSuccess(Intent intent) {
                        startActivityIntent.launch(intent);
                    }
                }
            );
    }

    /**
     * * Method to unlock an achievement
     *
     */
    public void unlockAchievement(PluginCall call) {
        Log.i(TAG, "unlockAchievement has been called");
        var achievementID = call.getString("achievementID");
        PlayGames.getAchievementsClient(this.activity).unlock(achievementID);
    }

    /**
     * * Method to increment the progress of an achievement
     *
     */
    public void incrementAchievementProgress(PluginCall call) {
        Log.i(TAG, "incrementAchievementProgress has been called");
        var achievementID = call.getString("achievementID");
        var pointsToIncrement = call.getInt("pointsToIncrement");
        PlayGames.getAchievementsClient(this.activity).increment(achievementID, pointsToIncrement);
    }

    /**
     * * Method to get the total player score from a leaderboard
     *
     */
    public void getUserTotalScore(PluginCall call) {
        Log.i(TAG, "getUserTotalScore has been called");
        var leaderboardID = call.getString("leaderboardID");
        var leaderboardScore = PlayGames
            .getLeaderboardsClient(this.activity)
            .loadCurrentPlayerLeaderboardScore(leaderboardID, LeaderboardVariant.TIME_SPAN_ALL_TIME, LeaderboardVariant.COLLECTION_PUBLIC);
        leaderboardScore
            .addOnSuccessListener(
                new OnSuccessListener<AnnotatedData<LeaderboardScore>>() {
                    @Override
                    public void onSuccess(AnnotatedData<LeaderboardScore> leaderboardScoreAnnotatedData) {
                        if (leaderboardScore != null) {
                            long userTotalScore = 0;
                            if (leaderboardScore.getResult().get() != null) {
                                userTotalScore = leaderboardScore.getResult().get().getRawScore();
                            }
                            JSObject result = new JSObject();
                            result.put("player_score", userTotalScore);
                            call.resolve(result);
                        }
                    }
                }
            )
            .addOnFailureListener(
                new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        call.reject("Error getting player score" + e.getMessage());
                    }
                }
            );
    }

  private Task<SnapshotMetadata> writeSnapshot(Snapshot snapshot, byte[] data, String desc) {
    // Set the data payload for the snapshot
    snapshot.getSnapshotContents().writeBytes(data);

    // Create the change operation
    SnapshotMetadataChange metadataChange = new SnapshotMetadataChange.Builder()
        //.setCoverImage(coverImage)
        .setDescription(desc)
        .build();


    SnapshotsClient snapshotsClient =
        PlayGames.getSnapshotsClient(this.activity);

    // Commit the operation
    return snapshotsClient.commitAndClose(snapshot, metadataChange);
  }

  private Task<byte[]> loadSnapshot(String snapshotID) {
    SnapshotsClient snapshotsClient =
        PlayGames.getSnapshotsClient(this.activity);

    // In the case of a conflict, the most recently modified version of this snapshot will be used.
    int conflictResolutionPolicy = SnapshotsClient.RESOLUTION_POLICY_MOST_RECENTLY_MODIFIED;

    // Open the saved game using its name
    return snapshotsClient.open(snapshotID, true, conflictResolutionPolicy)
        .addOnFailureListener(new OnFailureListener() {
          @Override
          public void onFailure(@NonNull Exception e) {
            Log.e(TAG, "Error while opening Snapshot.", e);
          }
        }).continueWith(new Continuation<DataOrConflict<Snapshot>, byte[]>() {
          @Override
          public byte[] then(@NonNull Task<SnapshotsClient.DataOrConflict<Snapshot>> task) throws Exception {
            Snapshot snapshot = task.getResult().getData();

            // Opening the snapshot was a success and any conflicts have been resolved.
            try {
              // Extract the raw data from the snapshot.
              return snapshot.getSnapshotContents().readFully();
            } catch (IOException e) {
              Log.e(TAG, "Error while reading Snapshot.", e);
            }

            return null;
          }
        });
  }
}
