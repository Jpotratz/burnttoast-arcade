// arcade.battleship
// The simplest opponent: fire at a uniformly random unfired cell. Deterministic
// given a seeded Random, so it's easy to unit-test.

package arcade.battleship;

import java.util.Random;

import arcade.core.Coord;
import arcade.core.MoveInfo;
import arcade.core.Opponent;

public class RandomOpponent implements Opponent<BoardState, Coord> {

	private final Random rng;

	public RandomOpponent() {
		this(new Random());
	}

	public RandomOpponent(Random rng) {
		this.rng = rng;
	}

	@Override
	public Coord chooseMove(BoardState target) {
		return target.randomUnfiredCell(rng);
	}

	@Override
	public MoveInfo lastMoveInfo() {
		return new MoveInfo("random", -1);
	}
}
