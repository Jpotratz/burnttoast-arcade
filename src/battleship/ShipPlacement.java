// James Potratz CSIS 222 - MCC (v2)
// Pure, JavaFX-free ship-placement logic, split out of gameboard so it can be
// unit-tested without starting the JavaFX toolkit.
//
// It operates purely on a char grid where 'Z' (EMPTY) means open water and any
// other character is a ship icon. Index 0 (row and column) is reserved for the
// coordinate labels, so ships only ever occupy indices [1, size-1).

package battleship;

import java.util.Random;

public class ShipPlacement {

	// Grid value meaning "open water / no ship here".
	public static final char EMPTY = 'Z';

	// Number of random tries to place a single ship before the caller gives up and
	// re-places the entire fleet from an empty board.
	private static final int MAX_ATTEMPTS = 500;

	// Can a ship of the given length sit at (x, y) with the given orientation
	// without running off the board or overlapping a ship that is already there?
	// horizontal == true grows along x (columns); false grows along y (rows).
	public static boolean fitsAt(char[][] grid, int size, int x, int y, boolean horizontal, int shipLength) {
		int arrayBoundary = size - 1;
		// Must start inside the playable area (index 0 holds coordinate labels).
		if (x < 1 || y < 1) {
			return false;
		}
		// Must not extend past the last usable index.
		if (horizontal) {
			if (x + shipLength > arrayBoundary) {
				return false;
			}
		} else {
			if (y + shipLength > arrayBoundary) {
				return false;
			}
		}
		// Every cell the ship would occupy must currently be empty water.
		for (int i = 0; i < shipLength; i++) {
			int gx = horizontal ? x + i : x;
			int gy = horizontal ? y : y + i;
			if (grid[gx][gy] != EMPTY) {
				return false;
			}
		}
		return true;
	}

	// Stamp a ship's icon into the grid. The caller must have confirmed fitsAt first.
	public static void place(char[][] grid, int x, int y, boolean horizontal, int shipLength, char icon) {
		for (int i = 0; i < shipLength; i++) {
			int gx = horizontal ? x + i : x;
			int gy = horizontal ? y : y + i;
			grid[gx][gy] = icon;
		}
	}

	// Reset the whole grid to open water.
	public static void clear(char[][] grid) {
		for (int r = 0; r < grid.length; r++) {
			for (int c = 0; c < grid.length; c++) {
				grid[r][c] = EMPTY;
			}
		}
	}

	// Try to place a single ship at a random position and orientation, re-rolling
	// BOTH every attempt up to MAX_ATTEMPTS. Re-rolling the orientation is the fix
	// for the old infinite loop: previously the orientation was locked once up
	// front, so an orientation that could not fit would spin forever. Returns true
	// if the ship was placed, false if no spot was found within the attempt cap.
	public static boolean tryPlaceRandom(char[][] grid, int size, int shipLength, char icon, Random rng) {
		int arrayBoundary = size - 1;
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			int x = randomInt(rng, 1, arrayBoundary);
			int y = randomInt(rng, 1, arrayBoundary);
			boolean horizontal = randomInt(rng, 1, 2) == 2;
			if (fitsAt(grid, size, x, y, horizontal, shipLength)) {
				place(grid, x, y, horizontal, shipLength, icon);
				return true;
			}
		}
		return false;
	}

	// Place the whole fleet, restarting from an empty board if any ship cannot be
	// placed within its attempt cap. Guaranteed to terminate. shipLengths and
	// shipIcons are parallel arrays in the order the ships should be placed.
	public static void placeFleetRandomly(char[][] grid, int size, int[] shipLengths, char[] shipIcons, Random rng) {
		boolean allPlaced = false;
		while (!allPlaced) {
			clear(grid);
			allPlaced = true;
			for (int s = 0; s < shipLengths.length; s++) {
				if (!tryPlaceRandom(grid, size, shipLengths[s], shipIcons[s], rng)) {
					allPlaced = false;
					break;
				}
			}
		}
	}

	// Random int in [min, max], both inclusive.
	private static int randomInt(Random rng, int min, int max) {
		return rng.nextInt((max - min) + 1) + min;
	}
}
