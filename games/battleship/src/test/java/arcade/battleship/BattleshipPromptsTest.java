// Tests for the LLM prompt building (fair play: fired cells only) and reply
// parsing (1-based model coordinates -> legal 0-based moves).

package arcade.battleship;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import arcade.core.Coord;

class BattleshipPromptsTest {

	private final BattleshipPrompts prompts = new BattleshipPrompts();

	@Test
	void promptRevealsHitsAndMissesButNotUnfiredShips() {
		BoardState b = new BoardState(9);
		ShipPlacement.place(b.shipPositions, 2, 2, true, 3, 'Y'); // ship at (2..4, 2)
		b.fireAt(2, 2); // hit
		b.fireAt(0, 0); // miss
		String p = prompts.buildPrompt(b);
		assertTrue(p.contains("hits so far: (3,3)"), "fired hit must be listed 1-based");
		assertTrue(p.contains("misses so far: (1,1)"), "fired miss must be listed 1-based");
		assertFalse(p.contains("(4,3)"), "unfired ship cells must never be revealed");
		assertFalse(p.contains("(5,3)"), "unfired ship cells must never be revealed");
	}

	@Test
	void parsesTwoPlainIntegersAsOneBased() {
		BoardState b = new BoardState(9);
		assertEquals(new Coord(2, 3), prompts.parseResponse("3 4", b));
	}

	@Test
	void parsesTheFinalPairFromReasoningNoise() {
		BoardState b = new BoardState(9);
		String noisy = "The board is 9x9. I hit at 2,2 so I should try 3,2... no wait. Final answer: 5 7";
		assertEquals(new Coord(4, 6), prompts.parseResponse(noisy, b));
	}

	@Test
	void rejectsOutOfRangeCoordinates() {
		BoardState b = new BoardState(6);
		assertNull(prompts.parseResponse("7 7", b)); // 1-based max is 6
		assertNull(prompts.parseResponse("0 3", b)); // 1-based min is 1
	}

	@Test
	void rejectsACellThatWasAlreadyFiredAt() {
		BoardState b = new BoardState(6);
		b.fireAt(2, 3);
		assertNull(prompts.parseResponse("3 4", b)); // (3,4) 1-based == (2,3) 0-based
	}

	@Test
	void returnsNullWhenThereAreNoUsableNumbers() {
		BoardState b = new BoardState(6);
		assertNull(prompts.parseResponse("I refuse to answer.", b));
		assertNull(prompts.parseResponse(null, b));
		assertNull(prompts.parseResponse("", b));
	}
}
