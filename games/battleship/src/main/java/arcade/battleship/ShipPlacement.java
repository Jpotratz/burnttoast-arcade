// arcade.battleship
// Pure ship-placement logic over a char grid where EMPTY means open water and
// any other character is a ship icon. The grid is fully 0-based: every index
// [0, size) is playable. (v2 reserved index 0 for coordinate labels, which leaked
// an off-by-one into every consumer; labels are now purely a UI concern.)

package arcade.battleship;

import java.util.Random;

public class ShipPlacement {

	// Grid value meaning "open water / no ship here".
	public static final char EMPTY = 'Z';

	// Random tries to place a single ship before the caller restarts the fleet.
	private static final int MAX_ATTEMPTS = 500;

	// Can a ship of the given length sit at (x, y) with the given orientation
	// without running off the board or overlapping a ship already there?
	// horizontal == true grows along x (columns); false grows along y (rows).
	public static boolean fitsAt(char[][] grid, int size, int x, int y, boolean horizontal, int shipLength) {
		return fitsAt(grid, size, x, y, horizontal, shipLength, false);
	}

	// noTouch == true adds the Sea Battle placement rule: no other ship may
	// occupy any of the 8 cells surrounding any cell of this ship (no adjacency,
	// not even diagonal).
	public static boolean fitsAt(char[][] grid, int size, int x, int y, boolean horizontal, int shipLength,
			boolean noTouch) {
		if (x < 0 || y < 0) {
			return false;
		}
		if (horizontal ? (x + shipLength > size) : (y + shipLength > size)) {
			return false;
		}
		for (int i = 0; i < shipLength; i++) {
			int gx = horizontal ? x + i : x;
			int gy = horizontal ? y : y + i;
			if (grid[gx][gy] != EMPTY) {
				return false;
			}
			if (noTouch) {
				for (int dx = -1; dx <= 1; dx++) {
					for (int dy = -1; dy <= 1; dy++) {
						int nx = gx + dx;
						int ny = gy + dy;
						if (nx >= 0 && nx < size && ny >= 0 && ny < size && grid[nx][ny] != EMPTY) {
							return false;
						}
					}
				}
			}
		}
		return true;
	}

	// Stamp a ship's icon into the grid. The caller must have confirmed fitsAt.
	public static void place(char[][] grid, int x, int y, boolean horizontal, int shipLength, char icon) {
		for (int i = 0; i < shipLength; i++) {
			int gx = horizontal ? x + i : x;
			int gy = horizontal ? y : y + i;
			grid[gx][gy] = icon;
		}
	}

	// Reset the whole grid to open water.
	public static void clear(char[][] grid) {
		for (char[] row : grid) {
			java.util.Arrays.fill(row, EMPTY);
		}
	}

	// Try to place a single ship at a random position and orientation, re-rolling
	// BOTH position and orientation each attempt (re-rolling orientation is what
	// prevents the v1 infinite loop). Returns true if placed within the cap.
	public static boolean tryPlaceRandom(char[][] grid, int size, int shipLength, char icon, Random rng,
			boolean noTouch) {
		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			int x = rng.nextInt(size);
			int y = rng.nextInt(size);
			boolean horizontal = rng.nextBoolean();
			if (fitsAt(grid, size, x, y, horizontal, shipLength, noTouch)) {
				place(grid, x, y, horizontal, shipLength, icon);
				return true;
			}
		}
		return false;
	}

	public static boolean tryPlaceRandom(char[][] grid, int size, int shipLength, char icon, Random rng) {
		return tryPlaceRandom(grid, size, shipLength, icon, rng, false);
	}

	// Place the whole fleet, restarting from an empty board if any ship cannot be
	// placed within its attempt cap. shipLengths and shipIcons are parallel arrays
	// in placement order. Throws if the fleet cannot fit at all (e.g. no-touch on
	// a board that is too small) instead of spinning forever.
	public static void placeFleetRandomly(char[][] grid, int size, int[] shipLengths, char[] shipIcons, Random rng,
			boolean noTouch) {
		for (int restart = 0; restart < 10_000; restart++) {
			clear(grid);
			boolean allPlaced = true;
			for (int s = 0; s < shipLengths.length; s++) {
				if (!tryPlaceRandom(grid, size, shipLengths[s], shipIcons[s], rng, noTouch)) {
					allPlaced = false;
					break;
				}
			}
			if (allPlaced) {
				return;
			}
		}
		throw new IllegalStateException(
				"fleet cannot be placed on a " + size + "x" + size + " board" + (noTouch ? " with no-touch" : ""));
	}

	public static void placeFleetRandomly(char[][] grid, int size, int[] shipLengths, char[] shipIcons, Random rng) {
		placeFleetRandomly(grid, size, shipLengths, shipIcons, rng, false);
	}
}
