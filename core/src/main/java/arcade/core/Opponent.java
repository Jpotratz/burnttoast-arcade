// arcade.core
// A computer opponent for ANY game: given the current game state S, choose the
// next move M. Battleship uses Opponent<BoardState, Coord>; a future chess module
// would use Opponent<ChessPosition, ChessMove>. Implementations must only return
// legal moves; they return null only when no legal move exists.

package arcade.core;

public interface Opponent<S, M> {

	M chooseMove(S state);

	// Choose up to n moves as one simultaneous volley (salvo-style rules): all
	// moves are picked BEFORE any result is known, so implementations must not
	// use feedback between picks. The default just repeats chooseMove, which is
	// only correct for stateful opponents that remember their own picks --
	// stateless opponents MUST override to avoid duplicate moves.
	default java.util.List<M> chooseVolley(S state, int n) {
		java.util.List<M> out = new java.util.ArrayList<>();
		for (int i = 0; i < n; i++) {
			M m = chooseMove(state);
			if (m == null) {
				break;
			}
			out.add(m);
		}
		return out;
	}

	// Metadata about the most recent chooseMove(), for UI labels ("who picked this
	// and how long did it take"). Defaults to the class name with no timing.
	default MoveInfo lastMoveInfo() {
		return new MoveInfo(getClass().getSimpleName(), -1);
	}

	// Optional hook to prepare expensive resources (e.g. preload an LLM) so the
	// first real move isn't slow. No-op by default.
	default void warmUp() {
	}
}
