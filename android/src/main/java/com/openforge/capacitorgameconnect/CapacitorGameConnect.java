package com.openforge.capacitorgameconnect;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.getcapacitor.JSObject;
import com.getcapacitor.PluginCall;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
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
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import  com.google.android.gms.games.snapshot.Snapshot;
import com.openforge.capacitorgameconnect.glicko2.Rating;
import com.openforge.capacitorgameconnect.glicko2.RatingCalculator;
import com.openforge.capacitorgameconnect.glicko2.RatingPeriodResults;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class CapacitorGameConnect {

    private AppCompatActivity activity;
    private static final String TAG = "CapacitorGameConnect";

    private static final long REQUEST_TIMEOUT_MS = 8000;

    private double TAU = 0.75d;
    private double defaultVolatility = 0.09d;
    // rating that can be lost or gained with a single game
    private int maxRatingDelta = 700;
    private int minRating = 400;
    private int maxRating = 4000;

    private RatingCalculator ratingCalculator = new RatingCalculator(defaultVolatility, TAU);

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

        signIn(resultCallback, gamesSignInClient);
    }

    public void isAuthenticated(PluginCall call, final AuthenticatedCallback resultCallback) {
        Log.i(TAG, "isAuthenticated method called");
        GamesSignInClient gamesSignInClient = PlayGames.getGamesSignInClient(this.activity);

        gamesSignInClient
                .isAuthenticated()
                .addOnCompleteListener(
                        isAuthenticatedTask -> {
                            boolean isAuthenticated = (isAuthenticatedTask.isSuccessful() && isAuthenticatedTask.getResult().isAuthenticated());

                            if (isAuthenticated) {
                                Log.i(TAG, "User is already authenticated");
                            } else {
                                Log.i(TAG, "User is not authenticated");
                            }
                            resultCallback.success(isAuthenticated);
                        }
                )
                .addOnFailureListener(e -> resultCallback.error(e.getMessage()));
    }

  public void saveGame(PluginCall call) {
    Log.i(TAG, "saveGame method called");

    String data = call.getString("data");
    String snapshotId = call.getString("snapshotID");

    byte[] byteArray = data.getBytes(StandardCharsets.UTF_8);

    SnapshotsClient snapshotsClient = PlayGames.getSnapshotsClient(this.activity);
    int conflictResolutionPolicy = SnapshotsClient.RESOLUTION_POLICY_MANUAL;

    snapshotsClient.open(snapshotId, true, conflictResolutionPolicy)
        .addOnFailureListener(e -> {
             Log.e(TAG, "Error while opening Snapshot.", e);
             call.reject("Error while opening Snapshot: " + e.getMessage());
        })
            .addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    Log.e(TAG, "Failed to open snapshot for saving");
                    call.reject("Failed to open snapshot for saving");
                    return;
                }

                DataOrConflict<Snapshot> result = task.getResult();

                if (result.isConflict()) {
                    // Conflict detected
                    Log.i(TAG, "Conflict detected...");
                    SnapshotsClient.SnapshotConflict conflict = result.getConflict();
                    Snapshot snapshot = conflict.getSnapshot();
                    Snapshot conflictingSnapshot = conflict.getConflictingSnapshot();

                    try {
                        byte[] existingData = snapshot.getSnapshotContents().readFully();
                        byte[] conflictingData = conflictingSnapshot.getSnapshotContents().readFully();

                        // Merge the snapshot data
                        byte[] mergedData = mergeSnapshotData(existingData, conflictingData, byteArray);

                        // Write merged data
                        snapshot.getSnapshotContents().writeBytes(mergedData);

                        snapshotsClient.resolveConflict(conflict.getConflictId(), snapshot)
                                .addOnCompleteListener(resolveTask -> {
                                    if (resolveTask.isSuccessful()) {
                                        Log.i(TAG, "Conflict resolved, saving snapshot...");
                                        writeSnapshot(snapshot, mergedData, "Merged data")
                                                .addOnCompleteListener(t -> {
                                                    if (t.isSuccessful()) {
                                                        Log.i(TAG, "saveGame completed successfully");
                                                    } else {
                                                        Log.e("ERR", "saveGame failed " + t.getException());
                                                        call.reject("Failed saveGame, writeSnapshot: ");
                                                    }
                                                })
                                                .addOnFailureListener(e -> {
                                                    snapshotsClient.discardAndClose(snapshot);
                                                    Log.e(TAG, "Failed saveGame, writeSnapshot", e);
                                                    call.reject("Failed saveGame, writeSnapshot: " + e.getMessage());
                                                });
                                    } else {
                                        Log.e(TAG, "Failed to resolve conflict");
                                        call.reject("Failed to resolve conflict.");
                                    }
                                });
                    } catch (IOException e) {
                        Log.e(TAG, "Error while merging snapshots.", e);
                        call.reject("Error while merging snapshots: " + e.getMessage());
                    }
                } else {
                    // No conflict, proceed with normal save
                    Snapshot snapshot = result.getData();
                    writeSnapshot(snapshot, byteArray, "Saving snapshot")
                            .addOnCompleteListener(t -> {
                                if (t.isSuccessful()) {
                                    Log.i(TAG, "saveGame completed successfully");
                                } else {
                                    Log.e("ERR", "saveGame failed " + t.getException());
                                }
                            })
                            .addOnFailureListener(e -> {
                                snapshotsClient.discardAndClose(snapshot);
                                Log.e(TAG, "Failed saveGame, writeSnapshot", e);
                                call.reject("Failed saveGame, writeSnapshot: " + e.getMessage());
                            });
                }
            });
  }

  public void loadGame(PluginCall call) {
    Log.i(TAG, "load game called");

    // call.reject("loadGame custom error");

    String snapshotId = call.getString("snapshotID");
    loadSnapshot(call, snapshotId)
        .addOnSuccessListener(data -> {
            if (data == null) {
                call.reject("Loading snapshot null");
            }
            Log.i(TAG, "load game completed successfully: " + new String(data));
            JSObject result = new JSObject();
            result.put("snapshot_data", new String(data));
            call.resolve(result);
        })
        .addOnFailureListener(
            new OnFailureListener() {
              @Override
              public void onFailure(@NonNull Exception e) {
                Log.e("ERR", "loadGame failed: " + e.getMessage());
                call.reject("Error loading game: " + e.getMessage());
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
       // resultCallback.error("fetchUserInformation custom error");
        Log.i("CapacitorGameConnect", "fetchUserInformation called");
        Handler handler = new Handler(Looper.getMainLooper());
        Runnable timeoutRunnable = () -> {
            resultCallback.error("fetchUserInformation request timed out");
        };

        handler.postDelayed(timeoutRunnable, REQUEST_TIMEOUT_MS);

        PlayGames
                .getPlayersClient(this.activity)
                .getCurrentPlayer()
                .addOnSuccessListener(
                        player -> {
                            Log.i("CapacitorGameConnect", "fetchUserInformation success");
                            handler.removeCallbacks(timeoutRunnable); // Cancel timeout
                            resultCallback.success(player);
                        }
                )
                .addOnFailureListener(e -> {
                    handler.removeCallbacks(timeoutRunnable); // Cancel timeout
                    handleFailure(resultCallback, e);
                });
    }

    private static void handleFailure(PlayerResultCallback resultCallback, Exception e) {
        if (e instanceof ApiException) {
            Log.i(TAG, "fetchUserInformation failed...");
            ApiException apiException = (ApiException) e;
            int statusCode = apiException.getStatusCode();

            switch (statusCode) {
                case CommonStatusCodes.CANCELED:
                    // User canceled the sign-in, continue without it
                    Log.i(TAG, "User canceled sign-in. Proceeding without Play Games features.");
                    resultCallback.success(null); // Indicate sign-in is skipped
                    break;

                case CommonStatusCodes.SIGN_IN_REQUIRED:
                    // Sign-in required, but allow the user to skip it
                    Log.i(TAG, "Sign-in required but skipped. Proceeding without Play Games features.");
                    resultCallback.error("Sign-in failed with status code: " + statusCode);

                    break;

                default:
                    // Other errors, log and continue
                    Log.e(TAG, "Sign-in failed with status code: " + statusCode);
                    resultCallback.error("Sign-in failed with status code: " + statusCode);
                    break;
            }
        } else {
            Log.e(TAG, "Sign-in failed with exception: " + e.getMessage());
            resultCallback.error(e.getMessage());
        }
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

    public void calculateRating(PluginCall call) {
        try {
            Log.i(TAG, "calculateRating has been called");
            var currentPuzzleRating =  call.getDouble("puzzleRating");
            var puzzleRatingDeviation =  call.getDouble("puzzleRatingDeviation");
            var currentPlayerRating =  call.getDouble("playerRating");
            var playerRatingDeviation =  call.getDouble("playerRatingDeviation");
            var puzzleSolved =  call.getBoolean("puzzleSolved");

            currentPuzzleRating = Math.max(currentPuzzleRating, minRating);
            Rating puzzleRating = new Rating(currentPuzzleRating, puzzleRatingDeviation, defaultVolatility, 0);
            Rating playerRating = new Rating(currentPlayerRating, playerRatingDeviation, defaultVolatility, 0);

            RatingPeriodResults results = new RatingPeriodResults();
             if (puzzleSolved) {
                 results.addResult(playerRating, puzzleRating);
             } else {
                 results.addResult(puzzleRating, playerRating);
             }

            ratingCalculator.updateRatings(results);
            playerRating.setRating(Math.max(currentPlayerRating - maxRatingDelta, Math.min(playerRating.getRating(), currentPlayerRating + maxRatingDelta)));

            if (playerRating.getRating() < minRating) {
                playerRating.setRating(minRating);
            }

            if (playerRating.getRating() > maxRating) {
                playerRating.setRating(maxRating);
            }

            Log.i(TAG, "rating: " + playerRating.getRating());
            Log.i(TAG, "ratingDeviation: " + playerRating.getRatingDeviation());

            JSObject result = new JSObject();
            result.put("rating", playerRating.getRating());
            result.put("ratingDeviation", playerRating.getRatingDeviation());
            call.resolve(result);
        } catch (Exception e) {
            Log.i(TAG, "Error calculate rating: "+ e.getMessage());

            call.reject("Error calculate rating: " + e.getMessage());
        }
    }

/*    private void onCompleteIsAuthenticated(SignInCallback resultCallback, Task<AuthenticationResult> isAuthenticatedTask, GamesSignInClient gamesSignInClient) {
        boolean isAuthenticated = (isAuthenticatedTask.isSuccessful() && isAuthenticatedTask.getResult().isAuthenticated());
        if (isAuthenticated) {
            Log.i(TAG, "User is authenticated");
            resultCallback.success(true);
        } else {
            Log.i(TAG, "User is not authenticated");
            signIn(resultCallback, gamesSignInClient);
        }
    }*/

    private void signIn(SignInCallback resultCallback, GamesSignInClient gamesSignInClient) {
        gamesSignInClient
                .signIn()
                .addOnCompleteListener(
                        data -> {
                            boolean isAuthenticated = (data.isSuccessful() && data.getResult().isAuthenticated());

                            Log.i(TAG, "Sign-in completed successful, isAuthenticated: " + isAuthenticated);
                            resultCallback.success(isAuthenticated);
                        }
                )
                .addOnFailureListener(e -> onSignInFailure(resultCallback, e));
    }

    private static void onSignInFailure(SignInCallback resultCallback, Exception e) {
        if (e instanceof ApiException) {
            Log.i(TAG, "User sign-in failed...");
            ApiException apiException = (ApiException) e;
            int statusCode = apiException.getStatusCode();

            // TODO network error????
            switch (statusCode) {
                case CommonStatusCodes.CANCELED:
                    // User canceled the sign-in, continue without it
                    Log.i(TAG, "User canceled sign-in. Proceeding without Play Games features.");
                    resultCallback.success(false); // Indicate sign-in is skipped
                    break;

                case CommonStatusCodes.SIGN_IN_REQUIRED:
                    // Sign-in required, but allow the user to skip it
                    Log.i(TAG, "Sign-in required but skipped. Proceeding without Play Games features.");
                    resultCallback.success(false); // Indicate sign-in is skipped
                    break;

                default:
                    // Other errors, log and continue
                    Log.e(TAG, "Sign-in failed with status code: " + statusCode);
                    resultCallback.error("Sign-in failed with status code: " + statusCode);
                    break;
            }
        } else {
            Log.e(TAG, "Sign-in failed with exception: " + e.getMessage());
            resultCallback.error(e.getMessage());
        }
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

  private Task<byte[]> loadSnapshot(PluginCall call, String snapshotID) {
    SnapshotsClient snapshotsClient = PlayGames.getSnapshotsClient(this.activity);

      Handler handler = new Handler(Looper.getMainLooper());
      Runnable timeoutRunnable = () -> {
          call.reject("loadSnapshot request timed out");
      };

      handler.postDelayed(timeoutRunnable, REQUEST_TIMEOUT_MS);

      // In the case of a conflict, the most recently modified version of this snapshot will be used.
    int conflictResolutionPolicy = SnapshotsClient.RESOLUTION_POLICY_MANUAL;

      // Open the saved game using its name
    return snapshotsClient.open(snapshotID, true, conflictResolutionPolicy)
        .addOnFailureListener(new OnFailureListener() {
          @Override
          public void onFailure(@NonNull Exception e) {
            Log.e(TAG, "Error while opening Snapshot.", e);
            handler.removeCallbacks(timeoutRunnable); // Cancel timeout
            call.reject("Error while opening Snapshot: " + e.getMessage());
          }
        }) .continueWith(task -> {
                try {
                    handler.removeCallbacks(timeoutRunnable);

                    if (!task.isSuccessful()) {
                        Log.e(TAG, "Failed to load snapshot task");
                        call.reject("Failed to load snapshot task.");
                        return null;
                    }

                    DataOrConflict<Snapshot> result = task.getResult();

                    if (result.isConflict()) {
                        Log.i(TAG, "Conflict detected...");
                        // Conflict detected
                        SnapshotsClient.SnapshotConflict conflict = result.getConflict();
                        Snapshot snapshot1 = conflict.getSnapshot();
                        Snapshot snapshot2 = conflict.getConflictingSnapshot();

                        // Read both snapshots
                        byte[] data1 = snapshot1.getSnapshotContents().readFully();
                        byte[] data2 = snapshot2.getSnapshotContents().readFully();

                        // Merge data (custom logic needed here)
                        byte[] mergedData = mergeSnapshotData(data1, data2);

                        // Write merged data to snapshot1 (you can also create a new one)
                        snapshot1.getSnapshotContents().writeBytes(mergedData);

                        return snapshotsClient.resolveConflict(conflict.getConflictId(), snapshot1)
                                .continueWith(resolveTask -> {
                                    if (!resolveTask.isSuccessful()) {
                                        Log.e(TAG, "Failed to resolve snapshot conflict");
                                        call.reject("Failed to resolve snapshot conflict.");
                                        return null;//TODO auto to null den tha etaksei NPE
                                    }
                                    Log.i(TAG, "Conflict resolved, loading snapshot...");
                                    return mergedData;
                                }).getResult();
                    }

                    // No conflict, return the snapshot data
                    Snapshot snapshot = result.getData();
                    return snapshot.getSnapshotContents().readFully();
                } catch (IOException e) {
                    Log.e(TAG, "Error while reading Snapshot.", e);
                    call.reject("Error while reading Snapshot: " + e.getMessage());
                } catch (Exception e) {
                    Log.e(TAG, "Failed to load snapshot", e);
                    call.reject("Failed to load snapshot: " + e.getMessage());
                }
                return null;
            });
  }

    private byte[] mergeSnapshotData(byte[] data1, byte[] data2) {
        try {
            String dataStr1 = new String(data1, StandardCharsets.UTF_8);
            String dataStr2 = new String(data2, StandardCharsets.UTF_8);

            String[] dataArray1 = dataStr1.split("\\|");
            String[] dataArray2 = dataStr2.split("\\|");

            String remove1 = dataArray1[29];
            String starterPack1 = dataArray1[36];
            String premiumPack1 = dataArray1[37];
            String nowTimestamp1 = dataArray1[1];

            String remove2 = dataArray2[29];
            String starterPack2 = dataArray2[36];
            String premiumPack2 = dataArray2[37];
            String nowTimestamp2 = dataArray2[1];

            if (premiumPack1.equals("true")) {
                return data1;
            }
            if (premiumPack2.equals("true")) {
                return data2;
            }
            if (starterPack1.equals("true")) {
                return data1;
            }
            if (starterPack2.equals("true")) {
                return data2;
            }
            if (remove1.equals("true")) {
                return data1;
            }
            if (remove2.equals("true")) {
                return data2;
            }

            if (Long.parseLong(nowTimestamp1) >= Long.parseLong(nowTimestamp2)) {
                return data1;
            } else {
                return data2;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error merging snapshot data", e);
            return data1; // Return original data if merging fails
        }
    }

    private byte[] mergeSnapshotData(byte[] data1, byte[] data2, byte[] data3) {
        try {
            String dataStr1 = new String(data1, StandardCharsets.UTF_8);
            String dataStr2 = new String(data2, StandardCharsets.UTF_8);
            String dataStr3 = new String(data3, StandardCharsets.UTF_8);

            String[] dataArray1 = dataStr1.split("\\|");
            String[] dataArray2 = dataStr2.split("\\|");
            String[] dataArray3 = dataStr3.split("\\|");

            String remove1 = dataArray1[29];
            String starterPack1 = dataArray1[36];
            String premiumPack1 = dataArray1[37];
            String nowTimestamp1 = dataArray1[1];

            String remove2 = dataArray2[29];
            String starterPack2 = dataArray2[36];
            String premiumPack2 = dataArray2[37];
            String nowTimestamp2 = dataArray2[1];

            String remove3 = dataArray3[29];
            String starterPack3 = dataArray3[36];
            String premiumPack3 = dataArray3[37];
            String nowTimestamp3 = dataArray3[1];


            if (premiumPack1.equals("true")) {
                return data1;
            }
            if (premiumPack2.equals("true")) {
                return data2;
            }
            if (premiumPack3.equals("true")) {
                return data3;
            }

            if (starterPack1.equals("true")) {
                return data1;
            }
            if (starterPack2.equals("true")) {
                return data2;
            }
            if (starterPack3.equals("true")) {
                return data3;
            }

            if (remove1.equals("true")) {
                return data1;
            }
            if (remove2.equals("true")) {
                return data2;
            }
            if (remove3.equals("true")) {
                return data3;
            }

            if (Long.parseLong(nowTimestamp1) >= Long.parseLong(nowTimestamp2)) {
                if (Long.parseLong(nowTimestamp1) >= Long.parseLong(nowTimestamp3)) {
                    return data1;
                } else {
                    return data3;
                }
            } else {
                if (Long.parseLong(nowTimestamp2) >= Long.parseLong(nowTimestamp3)) {
                    return data2;
                } else {
                    return data3;
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "Error merging snapshot data", e);
            return data1; // Return original data if merging fails
        }
    }

}
