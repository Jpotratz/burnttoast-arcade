// arcade.core
// The game-specific half of an LLM opponent. OllamaOpponent owns the HTTP/retry/
// fallback plumbing (game-agnostic); each game supplies a PromptStrategy that
// knows how to describe its state to a model and how to parse the model's reply
// back into a legal move. parseResponse returns null when the reply is unusable
// (garbage, out of range, illegal move) -- the caller then falls back.

package arcade.core;

public interface PromptStrategy<S, M> {

	String buildPrompt(S state);

	M parseResponse(String text, S state);
}
