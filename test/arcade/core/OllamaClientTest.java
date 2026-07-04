// Tests for the pure JSON helpers of the Ollama client (no network).

package arcade.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class OllamaClientTest {

	@Test
	void extractsResponseFieldFromOllamaJson() {
		String json = "{\"model\":\"qwen2.5:7b\",\"created_at\":\"x\",\"response\":\"5 7\",\"done\":true}";
		assertEquals("5 7", OllamaClient.extractResponseField(json));
	}

	@Test
	void unescapesEscapedCharactersInTheResponse() {
		String json = "{\"response\":\"line1\\nline2 \\\"quoted\\\"\"}";
		assertEquals("line1\nline2 \"quoted\"", OllamaClient.extractResponseField(json));
	}

	@Test
	void returnsNullWhenThereIsNoResponseField() {
		assertNull(OllamaClient.extractResponseField("{\"error\":\"model not found\"}"));
		assertNull(OllamaClient.extractResponseField(null));
	}

	@Test
	void escapeAndUnescapeRoundTrip() {
		String original = "a \"quote\" and\na newline\tand tab \\ backslash";
		String escaped = OllamaClient.escapeJson(original);
		assertEquals(original, OllamaClient.unescapeJson(escaped));
	}
}
