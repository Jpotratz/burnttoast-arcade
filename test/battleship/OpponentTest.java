// James Potratz CSIS 222 - MCC (v2)
// Tests for the opponent layer. The RandomOpponent is fully testable; for the
// OllamaOpponent we test the pure prompt-building and reply-parsing (the risky part
// -- interpreting untrusted LLM text) with NO network involved.

package battleship;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class OpponentTest {

	@Test
	void randomOpponentOnlyPicksUnfiredCells() {
		BoardState board = new BoardState(7);
		RandomOpponent ai = new RandomOpponent(new Random(1));
		int[] cell;
		while ((cell = ai.chooseTarget(board)) != null) {
			assertFalse(board.shotsFired[cell[0]][cell[1]]);
			board.fireAt(cell[0], cell[1]);
		}
		// Board exhausted -> nothing left to choose.
		assertNull(ai.chooseTarget(board));
	}

	@Test
	void parsesTwoPlainIntegers() {
		BoardState board = new BoardState(10);
		assertArrayEquals(new int[] { 3, 6 }, OllamaOpponent.parseMove("3 6", board));
	}

	@Test
	void parsesTheFinalPairFromReasoningNoise() {
		BoardState board = new BoardState(10);
		// A reasoning-style reply that mentions several numbers before its answer.
		String reply = "<think>Maybe 5,5? No, I missed there. The hits are at 3,4 and 3,5.</think> 3 6";
		assertArrayEquals(new int[] { 3, 6 }, OllamaOpponent.parseMove(reply, board));
	}

	@Test
	void rejectsACellThatWasAlreadyFiredAt() {
		BoardState board = new BoardState(10);
		board.fireAt(3, 6);
		// Model suggests a spent cell -> unusable -> null so the caller falls back.
		assertNull(OllamaOpponent.parseMove("3 6", board));
	}

	@Test
	void rejectsOutOfRangeCoordinates() {
		BoardState board = new BoardState(10); // valid indices are 1..9
		assertNull(OllamaOpponent.parseMove("0 5", board));
		assertNull(OllamaOpponent.parseMove("9 42", board));
	}

	@Test
	void returnsNullWhenThereAreNoUsableNumbers() {
		BoardState board = new BoardState(10);
		assertNull(OllamaOpponent.parseMove("I refuse to answer.", board));
		assertNull(OllamaOpponent.parseMove(null, board));
	}

	@Test
	void extractsResponseFieldFromOllamaJson() {
		String json = "{\"model\":\"qwen2.5:7b\",\"response\":\"3 6\",\"done\":true}";
		assertTrue("3 6".equals(OllamaOpponent.extractResponseField(json)));
	}

	@Test
	void promptRevealsHitsAndMissesButNotUnfiredShips() {
		BoardState board = new BoardState(10);
		// Hidden ship the AI has NOT found yet.
		ShipPlacement.place(board.shipPositions, 7, 7, true, 3, 'R');
		board.fireAt(3, 4); // miss (open water)
		ShipPlacement.place(board.shipPositions, 2, 2, false, 2, 'P');
		board.fireAt(2, 2); // hit on the patrol
		String prompt = OllamaOpponent.buildPrompt(board);

		assertNotNull(prompt);
		assertTrue(prompt.contains("columns 1-9"), "should state the grid size");
		assertTrue(prompt.contains("(2,2)"), "should mention the hit cell");
		// The unfired carrier at (7,7)/(8,7)/(9,7) must NOT leak into the prompt.
		assertFalse(prompt.contains("(7,7)"), "must not reveal unfired ship locations");
	}
}
