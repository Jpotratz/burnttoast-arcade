// Tests for battleship's scoring values layered on ScoreKeeper.

package arcade.battleship;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BattleshipScorerTest {

	private static final Ship PATROL = new Ship(2, 'P', "patrol");

	@Test
	void hitPaysBasePointsAndStreaksUp() {
		BattleshipScorer s = new BattleshipScorer(6, false); // x1 board
		assertEquals(100, s.onShotResult(Shot.HIT, PATROL));
		assertEquals(200, s.onShotResult(Shot.HIT, PATROL));
		assertEquals(300, s.score());
	}

	@Test
	void missPaysNothingAndBreaksTheStreak() {
		BattleshipScorer s = new BattleshipScorer(6, false);
		s.onShotResult(Shot.HIT, PATROL);
		assertEquals(0, s.onShotResult(Shot.MISS, null));
		assertEquals(100, s.onShotResult(Shot.HIT, PATROL)); // back to x1
	}

	@Test
	void sunkPaysTheStreakedHitPlusLengthBonus() {
		BattleshipScorer s = new BattleshipScorer(6, false);
		Ship carrier = new Ship(5, 'R', "carrier");
		// x1 hit (100) + 50 x length 5 (250) = 350
		assertEquals(350, s.onShotResult(Shot.SUNK, carrier));
	}

	@Test
	void difficultyMultipliers() {
		assertEquals(1.0, BattleshipScorer.multiplierFor(6));
		assertEquals(1.5, BattleshipScorer.multiplierFor(9));
		assertEquals(2.0, BattleshipScorer.multiplierFor(12));
	}

	@Test
	void cheatModeZeroesAllScoring() {
		BattleshipScorer s = new BattleshipScorer(12, true);
		s.onShotResult(Shot.HIT, PATROL);
		s.onShotResult(Shot.SUNK, PATROL);
		s.onGameEnd(true, 5, 20, 17);
		assertEquals(0, s.score());
	}

	@Test
	void endOfGameBonusesAddUp() {
		BattleshipScorer s = new BattleshipScorer(6, false);
		// accuracy 5/10 = 50% -> 500; 2 survivors -> 600; victory -> 1000
		assertEquals(2100, s.onGameEnd(true, 2, 10, 5));
	}

	@Test
	void lossSkipsTheVictoryBonus() {
		BattleshipScorer s = new BattleshipScorer(6, false);
		// accuracy 5/10 -> 500; 0 survivors -> 0; no victory bonus
		assertEquals(500, s.onGameEnd(false, 0, 10, 5));
	}

	@Test
	void accuracyDisplayHandlesZeroShots() {
		assertEquals("--", BattleshipScorer.accuracyPercent(0, 0));
		assertEquals("50", BattleshipScorer.accuracyPercent(10, 5));
		assertEquals("33.3", BattleshipScorer.accuracyPercent(3, 1));
	}
}
