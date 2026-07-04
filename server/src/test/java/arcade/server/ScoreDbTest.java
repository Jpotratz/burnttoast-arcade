// Tests for the SQLite score store: history rows, leaderboard rules, initials.

package arcade.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScoreDbTest {

	@TempDir
	Path tmp;

	private ScoreDb db;

	@BeforeEach
	void open() {
		db = new ScoreDb(tmp.resolve("test.db"));
	}

	@AfterEach
	void close() throws Exception {
		db.close();
	}

	@Test
	void recordsAndListsHistoryNewestFirst() {
		db.recordGame("battleship", 9, "tactical", false, true, 3000, 30, 17, 60_000);
		db.recordGame("battleship", 6, "ollama", false, false, 800, 20, 8, 45_000);
		List<ScoreDb.ScoreRow> hist = db.history("battleship", 10);
		assertEquals(2, hist.size());
		assertEquals(800, hist.get(0).score()); // newest first
		assertNull(hist.get(0).initials()); // anonymous until submitted
	}

	@Test
	void leaderboardExcludesCheatedAndAnonymousGames() {
		long fair = db.recordGame("battleship", 9, "tactical", false, true, 5000, 30, 17, 1000);
		db.setInitials(fair, "JMS");
		long cheated = db.recordGame("battleship", 9, "tactical", true, true, 9999, 30, 17, 1000);
		db.setInitials(cheated, "CHT");
		db.recordGame("battleship", 9, "tactical", false, true, 7000, 30, 17, 1000); // no initials
		List<ScoreDb.ScoreRow> top = db.top("battleship", null, 10);
		assertEquals(1, top.size());
		assertEquals("JMS", top.get(0).initials());
	}

	@Test
	void leaderboardOrdersByScoreAndFiltersByBoardSize() {
		for (int i = 1; i <= 3; i++) {
			long id = db.recordGame("battleship", 9, "tactical", false, true, i * 1000, 30, 17, 1000);
			db.setInitials(id, "P" + i);
		}
		long small = db.recordGame("battleship", 6, "tactical", false, true, 9000, 20, 17, 1000);
		db.setInitials(small, "SML");
		List<ScoreDb.ScoreRow> all = db.top("battleship", null, 10);
		assertEquals(9000, all.get(0).score());
		List<ScoreDb.ScoreRow> nines = db.top("battleship", 9, 10);
		assertEquals(3, nines.size());
		assertEquals(3000, nines.get(0).score());
	}

	@Test
	void qualifiesForTopUntilTheTableIsFullThenRequiresBeatingTheFloor() {
		assertTrue(db.qualifiesForTop("battleship", 1, 3));
		assertFalse(db.qualifiesForTop("battleship", 0, 3)); // zero never qualifies
		for (int i = 1; i <= 3; i++) {
			long id = db.recordGame("battleship", 9, "tactical", false, true, i * 1000, 30, 17, 1000);
			db.setInitials(id, "P" + i);
		}
		assertFalse(db.qualifiesForTop("battleship", 999, 3)); // below the floor
		assertTrue(db.qualifiesForTop("battleship", 1001, 3)); // beats the floor
	}

	@Test
	void gamesAreNamespacedByGameId() {
		long id = db.recordGame("chess", 9, "engine", false, true, 4000, 1, 1, 1000);
		db.setInitials(id, "CHS");
		assertTrue(db.top("battleship", null, 10).isEmpty());
		assertEquals(1, db.top("chess", null, 10).size());
	}
}
