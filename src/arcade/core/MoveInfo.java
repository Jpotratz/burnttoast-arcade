// arcade.core
// Metadata about an opponent's most recent move, for UI display: where the move
// came from (e.g. "tactical", a model name, "fallback") and how long it took.
// millis is -1 when timing is meaningless (instant algorithmic moves).

package arcade.core;

public record MoveInfo(String source, long millis) {
}
