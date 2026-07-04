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

	// Volley (salvo) support: the model contributes the first shot; the rest of
	// the volley comes from the fallback opponent's volley logic, deduplicated.
	// Asking an LLM for N coordinates at once parses too unreliably to be worth
	// it, and official salvo rules forbid feedback between picks anyway.
	@Override
	public java.util.List<M> chooseVolley(S state, int n) {
		java.util.LinkedHashSet<M> out = new java.util.LinkedHashSet<>();
		M first = chooseMove(state);
		if (first != null) {
			out.add(first);
		}
		for (M m : fallback.chooseVolley(state, n)) {
			if (out.size() >= n) {
				break;
			}
			out.add(m);
		}
		return new java.util.ArrayList<>(out);
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
