// James Potratz CSIS 222 - MCC (v2)
// The computer opponent's targeting strategy, behind an interface so it can be
// swapped: a plain random shooter (used for tests and as a safety fallback) or an
// LLM-driven shooter that asks a local Ollama model where to fire.

package battleship;

public interface Opponent {

	// Choose a cell {x, y} to fire at on the given board. Implementations must never
	// return an out-of-range or already-fired cell; they return null only when the
	// whole board has been fired at.
	int[] chooseTarget(BoardState target);

	// Did the most recent chooseTarget() come from the real model (true) or from the
	// random fallback (false)? Lets the UI label the source each turn.
	default boolean lastMoveFromModel() {
		return false;
	}

	// How long the most recent chooseTarget() took, in milliseconds (-1 if unknown).
	default long lastMoveMillis() {
		return -1;
	}

	// Optional: give the opponent a chance to warm up (e.g. preload the model) so the
	// first real move isn't slow. No-op by default.
	default void warmUp() {
	}
}
