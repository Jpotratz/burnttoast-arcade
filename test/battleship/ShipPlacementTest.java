// James Potratz CSIS 222 - MCC (v2)
// Unit tests for the pure ship-placement logic. These run with NO JavaFX toolkit
// because ShipPlacement has no UI dependencies -- that separation is exactly what
// makes it testable.

package battleship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class ShipPlacementTest {

	// The standard 5-ship fleet, in required placement order (carrier .. patrol).
	private static final int[] LENGTHS = { 5, 4, 3, 3, 2 };
	private static final char[] ICONS = { 'R', 'T', 'Y', 'S', 'P' };
	private static final int TOTAL_CELLS = 5 + 4 + 3 + 3 + 2; // 17

	@Test
	void shipFitsOnAnEmptyBoard() {
		char[][] grid = newGrid(10);
		assertTrue(ShipPlacement.fitsAt(grid, 10, 1, 1, true, 5));
	}

	@Test
	void shipDoesNotFitWhenItRunsOffTheBoard() {
		// Beginner board: size 7 -> usable ship indices 1..5. A length-5 ship
		// starting at x=3 horizontally would need indices 3..7, which runs off.
		char[][] grid = newGrid(7);
		assertFalse(ShipPlacement.fitsAt(grid, 7, 3, 1, true, 5));
	}

	@Test
	void shipDoesNotFitOnTheCoordinateRowOrColumn() {
		char[][] grid = newGrid(10);
		assertFalse(ShipPlacement.fitsAt(grid, 10, 0, 1, true, 3)); // column 0 = labels
		assertFalse(ShipPlacement.fitsAt(grid, 10, 1, 0, false, 3)); // row 0 = labels
	}

	@Test
	void shipDoesNotFitWhenItOverlapsAnother() {
		char[][] grid = newGrid(10);
		// Place a horizontal ship occupying (2,2)(3,2)(4,2).
		ShipPlacement.place(grid, 2, 2, true, 3, 'R');
		// A vertical ship starting at (3,2) would cross the occupied cell (3,2).
		assertFalse(ShipPlacement.fitsAt(grid, 10, 3, 2, false, 3));
	}

	// The headline test. The ORIGINAL code could infinite-loop placing the fleet on
	// the tiny beginner board (orientation was locked once, then only the position
	// was re-rolled). Here we place the whole fleet thousands of times and assert it
	// (a) always terminates and (b) always yields a valid, non-overlapping layout.
	// @Timeout converts a hang into a FAILING test instead of freezing the run.
	@Test
	@Timeout(value = 30, unit = TimeUnit.SECONDS)
	void placesFullFleetOnBeginnerBoardWithoutHangingOrOverlapping() {
		Random rng = new Random(12345); // fixed seed -> reproducible runs
		for (int trial = 0; trial < 5000; trial++) {
			char[][] grid = newGrid(7); // beginner: the tightest board
			ShipPlacement.placeFleetRandomly(grid, 7, LENGTHS, ICONS, rng);

			// No overlaps: exactly the sum of ship lengths worth of cells are filled.
			assertEquals(TOTAL_CELLS, countNonEmpty(grid),
					"trial " + trial + ": all 17 ship cells must be placed with no overlaps");
			// Each ship occupies exactly its own length.
			for (int s = 0; s < LENGTHS.length; s++) {
				assertEquals(LENGTHS[s], countIcon(grid, ICONS[s]),
						"trial " + trial + ": ship '" + ICONS[s] + "' must occupy exactly " + LENGTHS[s] + " cells");
			}
		}
	}

	// ---- small helpers ----

	private static char[][] newGrid(int size) {
		char[][] grid = new char[size][size];
		ShipPlacement.clear(grid);
		return grid;
	}

	private static int countNonEmpty(char[][] grid) {
		int n = 0;
		for (char[] row : grid) {
			for (char c : row) {
				if (c != ShipPlacement.EMPTY) {
					n++;
				}
			}
		}
		return n;
	}

	private static int countIcon(char[][] grid, char icon) {
		int n = 0;
		for (char[] row : grid) {
			for (char c : row) {
				if (c == icon) {
					n++;
				}
			}
		}
		return n;
	}
}
