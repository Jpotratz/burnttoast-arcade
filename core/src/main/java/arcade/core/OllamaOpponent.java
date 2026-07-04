// arcade.core
// A game-agnostic LLM opponent: asks an Ollama model for the next move via a
// game-supplied PromptStrategy, and delegates to a fallback Opponent whenever
// anything goes wrong (server down, timeout, unparseable reply, illegal move).
// The game is therefore always playable, model or no model.

package arcade.core;

public class OllamaOpponent<S, M> implements Opponent<S, M> {

	private final OllamaClient client;
	private final PromptStrategy<S, M> strategy;
	private final Opponent<S, M> fallback;

	private boolean lastFromModel = false;
	private long lastMillis = -1;

	public OllamaOpponent(OllamaClient client, PromptStrategy<S, M> strategy, Opponent<S, M> fallback) {
		this.client = client;
		this.strategy = strategy;
		this.fallback = fallback;
	}

	@Override
	public M chooseMove(S state) {
		long start = System.nanoTime();
		lastFromModel = false;
		M move = null;
		try {
			String reply = client.generate(strategy.buildPrompt(state));
			move = strategy.parseResponse(reply, state);
			lastFromModel = (move != null);
		} catch (Exception ignored) {
			// network/timeout -> fall through to the fallback opponent
		}
		if (move == null) {
			move = fallback.chooseMove(state);
		}
		lastMillis = (System.nanoTime() - start) / 1_000_000;
		return move;
	}

	@Override
	public MoveInfo lastMoveInfo() {
		String source = lastFromModel ? client.model() : "fallback:" + fallback.lastMoveInfo().source();
		return new MoveInfo(source, lastMillis);
	}

	@Override
	public void warmUp() {
		client.warmUp();
	}
}
