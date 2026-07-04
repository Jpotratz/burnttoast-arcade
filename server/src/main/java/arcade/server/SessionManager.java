// arcade.server
// In-memory game sessions with idle expiry, shared by all game modules. Games
// live only in memory (a server restart drops active games -- fine at homelab
// scale); completed results are persisted separately in ScoreDb.

package arcade.server;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager<T> {

	private static final SecureRandom RANDOM = new SecureRandom();

	private record Entry<T>(T value, long lastTouched) {
	}

	private final Map<String, Entry<T>> sessions = new ConcurrentHashMap<>();
	private final long ttlMillis;

	public SessionManager(long ttlMillis) {
		this.ttlMillis = ttlMillis;
	}

	public String create(T value) {
		sweep();
		byte[] id = new byte[12];
		RANDOM.nextBytes(id);
		String key = HexFormat.of().formatHex(id);
		sessions.put(key, new Entry<>(value, System.currentTimeMillis()));
		return key;
	}

	// Fetch a session and refresh its idle timer; null if unknown or expired.
	public T get(String id) {
		if (id == null) {
			return null;
		}
		Entry<T> e = sessions.get(id);
		if (e == null) {
			return null;
		}
		if (System.currentTimeMillis() - e.lastTouched() > ttlMillis) {
			sessions.remove(id);
			return null;
		}
		sessions.put(id, new Entry<>(e.value(), System.currentTimeMillis()));
		return e.value();
	}

	public int size() {
		sweep();
		return sessions.size();
	}

	private void sweep() {
		long now = System.currentTimeMillis();
		sessions.entrySet().removeIf(e -> now - e.getValue().lastTouched() > ttlMillis);
	}
}
