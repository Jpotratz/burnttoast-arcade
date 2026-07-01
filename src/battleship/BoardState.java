// James Potratz CSIS 222 - MCC (v2)
// All of the state and rules for ONE side's board, with NO JavaFX: the ship grid,
// which cells have been fired at, that side's fleet, and shot/hit tallies. The
// game has two of these -- the player's board (the computer fires at it) and the
// enemy board (the player fires at it). Keeping this pure means the shot rules and
// the computer's targeting can be unit-tested without launching the UI.

package battleship;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BoardState {

	// Result of firing at a single cell.
	public enum Shot {
		ALREADY_FIRED, MISS, HIT, SUNK
	}

	public final int size;
	public final char[][] shipPositions; // 'Z' == open water, else a ship icon
	public final boolean[][] shotsFired; // has this cell already been fired at
	public final Fleet fleet;

	public int shots; // total shots fired at this board
	public int hits; // shots that struck a ship

	public BoardState(int size) {
		this.size = size;
		this.shipPositions = new char[size][size];
		ShipPlacement.clear(shipPositions);
		this.shotsFired = new boolean[size][size];
		this.fleet = new Fleet();
	}

	// Randomly place this board's fleet. Delegates to the pure ShipPlacement core.
	public void placeFleetRandomly(Random rng) {
		ShipPlacement.placeFleetRandomly(shipPositions, size, fleet.lengths(), fleet.icons(), rng);
	}

	// Resolve a shot at (x, y). Pure logic; the caller renders the outcome.
	public Shot fireAt(int x, int y) {
		if (shotsFired[x][y]) {
			return Shot.ALREADY_FIRED;
		}
		shotsFired[x][y] = true;
		shots++;

		char c = shipPositions[x][y];
		if (c == ShipPlacement.EMPTY) {
			return Shot.MISS;
		}
		hits++;
		Ship ship = fleet.shipForIcon(c);
		boolean sank = ship.hit();
		return sank ? Shot.SUNK : Shot.HIT;
	}

	// Computer targeting: pick a random playable cell (indices 1..size-1) that has
	// not been fired at yet. Returns {x, y}, or null if the whole board is spent.
	public int[] randomUnfiredCell(Random rng) {
		List<int[]> free = new ArrayList<>();
		for (int x = 1; x < size; x++) {
			for (int y = 1; y < size; y++) {
				if (!shotsFired[x][y]) {
					free.add(new int[] { x, y });
				}
			}
		}
		if (free.isEmpty()) {
			return null;
		}
		return free.get(rng.nextInt(free.size()));
	}
}
