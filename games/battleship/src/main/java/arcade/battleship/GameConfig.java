// arcade.battleship
// Everything the start screen decides, bundled into one immutable value instead
// of the v2 public-static globals (battleship.difficulty, revealMapCheat).

package arcade.battleship;

public record GameConfig(int boardSize, boolean revealCheat, OpponentKind opponentKind) {

	public enum OpponentKind {
		HUNT_TARGET, OLLAMA
	}
}
