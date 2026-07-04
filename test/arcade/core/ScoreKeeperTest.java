// Tests for the game-agnostic scoring math: streaks, caps, multipliers.

package arcade.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ScoreKeeperTest {

	@Test
	void streakMultipliesConsecutiveAwards() {
		ScoreKeeper k = new ScoreKeeper(1.0);
		assertEquals(100, k.addStreaked(100)); // x1
		assertEquals(200, k.addStreaked(100)); // x2
		assertEquals(300, k.addStreaked(100)); // x3
		assertEquals(600, k.score());
	}

	@Test
	void streakFactorIsCapped() {
		ScoreKeeper k = new ScoreKeeper(1.0);
		for (int i = 0; i < 10; i++) {
			k.addStreaked(100);
		}
		assertEquals(ScoreKeeper.MAX_STREAK_FACTOR, k.streakFactor());
		assertEquals(ScoreKeeper.MAX_STREAK_FACTOR * 100, k.addStreaked(100));
	}

	@Test
	void breakStreakResetsToTimesOne() {
		ScoreKeeper k = new ScoreKeeper(1.0);
		k.addStreaked(100);
		k.addStreaked(100);
		k.breakStreak();
		assertEquals(1, k.streakFactor());
		assertEquals(100, k.addStreaked(100));
	}

	@Test
	void difficultyMultiplierScalesEverything() {
		ScoreKeeper k = new ScoreKeeper(1.5);
		assertEquals(150, k.addStreaked(100)); // 100 x1 x1.5
		assertEquals(300, k.addStreaked(100)); // 100 x2 x1.5
		assertEquals(75, k.addFlat(50));
		assertEquals(525, k.score());
	}

	@Test
	void flatAwardsDoNotTouchTheStreak() {
		ScoreKeeper k = new ScoreKeeper(1.0);
		k.addStreaked(100);
		k.addFlat(500);
		assertEquals(2, k.streakFactor()); // still primed for x2
	}

	@Test
	void zeroMultiplierDisablesScoring() {
		ScoreKeeper k = new ScoreKeeper(0);
		k.addStreaked(100);
		k.addFlat(1000);
		assertEquals(0, k.score());
	}

	@Test
	void negativeMultiplierIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> new ScoreKeeper(-1));
	}
}
