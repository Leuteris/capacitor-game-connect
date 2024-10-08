import type { PlayerScore } from './interfaces/player-score.interface';
import { SnapshotData } from './interfaces/snapshot-data.interface';
import { Rating } from './interfaces/rating.interface';

export interface CapacitorGameConnectPlugin {
  /**
   * * Method to sign-in a user
   *
   *
   */
  signIn(): Promise<{
    player_name: string;
    player_id: string;
  }>;

  isAuthenticated(): Promise<{
    player_name: string;
    player_id: string;
  }>;


  calculateRating(options: { puzzleSolved: boolean, puzzleRating: number,  puzzleRatingDeviation: number,  playerRating: number,  playerRatingDeviation: number }): Promise<Rating>;

  /**
   * * Method to display the Leaderboards
   *
   * @param leaderboardID as string
   */
  showLeaderboard(options: { leaderboardID: string }): Promise<void>;

  saveGame(options: { snapshotID: string, data: string }): Promise<void>;

  loadGame(options: { snapshotID: string }): Promise<SnapshotData>;

  /**
   * * Method to submit a score to the Google Play Services SDK
   *
   */
  submitScore(options: {
    leaderboardID: string;
    totalScoreAmount: number;
  }): Promise<void>;

  /**
   * * Method to display the Achievements view
   *
   */
  showAchievements(): Promise<void>;

  /**
   * * Method to unlock an achievement
   *
   */
  unlockAchievement(options: { achievementID: string }): Promise<void>;

  /**
   * * Method to increment the progress of an achievement
   */
  incrementAchievementProgress(options: {
    achievementID: string;
    pointsToIncrement: number;
  }): Promise<void>;

  /**
   * * Method to get total player score from a leaderboard
   *
   * @param options { leaderboardID: string }
   */
  getUserTotalScore(options: { leaderboardID: string }): Promise<PlayerScore>;
}
