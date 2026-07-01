// James Potratz CSIS 222 - MCC (v2)
// Unit tests for the pure board rules (fireAt) and the computer's targeting
// (randomUnfiredCell). No JavaFX required.

package battleship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class BoardStateTest {

	@Test
	void firingAtOpenWaterIsAMiss() {
		BoardState board = new BoardState(10); // starts all empty water
		assertEquals(BoardState.Shot.MISS, board.fireAt(3, 3));
		assertEquals(1, board.shots);
		assertEquals(0, board.hits);
	}

	@Test
	void firingAtAShipCellIsAHit() {
		BoardState board = new BoardState(10);
		// Drop the patrol ship (length 2, icon 'P') at (1,1)-(2,1).
		ShipPlacement.place(board.shipPositions, 1, 1, true, 2, 'P');
		assertEquals(BoardState.Shot.HIT, board.fireAt(1, 1));
		assertEquals(1, board.hits);
	}

	@Test
	void firingTheLastCellOfAShipSinksIt() {
		BoardState board = new BoardState(10);
		ShipPlacement.place(board.shipPositions, 1, 1, true, 2, 'P');
		board.fireAt(1, 1); // HIT
		assertEquals(BoardState.Shot.SUNK, board.fireAt(2, 1)); // sinks the patrol
		assertTrue(board.fleet.shipForIcon('P').isSunk);
		assertEquals(4, board.fleet.shipsRemaining()); // 4 of 5 still afloat
		assertFalse(board.fleet.isDestroyed());
	}

	@Test
	void firingTheSameCellTwiceIsRejectedAndNotDoubleCounted() {
		BoardState board = new BoardState(10);
		board.fireAt(4, 4);
		assertEquals(BoardState.Shot.ALREADY_FIRED, board.fireAt(4, 4));
		assertEquals(1, board.shots); // the rejected shot must not count
	}

	@Test
	void sinkingEveryShipCellDestroysTheFleet() {
		BoardState board = new BoardState(10);
		board.placeFleetRandomly(new Random(42));
		// Fire at every occupied cell.
		for (int x = 1; x < board.size; x++) {
			for (int y = 1; y < board.size; y++) {
				if (board.shipPositions[x][y] != ShipPlacement.EMPTY) {
					board.fireAt(x, y);
				}
			}
		}
		assertTrue(board.fleet.isDestroyed());
		assertEquals(5 + 4 + 3 + 3 + 2, board.hits); // all 17 ship cells struck
	}

	@Test
	void computerNeverPicksACellItAlreadyFiredAt() {
		BoardState board = new BoardState(7); // small board = quick to exhaust
		Random rng = new Random(7);
		int[] cell;
		int guard = 0;
		while ((cell = board.randomUnfiredCell(rng)) != null) {
			assertFalse(board.shotsFired[cell[0]][cell[1]], "picked an already-fired cell");
			board.fireAt(cell[0], cell[1]);
			if (++guard > 1000) {
				break; // safety net; should never trigger
			}
		}
		// Once every playable cell is spent, there is nothing left to target.
		assertNull(board.randomUnfiredCell(rng));
	}
}
