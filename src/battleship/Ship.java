// James Potratz CSIS 222 - MCC (v2)
// A single ship. Pure data + damage state, with NO UI dependencies so it can be
// unit-tested. In v1 the ships were five subclasses of an inner Ship class that
// also pushed scoreboard messages; v2 makes Ship a plain value object and moves
// all messaging out to the UI layer, so the same Ship works whether the player or
// the computer is the one firing at it.

package battleship;

public class Ship {

	public final int length;
	public final char icon;
	public final String name;

	public int health;
	public boolean isSunk;

	public Ship(int length, char icon, String name) {
		this.length = length;
		this.icon = icon;
		this.name = name;
		this.health = length;
		this.isSunk = false;
	}

	// Register a single hit on this ship. Returns true if THIS hit sank it.
	public boolean hit() {
		health--;
		boolean justSank = (health == 0);
		if (justSank) {
			isSunk = true;
		}
		return justSank;
	}
}
