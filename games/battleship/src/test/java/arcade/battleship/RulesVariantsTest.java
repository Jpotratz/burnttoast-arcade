// Tests for the rules-pack additions: Sea Battle no-touch placement, salvo
// volley selection, and the classic 10x10 multiplier.

package arcade.battleship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import arcade.core.Coord;

class RulesVariantsTest {

	private static char[][] emptyGrid(int size) {
		char[][] g = new char[size][size];
		ShipPlacement.clear(g);
		return g;
	}

	// ---- no-touch placement ---------------------------------------------------

	@Test
	void noTouchRejectsSideAndDiagonalAdjacency() {
		char[][] g = emptyGrid(10);
		ShipPlacement.place(g, 4, 4, true, 3, 'Y'); // occupies (4..6, 4)
		assertFalse(ShipPlacement.fitsAt(g, 10, 4, 5, true, 3, true), "side adjacency");
		assertFalse(ShipPlacement.fitsAt(g, 10, 7, 5, false, 2, true), "diagonal adjacency at (7,5)");
		assertFalse(ShipPlacement.fitsAt(g, 10, 3, 3, false, 2, true), "diagonal adjacency at (3,3)");
		assertTrue(ShipPlacement.fitsAt(g, 10, 4, 6, true, 3, true), "one row of water between is legal");
		assertTrue(ShipPlacement.fitsAt(g, 10, 8, 4, false, 2, true), "one column of water between is legal");
	}

	@Test
	void noTouchStillAllowsPlainRulesAdjacency() {
		char[][] g = emptyGrid(10);
		ShipPlacement.place(g, 4, 4, true, 3, 'Y');
		assertTrue(ShipPlacement.fitsAt(g, 10, 4, 5, true, 3, false), "classic rules allow touching");
	}

	@Test
	void randomNoTouchFleetHasNoAdjacentShips() {
		BoardState b = new BoardState(10);
		b.placeFleetRandomly(new Random(42), true);
		for (int x = 0; x < 10; x++) {
			for (int y = 0; y < 10; y++) {
				char icon = b.shipPositions[x][y];
				if (icon == ShipPlacement.EMPTY) {
					continue;
				}
				for (int dx = -1; dx <= 1; dx++) {
					for (int dy = -1; dy <= 1; dy++) {
						int nx = x + dx, ny = y + dy;
						if (nx < 0 || nx >= 10 || ny < 0 || ny >= 10) {
							continue;
						}
						char n = b.shipPositions[nx][ny];
						assertTrue(n == ShipPlacement.EMPTY || n == icon,
								"ships " + icon + " and " + n + " touch at (" + x + "," + y + ")");
					}
				}
			}
		}
	}

	@Test
	void impossibleNoTouchPlacementThrowsInsteadOfHanging() {
		// The 5-ship fleet with mandatory water gaps cannot fit on 6x6.
		BoardState b = new BoardState(6);
		assertThrows(IllegalStateException.class, () -> b.placeFleetRandomly(new Random(1), true));
	}

	// ---- salvo volleys ----------------------------------------------------------

	@Test
	void volleyReturnsDistinctUnfiredCells() {
		BoardState b = new BoardState(9);
		HuntTargetOpponent ai = new HuntTargetOpponent(new Random(5));
		List<Coord> volley = ai.chooseVolley(b, 5);
		assertEquals(5, volley.size());
		Set<Coord> unique = new HashSet<>(volley);
		assertEquals(5, unique.size(), "volley must not repeat cells");
		for (Coord c : volley) {
			assertFalse(b.shotsFired[c.x()][c.y()]);
		}
	}

	@Test
	void volleyPrioritizesFinishingAFoundShip() {
		BoardState b = new BoardState(9);
		ShipPlacement.place(b.shipPositions, 3, 4, true, 4, 'T');
		b.fireAt(4, 4);
		b.fireAt(5, 4); // two collinear hits: extensions are (3,4) and (6,4)
		HuntTargetOpponent ai = new HuntTargetOpponent(new Random(7));
		List<Coord> volley = ai.chooseVolley(b, 3);
		assertTrue(volley.containsAll(List.of(new Coord(3, 4), new Coord(6, 4))),
				"both line extensions must be in the volley, got " + volley);
	}

	@Test
	void volleyLargerThanRemainingCellsReturnsWhatExists() {
		BoardState b = new BoardState(6);
		for (int x = 0; x < 6; x++) {
			for (int y = 0; y < 6; y++) {
				if (x + y > 1) {
					b.fireAt(x, y);
				}
			}
		}
		// only (0,0), (1,0), (0,1) unfired
		HuntTargetOpponent ai = new HuntTargetOpponent(new Random(9));
		assertEquals(3, ai.chooseVolley(b, 5).size());
	}

	@Test
	void randomOpponentVolleyIsAlsoDistinct() {
		BoardState b = new BoardState(6);
		List<Coord> volley = new RandomOpponent(new Random(3)).chooseVolley(b, 6);
		assertEquals(6, new HashSet<>(volley).size());
	}

	// ---- classic 10x10 ----------------------------------------------------------

	@Test
	void classicTenByTenMultiplier() {
		assertEquals(1.75, BattleshipScorer.multiplierFor(10));
		assertEquals(1.5, BattleshipScorer.multiplierFor(9));
		assertEquals(2.0, BattleshipScorer.multiplierFor(12));
	}
}
