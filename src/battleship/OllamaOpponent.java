// James Potratz CSIS 222 - MCC (v2)
// An opponent that asks a local Ollama model where to fire. It only ever tells the
// model about its OWN past shots and their hit/miss results -- never the location
// of ships it hasn't found -- so the AI plays fair. Everything that can go wrong
// with a network call (unreachable server, timeout, garbage output, a cell that was
// already fired at) falls back to a random legal move, so the game is always
// playable even with no model available.
//
// Uses only the JDK's built-in HttpClient (Java 11+), so there are no extra jars.
// The prompt-building and response-parsing are pure static methods so they can be
// unit-tested with no network (see OpponentTest).

package battleship;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OllamaOpponent implements Opponent {

	private final String baseUrl;
	private final String model;
	private final HttpClient client;
	private final Random fallback;
	private final Duration timeout;

	private boolean lastFromModel = false;
	private long lastMillis = -1;

	public OllamaOpponent(String baseUrl, String model) {
		this.baseUrl = baseUrl;
		this.model = model;
		this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
		this.fallback = new Random();
		this.timeout = Duration.ofSeconds(20);
	}

	@Override
	public boolean lastMoveFromModel() {
		return lastFromModel;
	}

	@Override
	public long lastMoveMillis() {
		return lastMillis;
	}

	@Override
	public void warmUp() {
		// Fire a trivial request so Ollama loads the model into memory; the first real
		// move then avoids a multi-second cold start. Failures are ignored.
		try {
			post(requestBody("Reply with the word: ready."));
		} catch (Exception ignored) {
		}
	}

	@Override
	public int[] chooseTarget(BoardState target) {
		long start = System.nanoTime();
		lastFromModel = false;
		int[] result = null;
		try {
			String responseText = extractResponseField(post(requestBody(buildPrompt(target))));
			int[] pick = parseMove(responseText, target);
			if (pick != null) {
				lastFromModel = true;
				result = pick;
			}
		} catch (Exception ignored) {
			// any failure -> fall through to a random legal move
		}
		if (result == null) {
			result = target.randomUnfiredCell(fallback);
		}
		lastMillis = (System.nanoTime() - start) / 1_000_000;
		return result;
	}

	// ---- HTTP ----------------------------------------------------------------

	private String post(String body) throws Exception {
		HttpRequest req = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/generate")).timeout(timeout)
				.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
		HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
		return resp.body();
	}

	private String requestBody(String prompt) {
		return "{\"model\":\"" + model + "\",\"prompt\":\"" + escapeJson(prompt)
				+ "\",\"stream\":false,\"options\":{\"temperature\":0.2}}";
	}

	// ---- pure, testable helpers ---------------------------------------------

	// Build the instruction we send the model. Reveals only fired cells and whether
	// each was a hit or a miss -- never an unfired ship location.
	static String buildPrompt(BoardState target) {
		int n = target.size - 1; // playable coordinates are 1..n
		List<String> hits = new ArrayList<>();
		List<String> misses = new ArrayList<>();
		for (int x = 1; x <= n; x++) {
			for (int y = 1; y <= n; y++) {
				if (target.shotsFired[x][y]) {
					String cell = "(" + x + "," + y + ")";
					if (target.shipPositions[x][y] != ShipPlacement.EMPTY) {
						hits.add(cell);
					} else {
						misses.add(cell);
					}
				}
			}
		}
		StringBuilder sb = new StringBuilder();
		sb.append("You are playing Battleship as the attacker. ");
		sb.append("The grid has columns 1-").append(n).append(" and rows 1-").append(n).append(". ");
		sb.append("Ships are straight horizontal or vertical lines of cells. ");
		sb.append("Sink every ship in as few shots as possible. ");
		sb.append("Your hits so far: ").append(hits.isEmpty() ? "none" : String.join(",", hits)).append(". ");
		sb.append("Your misses so far: ").append(misses.isEmpty() ? "none" : String.join(",", misses)).append(". ");
		sb.append("If a hit has unsunk neighbors, fire adjacent to your hits to finish the ship. ");
		sb.append("Pick a cell you have NOT already fired at. ");
		sb.append("Respond with ONLY two integers 'column row', each between 1 and ").append(n)
				.append(", and nothing else.");
		return sb.toString();
	}

	// Parse the model's reply into a legal {x, y} move, or null if it can't be used
	// (no numbers, out of range, or a cell already fired at). Reasoning models may
	// emit lots of numbers, so we take the LAST in-range, unfired pair -- that tends
	// to be the model's final answer.
	static int[] parseMove(String text, BoardState target) {
		if (text == null) {
			return null;
		}
		List<Integer> nums = new ArrayList<>();
		Matcher m = Pattern.compile("\\d+").matcher(text);
		while (m.find()) {
			try {
				nums.add(Integer.parseInt(m.group()));
			} catch (NumberFormatException ignored) {
				// skip absurdly long number-like strings
			}
		}
		for (int i = nums.size() - 2; i >= 0; i--) {
			int x = nums.get(i);
			int y = nums.get(i + 1);
			if (inRange(x, target.size) && inRange(y, target.size) && !target.shotsFired[x][y]) {
				return new int[] { x, y };
			}
		}
		return null;
	}

	private static boolean inRange(int v, int size) {
		return v >= 1 && v < size;
	}

	// Pull the "response" string out of Ollama's JSON reply.
	static String extractResponseField(String json) {
		if (json == null) {
			return null;
		}
		Matcher m = Pattern.compile("\"response\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(json);
		if (m.find()) {
			return unescapeJson(m.group(1));
		}
		return null;
	}

	// Minimal JSON string escaping/unescaping -- enough for our short prompts and
	// numeric replies (no need to pull in a JSON library).
	private static String escapeJson(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "").replace("\t", "\\t");
	}

	private static String unescapeJson(String s) {
		return s.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\");
	}
}
