// arcade.battleship
// The five standard vessels for one side of the game, carrier-first (the order
// ships are placed -- largest first so random placement converges fast).

package arcade.battleship;

import java.util.HashMap;
import java.util.Map;

public class Fleet {

	public final Ship[] ships;
	private final Map<Character, Ship> byIcon = new HashMap<>();

	public Fleet() {
		ships = new Ship[] {
				new Ship(5, 'R', "Carrier USS Mississippi"),
				new Ship(4, 'T', "Battleship USS Arizona"),
				new Ship(3, 'Y', "Destroyer USS Decatur"),
				new Ship(3, 'S', "Submarine USS Barracuda"),
				new Ship(2, 'P', "Patrol Ship USS Pegasus") };
		for (Ship s : ships) {
			byIcon.put(s.icon, s);
		}
	}

	// The ship using the given grid icon, or null if none.
	public Ship shipForIcon(char icon) {
		return byIcon.get(icon);
	}

	public int size() {
		return ships.length;
	}

	public int shipsRemaining() {
		int remaining = 0;
		for (Ship s : ships) {
			if (!s.isSunk()) {
				remaining++;
			}
		}
		return remaining;
	}

	public boolean isDestroyed() {
		return shipsRemaining() == 0;
	}

	// The length of the shortest ship still afloat (used by AI parity search).
	// Returns 0 when the fleet is destroyed.
	public int shortestAfloat() {
		int min = 0;
		for (Ship s : ships) {
			if (!s.isSunk() && (min == 0 || s.length < min)) {
				min = s.length;
			}
		}
		return min;
	}

	// Ship lengths, carrier-first, for feeding ShipPlacement.
	public int[] lengths() {
		int[] out = new int[ships.length];
		for (int i = 0; i < ships.length; i++) {
			out[i] = ships[i].length;
		}
		return out;
	}

	// Ship icons, carrier-first, parallel to lengths().
	public char[] icons() {
		char[] out = new char[ships.length];
		for (int i = 0; i < ships.length; i++) {
			out[i] = ships[i].icon;
		}
		return out;
	}
}
