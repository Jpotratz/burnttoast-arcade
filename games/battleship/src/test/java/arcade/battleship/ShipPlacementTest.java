// Tests for the pure, 0-based ship-placement logic.

package arcade.battleship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class ShipPlacementTest {

	private static char[][] emptyGrid(int size) {
		char[][] g = new char[size][size];
		ShipPlacement.clear(g);
		return g;
	}

	@Test
	void shipFitsOnAnEmptyBoard() {
		char[][] g = emptyGrid(6);
		assertTrue(ShipPlacement.fitsAt(g, 6, 0, 0, true, 5));
		assertTrue(ShipPlacement.fitsAt(g, 6, 0, 0, false, 5));
		assertTrue(ShipPlacement.fitsAt(g, 6, 1, 5, true, 5)); // ends exactly at the edge
	}

	@Test
	void shipDoesNotFitWhenItRunsOffTheBoard() {
		char[][] g = emptyGrid(6);
		assertFalse(ShipPlacement.fitsAt(g, 6, 2, 0, true, 5)); // 2+5 > 6
		assertFalse(ShipPlacement.fitsAt(g, 6, 0, 4, false, 3)); // 4+3 > 6
		assertFalse(ShipPlacement.fitsAt(g, 6, -1, 0, true, 2));
		assertFalse(ShipPlacement.fitsAt(g, 6, 0, -1, false, 2));
	}

	@Test
	void shipDoesNotFitWhenItOverlapsAnother() {
		char[][] g = emptyGrid(6);
		ShipPlacement.place(g, 2, 2, true, 3, 'R'); // occupies (2,2)(3,2)(4,2)
		assertFalse(ShipPlacement.fitsAt(g, 6, 3, 0, false, 4)); // crosses (3,2)
		assertFalse(ShipPlacement.fitsAt(g, 6, 0, 2, true, 3)); // touches (2,2)
		assertTrue(ShipPlacement.fitsAt(g, 6, 0, 3, true, 3)); // clear row below
	}

	@Test
	void placeStampsExactlyTheShipCells() {
		char[][] g = emptyGrid(6);
		ShipPlacement.place(g, 1, 4, true, 3, 'Y');
		int stamped = 0;
		for (int x = 0; x < 6; x++) {
			for (int y = 0; y < 6; y++) {
				if (g[x][y] != ShipPlacement.EMPTY) {
					stamped++;
					assertEquals('Y', g[x][y]);
					assertEquals(4, y);
				}
			}
		}
		assertEquals(3, stamped);
	}

	@Test
	void placesFullFleetOnBeginnerBoardWithoutHangingOrOverlapping() {
		Fleet fleet = new Fleet();
		char[][] g = emptyGrid(6);
		ShipPlacement.placeFleetRandomly(g, 6, fleet.lengths(), fleet.icons(), new Random(42));
		int shipCells = 0;
		for (int x = 0; x < 6; x++) {
			for (int y = 0; y < 6; y++) {
				if (g[x][y] != ShipPlacement.EMPTY) {
					shipCells++;
				}
			}
		}
		// 5+4+3+3+2 == 17 cells; any overlap would make this smaller.
		assertEquals(17, shipCells);
	}
}
