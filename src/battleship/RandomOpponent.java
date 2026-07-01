// James Potratz CSIS 222 - MCC (v2)
// The simplest opponent: fire at a uniformly random cell that hasn't been shot
// yet. Pure and deterministic given a seeded Random, so it's easy to unit-test.
// Also serves as the fallback whenever the Ollama opponent can't produce a move.

package battleship;

import java.util.Random;

public class RandomOpponent implements Opponent {

	private final Random rng;

	public RandomOpponent() {
		this(new Random());
	}

	public RandomOpponent(Random rng) {
		this.rng = rng;
	}

	@Override
	public int[] chooseTarget(BoardState target) {
		return target.randomUnfiredCell(rng);
	}
}
