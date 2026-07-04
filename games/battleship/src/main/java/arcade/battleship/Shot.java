// arcade.battleship -- the battleship game engine: pure rules, no UI, no HTTP.
// The result of firing at a single cell.

package arcade.battleship;

public enum Shot {
	ALREADY_FIRED, MISS, HIT, SUNK
}
