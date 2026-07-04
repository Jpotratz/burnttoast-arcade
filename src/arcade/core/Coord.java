// arcade.core -- game-agnostic building blocks shared by every game in the portal.
// A single cell on a 2D grid, 0-based. Used as the move type for grid games
// (battleship shots today; checkers/chess squares later).

package arcade.core;

public record Coord(int x, int y) {
}
