// arcade.battleship
// Everything the start screen decides, bundled into one immutable value instead
// of the v2 public-static globals (battleship.difficulty, revealMapCheat).
//
//   salvo   -- official variant: each turn you fire a volley of shots equal to
//              your number of surviving ships, all chosen before any results.
//   noTouch -- Sea Battle placement rule: ships may not be adjacent, not even
//              diagonally. Needs a board of at least 9x9 to stay placeable.

package arcade.battleship;

public record GameConfig(int boardSize, boolean revealCheat, OpponentKind opponentKind, boolean salvo,
		boolean noTouch) {

	public enum OpponentKind {
		HUNT_TARGET, OLLAMA
	}

	// Classic single-shot config (used by tests and as the simple default).
	public GameConfig(int boardSize, boolean revealCheat, OpponentKind opponentKind) {
		this(boardSize, revealCheat, opponentKind, false, false);
	}
}
