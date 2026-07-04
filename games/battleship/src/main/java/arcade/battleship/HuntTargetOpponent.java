// arcade.battleship
// The classic strong battleship strategy, and the default computer opponent:
//
//   HUNT:   no unresolved hits -> fire on a parity grid spaced by the shortest
//           ship still afloat (no ship can hide between those cells), so open
//           water is swept with far fewer shots than pure random.
//   TARGET: after a hit -> fire the cells adjacent to it; once two hits line up,
//           extend that line at both ends until the ship sinks.
//
// It plays fair: decisions use only fired-cell results and public sunk
// announcements (BoardState.isActiveHit), never unfired ship locations. All
// state is derived from the board each turn, so the opponent itself is
// stateless and trivially testable with a seeded Random.

package arcade.battleship;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import arcade.core.Coord;
import arcade.core.MoveInfo;
import arcade.core.Opponent;

public class HuntTargetOpponent implements Opponent<BoardState, Coord> {

	private static final int[][] DIRS = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

	private final Random rng;

	public HuntTargetOpponent() {
		this(new Random());
	}

	public HuntTargetOpponent(Random rng) {
		this.rng = rng;
	}

	@Override
	public Coord chooseMove(BoardState board) {
		List<Coord> active = activeHits(board);
		if (!active.isEmpty()) {
			List<Coord> line = lineExtensions(board, active);
			if (!line.isEmpty()) {
				return pick(line);
			}
			List<Coord> adjacent = unfiredNeighbors(board, active);
			if (!adjacent.isEmpty()) {
				return pick(adjacent);
			}
		}
		List<Coord> parity = board.unfiredCells(Math.max(2, board.fleet.shortestAfloat()));
		if (!parity.isEmpty()) {
			return pick(parity);
		}
		return board.randomUnfiredCell(rng);
	}

	// Salvo volley: n distinct cells chosen from the SAME pre-volley knowledge
	// (official salvo rules -- no feedback between shots of one volley). Highest
	// priority tiers first: line extensions, then hit neighbors, then parity
	// hunting, then anything unfired.
	@Override
	public java.util.List<Coord> chooseVolley(BoardState board, int n) {
		LinkedHashSet<Coord> picks = new LinkedHashSet<>();
		List<Coord> active = activeHits(board);
		addShuffled(picks, lineExtensions(board, active), n);
		addShuffled(picks, unfiredNeighbors(board, active), n);
		addShuffled(picks, board.unfiredCells(Math.max(2, board.fleet.shortestAfloat())), n);
		addShuffled(picks, board.unfiredCells(1), n);
		return new ArrayList<>(picks);
	}

	private void addShuffled(LinkedHashSet<Coord> picks, List<Coord> tier, int n) {
		List<Coord> pool = new ArrayList<>(tier);
		java.util.Collections.shuffle(pool, rng);
		for (Coord c : pool) {
			if (picks.size() >= n) {
				return;
			}
			picks.add(c);
		}
	}

	@Override
	public MoveInfo lastMoveInfo() {
		return new MoveInfo("tactical", -1);
	}

	private Coord pick(List<Coord> options) {
		return options.get(rng.nextInt(options.size()));
	}

	// Hits belonging to ships that are still afloat -- work left to finish.
	private static List<Coord> activeHits(BoardState b) {
		List<Coord> out = new ArrayList<>();
		for (int x = 0; x < b.size; x++) {
			for (int y = 0; y < b.size; y++) {
				if (b.isActiveHit(x, y)) {
					out.add(new Coord(x, y));
				}
			}
		}
		return out;
	}

	// For every pair of adjacent collinear active hits, the unfired cells just
	// past each end of that run. These are the highest-probability shots.
	private static List<Coord> lineExtensions(BoardState b, List<Coord> active) {
		Set<Coord> out = new LinkedHashSet<>();
		for (Coord hit : active) {
			for (int[] d : DIRS) {
				// Only walk each line from its "first" hit in this direction once.
				if (b.inBounds(hit.x() - d[0], hit.y() - d[1]) && b.isActiveHit(hit.x() - d[0], hit.y() - d[1])) {
					continue;
				}
				int runLength = 0;
				int x = hit.x();
				int y = hit.y();
				while (b.inBounds(x, y) && b.isActiveHit(x, y)) {
					runLength++;
					x += d[0];
					y += d[1];
				}
				if (runLength >= 2) {
					// Cell past the far end of the run.
					if (b.inBounds(x, y) && !b.shotsFired[x][y]) {
						out.add(new Coord(x, y));
					}
					// Cell just before the run's start.
					int bx = hit.x() - d[0];
					int by = hit.y() - d[1];
					if (b.inBounds(bx, by) && !b.shotsFired[bx][by]) {
						out.add(new Coord(bx, by));
					}
				}
			}
		}
		return new ArrayList<>(out);
	}

	// Unfired 4-neighbors of any active hit (used when a hit is still isolated).
	private static List<Coord> unfiredNeighbors(BoardState b, List<Coord> active) {
		Set<Coord> out = new LinkedHashSet<>();
		for (Coord hit : active) {
			for (int[] d : DIRS) {
				int x = hit.x() + d[0];
				int y = hit.y() + d[1];
				if (b.inBounds(x, y) && !b.shotsFired[x][y]) {
					out.add(new Coord(x, y));
				}
			}
		}
		return new ArrayList<>(out);
	}
}
