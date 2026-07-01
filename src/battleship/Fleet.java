// James Potratz CSIS 222 - MCC (v2)
// A fleet of the five standard battleship vessels. Pure (no UI), so each side of
// the game -- the player and the computer -- owns its own Fleet. The ships are
// held in carrier-first order because ShipPlacement requires the carrier to be
// placed first (see ShipPlacement / the isOccupied carrier shortcut history).

package battleship;

public class Fleet {

	// Carrier-first order; also the order ships are placed.
	public final Ship[] ships;

	public Fleet() {
		ships = new Ship[] {
				new Ship(5, 'R', "Carrier USS Mississippi"),
				new Ship(4, 'T', "Battleship USS Arizona"),
				new Ship(3, 'Y', "Destroyer USS Decatur"),
				new Ship(3, 'S', "Submarine USS Barracuda"),
				new Ship(2, 'P', "Patrol Ship USS Pegasus") };
	}

	// Find the ship that uses the given icon, or null if none.
	public Ship shipForIcon(char icon) {
		for (Ship s : ships) {
			if (s.icon == icon) {
				return s;
			}
		}
		return null;
	}

	// How many ships are still afloat.
	public int shipsRemaining() {
		int remaining = 0;
		for (Ship s : ships) {
			if (!s.isSunk) {
				remaining++;
			}
		}
		return remaining;
	}

	public boolean isDestroyed() {
		return shipsRemaining() == 0;
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
