// arcade.battleship
// All state and rules for ONE side's board: the ship grid, which cells have been
// fired at, that side's fleet, and shot/hit tallies. The game holds two of these.
// Fully 0-based: every cell [0, size) x [0, size) is playable.

package arcade.battleship;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import arcade.core.Coord;

public class BoardState {

	public final int size;
	public final char[][] shipPositions; // ShipPlacement.EMPTY == open water, else a ship icon
	public final boolean[][] shotsFired; // has this cell already been fired at
	public final Fleet fleet;

	public int shots; // total shots fired at this board
	public int hits; // shots that struck a ship

	public BoardState(int size) {
		this.size = size;
		this.shipPositions = new char[size][size];
		ShipPlacement.clear(shipPositions);
		this.shotsFired = new boolean[size][size];
		this.fleet = new Fleet();
	}

	public void placeFleetRandomly(Random rng) {
		placeFleetRandomly(rng, false);
	}

	public void placeFleetRandomly(Random rng, boolean noTouch) {
		ShipPlacement.placeFleetRandomly(shipPositions, size, fleet.lengths(), fleet.icons(), rng, noTouch);
	}

	public boolean inBounds(int x, int y) {
		return x >= 0 && x < size && y >= 0 && y < size;
	}

	// Resolve a shot at (x, y). Pure logic; the caller renders the outcome.
	public Shot fireAt(int x, int y) {
		if (shotsFired[x][y]) {
			return Shot.ALREADY_FIRED;
		}
		shotsFired[x][y] = true;
		shots++;

		char c = shipPositions[x][y];
		if (c == ShipPlacement.EMPTY) {
			return Shot.MISS;
		}
		hits++;
		return fleet.shipForIcon(c).hit() ? Shot.SUNK : Shot.HIT;
	}

	// Is (x, y) a fired cell that struck a ship?
	public boolean isHit(int x, int y) {
		return shotsFired[x][y] && shipPositions[x][y] != ShipPlacement.EMPTY;
	}

	// A fired hit whose ship is still afloat -- AI targeting works on these.
	public boolean isActiveHit(int x, int y) {
		return isHit(x, y) && !fleet.shipForIcon(shipPositions[x][y]).isSunk();
	}

	// Every unfired cell, optionally restricted to a checkerboard parity pattern
	// with the given step (the length of the shortest ship still afloat -- no ship
	// of that length can hide between cells of that spacing).
	public List<Coord> unfiredCells(int parityStep) {
		List<Coord> free = new ArrayList<>();
		for (int x = 0; x < size; x++) {
			for (int y = 0; y < size; y++) {
				if (!shotsFired[x][y] && (parityStep <= 1 || (x + y) % parityStep == 0)) {
					free.add(new Coord(x, y));
				}
			}
		}
		return free;
	}

	// Pick a random unfired cell, or null if the whole board is spent.
	public Coord randomUnfiredCell(Random rng) {
		List<Coord> free = unfiredCells(1);
		return free.isEmpty() ? null : free.get(rng.nextInt(free.size()));
	}
}
