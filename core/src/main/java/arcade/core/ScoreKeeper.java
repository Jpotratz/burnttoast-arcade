// arcade.core
// Game-agnostic scoring math: a running score with a consecutive-success streak
// multiplier and a game-wide difficulty multiplier. Games supply their own point
// VALUES (what a hit is worth, what bonuses exist) and call in; this class owns
// the arithmetic so every game's scoring behaves consistently.
//
// Streak semantics: the first streaked award pays x1, the next consecutive one
// x2, and so on up to MAX_STREAK_FACTOR. breakStreak() resets to x1.
// A difficulty multiplier of 0 disables scoring entirely (used for cheat modes).

package arcade.core;

public class ScoreKeeper {

	public static final int MAX_STREAK_FACTOR = 5;

	private final double multiplier;
	private int score = 0;
	private int streak = 0; // consecutive streaked awards so far

	public ScoreKeeper(double multiplier) {
		if (multiplier < 0) {
			throw new IllegalArgumentException("multiplier must be >= 0");
		}
		this.multiplier = multiplier;
	}

	// Award points that participate in the streak: pays base * streakFactor *
	// multiplier and extends the streak. Returns the points actually awarded.
	public int addStreaked(int base) {
		int awarded = (int) Math.round(base * streakFactor() * multiplier);
		score += awarded;
		streak++;
		return awarded;
	}

	// Award points unaffected by the streak (bonuses). Still scaled by difficulty.
	public int addFlat(int base) {
		int awarded = (int) Math.round(base * multiplier);
		score += awarded;
		return awarded;
	}

	public void breakStreak() {
		streak = 0;
	}

	// The factor the NEXT streaked award will pay (x1 .. x MAX_STREAK_FACTOR).
	public int streakFactor() {
		return Math.min(streak + 1, MAX_STREAK_FACTOR);
	}

	public int score() {
		return score;
	}

	public double multiplier() {
		return multiplier;
	}
}
