// Tests for the pure board rules: shot resolution, tallies, fleet destruction.

package arcade.battleship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import arcade.core.Coord;

class BoardStateTest {

	@Test
	void firingAtOpenWaterIsAMiss() {
		BoardState b = new BoardState(6);
		assertEquals(Shot.MISS, b.fireAt(0, 0));
		assertEquals(1, b.shots);
		assertEquals(0, b.hits);
	}

	@Test
	void firingAtAShipCellIsAHit() {
		BoardState b = new BoardState(6);
		ShipPlacement.place(b.shipPositions, 1, 1, true, 5, 'R');
		assertEquals(Shot.HIT, b.fireAt(1, 1));
		assertEquals(1, b.hits);
		assertTrue(b.isActiveHit(1, 1));
	}

	@Test
	void firingTheLastCellOfAShipSinksIt() {
		BoardState b = new BoardState(6);
		ShipPlacement.place(b.shipPositions, 0, 0, true, 2, 'P');
		assertEquals(Shot.HIT, b.fireAt(0, 0));
		assertEquals(Shot.SUNK, b.fireAt(1, 0));
		assertTrue(b.fleet.shipForIcon('P').isSunk());
		assertFalse(b.isActiveHit(0, 0)); // sunk hits are no longer "active"
		assertEquals(4, b.fleet.shipsRemaining());
	}

	@Test
	void firingTheSameCellTwiceIsRejectedAndNotDoubleCounted() {
		BoardState b = new BoardState(6);
		ShipPlacement.place(b.shipPositions, 0, 0, true, 2, 'P');
		assertEquals(Shot.HIT, b.fireAt(0, 0));
		assertEquals(Shot.ALREADY_FIRED, b.fireAt(0, 0));
		assertEquals(1, b.shots);
		assertEquals(1, b.hits);
	}

	@Test
	void sinkingEveryShipCellDestroysTheFleet() {
		BoardState b = new BoardState(9);
		b.placeFleetRandomly(new Random(7));
		for (int x = 0; x < 9; x++) {
			for (int y = 0; y < 9; y++) {
				if (b.shipPositions[x][y] != ShipPlacement.EMPTY) {
					assertNotEquals(Shot.MISS, b.fireAt(x, y));
				}
			}
		}
		assertTrue(b.fleet.isDestroyed());
		assertEquals(0, b.fleet.shipsRemaining());
		assertEquals(17, b.hits);
	}

	@Test
	void randomUnfiredCellNeverRepeatsAndExhaustsTheBoard() {
		BoardState b = new BoardState(6);
		Random rng = new Random(1);
		Set<Coord> seen = new HashSet<>();
		for (int i = 0; i < 36; i++) {
			Coord c = b.randomUnfiredCell(rng);
			assertTrue(seen.add(c), "repeated cell " + c);
			b.fireAt(c.x(), c.y());
		}
		assertNull(b.randomUnfiredCell(rng));
	}

	@Test
	void parityFilterOnlyReturnsMatchingCells() {
		BoardState b = new BoardState(6);
		for (Coord c : b.unfiredCells(2)) {
			assertEquals(0, (c.x() + c.y()) % 2);
		}
		assertEquals(18, b.unfiredCells(2).size()); // half of a 6x6 board
	}
}
