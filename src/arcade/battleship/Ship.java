// arcade.battleship
// A single ship: identity plus damage state. Pure data so it can be unit-tested
// and shared by both sides of the game.

package arcade.battleship;

public class Ship {

	public final int length;
	public final char icon;
	public final String name;

	private int health;

	public Ship(int length, char icon, String name) {
		this.length = length;
		this.icon = icon;
		this.name = name;
		this.health = length;
	}

	// Register a single hit. Returns true if THIS hit sank the ship.
	public boolean hit() {
		health--;
		return health == 0;
	}

	public boolean isSunk() {
		return health <= 0;
	}
}
