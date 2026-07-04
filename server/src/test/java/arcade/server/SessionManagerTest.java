// Tests for in-memory session storage and idle expiry.

package arcade.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class SessionManagerTest {

	@Test
	void storesAndRetrievesByGeneratedId() {
		SessionManager<String> m = new SessionManager<>(60_000);
		String id = m.create("hello");
		assertEquals("hello", m.get(id));
		assertEquals(1, m.size());
	}

	@Test
	void idsAreUniqueAndUnknownIdsReturnNull() {
		SessionManager<String> m = new SessionManager<>(60_000);
		assertNotEquals(m.create("a"), m.create("b"));
		assertNull(m.get("nope"));
		assertNull(m.get(null));
	}

	@Test
	void expiredSessionsVanish() throws InterruptedException {
		SessionManager<String> m = new SessionManager<>(50); // 50ms ttl
		String id = m.create("brief");
		Thread.sleep(80);
		assertNull(m.get(id));
		assertEquals(0, m.size());
	}
}
