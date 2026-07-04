// arcade.battleship
// Battleship's scoring VALUES, layered on the game-agnostic ScoreKeeper math.
//
//   hit          +100, on the streak track (consecutive hits pay x1, x2 ... x5)
//   sunk         the hit that sinks it also pays +50 x ship length, flat
//   miss         0 points, breaks the streak
//   game end     +accuracy% x 10, +300 per own surviving ship, +1000 for victory
//
// Everything scales with the difficulty multiplier (x1 / x1.5 / x2 for the
// 6/9/12 boards) and the reveal-the-fleet cheat zeroes the multiplier, so
// cheated games always score 0.

package arcade.battleship;

import arcade.core.ScoreKeeper;

public class BattleshipScorer {

	public static final int HIT_POINTS = 100;
	public static final int SUNK_BONUS_PER_LENGTH = 50;
	public static final int SURVIVING_SHIP_BONUS = 300;
	public static final int VICTORY_BONUS = 1000;

	private final ScoreKeeper keeper;

	public BattleshipScorer(int boardSize, boolean cheatEnabled) {
		this.keeper = new ScoreKeeper(cheatEnabled ? 0 : multiplierFor(boardSize));
	}

	public static double multiplierFor(int boardSize) {
		if (boardSize >= 12) {
			return 2.0;
		}
		if (boardSize >= 9) {
			return 1.5;
		}
		return 1.0;
	}

	// Record the result of one of the PLAYER's shots. Returns the points awarded
	// for this shot (for "+250" style UI popups).
	public int onShotResult(Shot result, Ship ship) {
		switch (result) {
		case HIT:
			return keeper.addStreaked(HIT_POINTS);
		case SUNK:
			return keeper.addStreaked(HIT_POINTS) + keeper.addFlat(SUNK_BONUS_PER_LENGTH * ship.length);
		case MISS:
			keeper.breakStreak();
			return 0;
		default:
			return 0;
		}
	}

	// Apply the end-of-game bonuses. Returns the points awarded.
	public int onGameEnd(boolean won, int survivingOwnShips, int shotsFired, int hits) {
		int awarded = 0;
		if (shotsFired > 0) {
			awarded += keeper.addFlat((int) Math.round(100.0 * hits / shotsFired * 10));
		}
		awarded += keeper.addFlat(SURVIVING_SHIP_BONUS * survivingOwnShips);
		if (won) {
			awarded += keeper.addFlat(VICTORY_BONUS);
		}
		return awarded;
	}

	public int score() {
		return keeper.score();
	}

	// The multiplier the NEXT hit will pay (for streak display in the UI).
	public int streakFactor() {
		return keeper.streakFactor();
	}

	// Human-readable accuracy for display: "--" before the first shot, otherwise
	// a percentage with up to one decimal.
	public static String accuracyPercent(int shots, int hits) {
		if (shots == 0) {
			return "--";
		}
		double pct = 100.0 * hits / shots;
		return (pct == Math.floor(pct)) ? String.valueOf((int) pct) : String.format("%.1f", pct);
	}
}
