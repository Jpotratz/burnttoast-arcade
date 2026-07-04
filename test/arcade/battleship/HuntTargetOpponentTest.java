// Tests for the hunt/target strategy: parity hunting, adjacent targeting after a
// hit, line extension after two collinear hits, and full-game efficiency.

package arcade.battleship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import arcade.core.Coord;

class HuntTargetOpponentTest {

	@Test
	void huntsOnTheParityGridWhenThereAreNoHits() {
		// Empty grid (the Fleet exists but nothing is stamped), so every shot
		// misses and the opponent stays in hunt mode the whole time.
		BoardState b = new BoardState(9);
		HuntTargetOpponent ai = new HuntTargetOpponent(new Random(1));
		for (int i = 0; i < 10; i++) {
			Coord c = ai.chooseMove(b);
			// All ships afloat -> shortest is the patrol (2) -> parity-2 pattern.
			assertTrue((c.x() + c.y()) % 2 == 0, "off-parity hunt shot " + c);
			b.fireAt(c.x(), c.y());
		}
	}

	@Test
	void firesAdjacentToAnIsolatedHit() {
		BoardState b = new BoardState(9);
		ShipPlacement.place(b.shipPositions, 3, 3, true, 3, 'Y');
		b.fireAt(3, 3); // one hit, ship not sunk
		HuntTargetOpponent ai = new HuntTargetOpponent(new Random(5));
		for (int i = 0; i < 20; i++) {
			Coord c = ai.chooseMove(b);
			int dist = Math.abs(c.x() - 3) + Math.abs(c.y() - 3);
			assertTrue(dist == 1, "expected a 4-neighbor of the hit, got " + c);
		}
	}

	@Test
	void extendsTheLineAfterTwoCollinearHits() {
		BoardState b = new BoardState(9);
		ShipPlacement.place(b.shipPositions, 3, 4, true, 4, 'T');
		b.fireAt(4, 4);
		b.fireAt(5, 4); // two adjacent hits on row 4
		HuntTargetOpponent ai = new HuntTargetOpponent(new Random(5));
		for (int i = 0; i < 20; i++) {
			Coord c = ai.chooseMove(b);
			boolean extendsLine = (c.equals(new Coord(3, 4)) || c.equals(new Coord(6, 4)));
			assertTrue(extendsLine, "expected an end of the hit line, got " + c);
		}
	}

	@Test
	void doesNotChaseSunkShips() {
		BoardState b = new BoardState(9);
		ShipPlacement.place(b.shipPositions, 0, 0, true, 2, 'P');
		b.fireAt(0, 0);
		b.fireAt(1, 0); // patrol sunk; no active hits remain
		HuntTargetOpponent ai = new HuntTargetOpponent(new Random(2));
		// No active hits remain, so the opponent must be back in hunt mode. With
		// the patrol (2) sunk, the shortest afloat is 3, so stripes widen to 3.
		int step = b.fleet.shortestAfloat();
		assertEquals(3, step);
		for (int i = 0; i < 10; i++) {
			Coord c = ai.chooseMove(b);
			assertTrue((c.x() + c.y()) % step == 0,
					"should be hunting on the parity-" + step + " grid, not chasing the sunk ship: " + c);
		}
	}

	@Test
	void finishesAFullGameWithoutIllegalMovesAndBeatsRandomBaseline() {
		BoardState b = new BoardState(9);
		b.placeFleetRandomly(new Random(11));
		HuntTargetOpponent ai = new HuntTargetOpponent(new Random(13));
		while (!b.fleet.isDestroyed()) {
			Coord c = ai.chooseMove(b);
			assertNotNull(c, "opponent gave up before the fleet was destroyed");
			assertNotEquals(Shot.ALREADY_FIRED, b.fireAt(c.x(), c.y()), "repeated cell " + c);
		}
		// 9x9 = 81 cells. Pure random needs ~77 shots on average to find all 17
		// ship cells; hunt/target should be far below that.
		assertTrue(b.shots <= 70, "took " + b.shots + " shots; hunt/target should beat random handily");
	}

	@Test
	void sinksAnIsolatedShipQuicklyOnceFound() {
		BoardState b = new BoardState(9);
		ShipPlacement.place(b.shipPositions, 2, 4, false, 4, 'T'); // cells (2,4)..(2,7)
		b.fireAt(2, 4); // first blood
		HuntTargetOpponent ai = new HuntTargetOpponent(new Random(17));
		int followUps = 0;
		while (!b.fleet.shipForIcon('T').isSunk()) {
			Coord c = ai.chooseMove(b);
			b.fireAt(c.x(), c.y());
			followUps++;
			assertTrue(followUps <= 12, "took too long to finish a found ship");
		}
		// 3 remaining cells + at most a handful of probing misses around them.
		assertTrue(followUps <= 9, "needed " + followUps + " follow-up shots for a length-4 ship");
	}

	@Test
	void neighborCandidatesRespectBoardEdges() {
		BoardState b = new BoardState(6);
		ShipPlacement.place(b.shipPositions, 0, 0, true, 2, 'P');
		b.fireAt(0, 0); // hit in the corner
		HuntTargetOpponent ai = new HuntTargetOpponent(new Random(23));
		for (int i = 0; i < 10; i++) {
			Coord c = ai.chooseMove(b);
			List<Coord> legal = List.of(new Coord(1, 0), new Coord(0, 1));
			assertTrue(legal.contains(c), "corner hit must target (1,0) or (0,1), got " + c);
		}
	}
}
