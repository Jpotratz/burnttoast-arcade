// arcade.core
// Thin client for a local Ollama server's /api/generate endpoint. This is the
// game-agnostic HTTP half of the old battleship OllamaOpponent: JSON assembly,
// escaping, timeouts, and response extraction. Any game (or non-game feature,
// like AI commentary) can reuse it. Uses only the JDK's built-in HttpClient, so
// there are no extra dependencies.
//
// The tiny hand-rolled JSON handling is deliberate: the requests are flat
// {model, prompt, stream, options} objects and the reply field is one string,
// so a JSON library would be pure weight here. Revisit if requests grow nested.

package arcade.core;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OllamaClient {

	private static final Pattern RESPONSE_FIELD = Pattern.compile("\"response\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

	private final String baseUrl;
	private final String model;
	private final HttpClient client;
	private final Duration timeout;

	public OllamaClient(String baseUrl, String model) {
		this(baseUrl, model, Duration.ofSeconds(20));
	}

	public OllamaClient(String baseUrl, String model, Duration timeout) {
		this.baseUrl = baseUrl;
		this.model = model;
		this.timeout = timeout;
		this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	}

	public String model() {
		return model;
	}

	// Send one prompt, return the model's reply text, or null if the reply had no
	// usable "response" field. Throws on network failure/timeout; callers decide
	// their own fallback behavior.
	public String generate(String prompt) throws Exception {
		HttpRequest req = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/api/generate")).timeout(timeout)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(requestBody(prompt))).build();
		HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
		return extractResponseField(resp.body());
	}

	// Fire a trivial request so Ollama loads the model into memory; the first real
	// call then avoids a multi-second cold start. Failures are ignored.
	public void warmUp() {
		try {
			generate("Reply with the word: ready.");
		} catch (Exception ignored) {
		}
	}

	private String requestBody(String prompt) {
		return "{\"model\":\"" + model + "\",\"prompt\":\"" + escapeJson(prompt)
				+ "\",\"stream\":false,\"options\":{\"temperature\":0.2}}";
	}

	// ---- pure, testable helpers ---------------------------------------------

	// Pull the "response" string out of Ollama's JSON reply.
	static String extractResponseField(String json) {
		if (json == null) {
			return null;
		}
		Matcher m = RESPONSE_FIELD.matcher(json);
		if (m.find()) {
			return unescapeJson(m.group(1));
		}
		return null;
	}

	static String escapeJson(String s) {
		return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "").replace("\t",
				"\\t");
	}

	static String unescapeJson(String s) {
		return s.replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\");
	}
}
