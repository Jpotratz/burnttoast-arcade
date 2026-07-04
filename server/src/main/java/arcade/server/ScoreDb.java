// arcade.server
// SQLite persistence for game results: every finished game is one permanent
// history row; leaderboards are queries over it. One shared table serves every
// game in the portal (the `game` column namespaces them).
//
// A single connection with synchronized access is deliberate: SQLite is
// single-writer anyway and this is homelab traffic, not a queueing problem.

package arcade.server;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ScoreDb implements AutoCloseable {

	public record ScoreRow(String initials, int score, int boardSize, String opponent, String mode, boolean won,
			int shots, int hits, String playedAt) {
	}

	private final Connection conn;

	public ScoreDb(Path file) {
		try {
			if (file.getParent() != null) {
				Files.createDirectories(file.getParent());
			}
			conn = DriverManager.getConnection("jdbc:sqlite:" + file);
			try (Statement st = conn.createStatement()) {
				st.execute("""
						CREATE TABLE IF NOT EXISTS games (
						    id          INTEGER PRIMARY KEY AUTOINCREMENT,
						    game        TEXT    NOT NULL,
						    initials    TEXT,
						    score       INTEGER NOT NULL,
						    won         INTEGER NOT NULL,
						    board_size  INTEGER NOT NULL,
						    opponent    TEXT    NOT NULL,
						    cheat       INTEGER NOT NULL,
						    shots       INTEGER NOT NULL,
						    hits        INTEGER NOT NULL,
						    duration_ms INTEGER NOT NULL,
						    played_at   TEXT    NOT NULL DEFAULT (datetime('now'))
						)""");
				st.execute("CREATE INDEX IF NOT EXISTS idx_games_leaderboard ON games (game, score DESC)");
				// Migration for pre-rules-pack databases: add the mode column.
				try {
					st.execute("ALTER TABLE games ADD COLUMN mode TEXT NOT NULL DEFAULT 'classic'");
				} catch (SQLException alreadyThere) {
					// column exists -- fine
				}
			}
		} catch (Exception e) {
			throw new RuntimeException("failed to open score DB at " + file, e);
		}
	}

	// Persist a finished game (initials come later, via setInitials). Returns the
	// row id so the session can attach initials after the player types them.
	public synchronized long recordGame(String game, int boardSize, String opponent, String mode, boolean cheat,
			boolean won, int score, int shots, int hits, long durationMs) {
		String sql = "INSERT INTO games (game, score, won, board_size, opponent, mode, cheat, shots, hits, duration_ms) "
				+ "VALUES (?,?,?,?,?,?,?,?,?,?)";
		try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
			ps.setString(1, game);
			ps.setInt(2, score);
			ps.setInt(3, won ? 1 : 0);
			ps.setInt(4, boardSize);
			ps.setString(5, opponent);
			ps.setString(6, mode);
			ps.setInt(7, cheat ? 1 : 0);
			ps.setInt(8, shots);
			ps.setInt(9, hits);
			ps.setLong(10, durationMs);
			ps.executeUpdate();
			try (ResultSet keys = ps.getGeneratedKeys()) {
				keys.next();
				return keys.getLong(1);
			}
		} catch (SQLException e) {
			throw new RuntimeException("recordGame failed", e);
		}
	}

	public synchronized void setInitials(long rowId, String initials) {
		try (PreparedStatement ps = conn.prepareStatement("UPDATE games SET initials = ? WHERE id = ?")) {
			ps.setString(1, initials);
			ps.setLong(2, rowId);
			ps.executeUpdate();
		} catch (SQLException e) {
			throw new RuntimeException("setInitials failed", e);
		}
	}

	// Top scores for a game (optionally one board size). Cheated games score 0
	// and anonymous games have no initials; both are excluded.
	public synchronized List<ScoreRow> top(String game, Integer boardSize, int limit) {
		String sql = "SELECT initials, score, board_size, opponent, mode, won, shots, hits, played_at FROM games "
				+ "WHERE game = ? AND cheat = 0 AND initials IS NOT NULL "
				+ (boardSize != null ? "AND board_size = ? " : "") + "ORDER BY score DESC, id ASC LIMIT ?";
		return query(sql, game, boardSize, limit);
	}

	// Full history, newest first -- the "everything ever played" view.
	public synchronized List<ScoreRow> history(String game, int limit) {
		String sql = "SELECT initials, score, board_size, opponent, mode, won, shots, hits, played_at FROM games "
				+ "WHERE game = ? ORDER BY id DESC LIMIT ?";
		return query(sql, game, null, limit);
	}

	// Does this score make the current top-N cut? (Drives the initials prompt.)
	public synchronized boolean qualifiesForTop(String game, int score, int limit) {
		List<ScoreRow> top = top(game, null, limit);
		return score > 0 && (top.size() < limit || score > top.get(top.size() - 1).score());
	}

	private List<ScoreRow> query(String sql, String game, Integer boardSize, int limit) {
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			int i = 1;
			ps.setString(i++, game);
			if (boardSize != null) {
				ps.setInt(i++, boardSize);
			}
			ps.setInt(i, limit);
			try (ResultSet rs = ps.executeQuery()) {
				List<ScoreRow> rows = new ArrayList<>();
				while (rs.next()) {
					rows.add(new ScoreRow(rs.getString("initials"), rs.getInt("score"), rs.getInt("board_size"),
							rs.getString("opponent"), rs.getString("mode"), rs.getInt("won") == 1,
							rs.getInt("shots"), rs.getInt("hits"), rs.getString("played_at")));
				}
				return rows;
			}
		} catch (SQLException e) {
			throw new RuntimeException("score query failed", e);
		}
	}

	@Override
	public void close() throws SQLException {
		conn.close();
	}
}
