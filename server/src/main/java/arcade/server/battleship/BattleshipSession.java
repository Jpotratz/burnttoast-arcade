// arcade.server.battleship
// One in-progress battleship game: both boards, the AI opponent, the scorer,
// and the phase machine (PLACING -> BATTLE -> FINISHED). All rule enforcement
// delegates to the engine; this class only sequences a web game and builds the
// JSON-friendly views the frontend redraws from.
//
// All mutating access goes through the module's synchronized(session) blocks --
// one game is strictly turn-based, so a per-session lock is all it needs.

package arcade.server.battleship;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import arcade.battleship.BattleshipScorer;
import arcade.battleship.BoardState;
import arcade.battleship.GameConfig;
import arcade.battleship.Ship;
import arcade.battleship.ShipPlacement;
import arcade.battleship.Shot;
import arcade.core.Coord;
import arcade.core.Opponent;

public class BattleshipSession {

	public enum Phase {
		PLACING, BATTLE, FINISHED
	}

	public final GameConfig config;
	public final BoardState playerBoard;
	public final BoardState enemyBoard;
	public final Opponent<BoardState, Coord> opponent;
	public final BattleshipScorer scorer;
	public final String opponentName;
	public final long startedAt = System.currentTimeMillis();

	public Phase phase = Phase.PLACING;
	public boolean playerWon;
	public int endBonus;
	public long dbRowId = -1; // set when the finished game is persisted
	public boolean initialsSubmitted = false;
	public boolean qualifiesForLeaderboard = false;

	public BattleshipSession(GameConfig config, Opponent<BoardState, Coord> opponent, String opponentName,
			Random rng) {
		this.config = config;
		this.opponent = opponent;
		this.opponentName = opponentName;
		this.playerBoard = new BoardState(config.boardSize());
		this.enemyBoard = new BoardState(config.boardSize());
		this.enemyBoard.placeFleetRandomly(rng, config.noTouch());
		this.scorer = new BattleshipScorer(config.boardSize(), config.revealCheat());
	}

	// ---- JSON views ----------------------------------------------------------

	// The player's own board: full ship layout plus every shot the AI has taken.
	public Map<String, Object> playerBoardView() {
		return Map.of("ships", shipCells(playerBoard), "shots", shotCells(playerBoard));
	}

	// The enemy board as the player is allowed to see it: shots + results always;
	// ship layout only with the reveal cheat. Cells of fully sunk ships are
	// included so the UI can outline them (public info -- sinking is announced).
	public Map<String, Object> enemyBoardView() {
		List<Map<String, Object>> sunk = new ArrayList<>();
		for (int x = 0; x < enemyBoard.size; x++) {
			for (int y = 0; y < enemyBoard.size; y++) {
				char icon = enemyBoard.shipPositions[x][y];
				if (icon != ShipPlacement.EMPTY && enemyBoard.fleet.shipForIcon(icon).isSunk()) {
					sunk.add(Map.of("x", x, "y", y, "icon", String.valueOf(icon)));
				}
			}
		}
		return Map.of("shots", shotCells(enemyBoard), "sunkShips", sunk, "ships",
				config.revealCheat() ? shipCells(enemyBoard) : List.of());
	}

	public List<Map<String, Object>> fleetView(BoardState board) {
		List<Map<String, Object>> out = new ArrayList<>();
		for (Ship s : board.fleet.ships) {
			out.add(Map.of("name", s.name, "length", s.length, "icon", String.valueOf(s.icon), "sunk", s.isSunk()));
		}
		return out;
	}

	public Map<String, Object> stateView(String gameId) {
		return Map.ofEntries(Map.entry("gameId", gameId), Map.entry("phase", phase.name()),
				Map.entry("size", config.boardSize()), Map.entry("cheat", config.revealCheat()),
				Map.entry("salvo", config.salvo()), Map.entry("noTouch", config.noTouch()),
				// In salvo mode your volley size == your surviving ships.
				Map.entry("volleySize", config.salvo() ? playerBoard.fleet.shipsRemaining() : 1),
				Map.entry("opponent", opponentName), Map.entry("score", scorer.score()),
				Map.entry("streakFactor", scorer.streakFactor()),
				Map.entry("accuracy", BattleshipScorer.accuracyPercent(enemyBoard.shots, enemyBoard.hits)),
				Map.entry("yourFleet", fleetView(playerBoard)), Map.entry("enemyFleet", fleetView(enemyBoard)),
				Map.entry("yourBoard", playerBoardView()), Map.entry("enemyBoard", enemyBoardView()),
				Map.entry("playerWon", playerWon), Map.entry("endBonus", endBonus),
				Map.entry("qualifiesForLeaderboard", qualifiesForLeaderboard),
				Map.entry("initialsSubmitted", initialsSubmitted));
	}

	public static Map<String, Object> shotView(int x, int y, Shot result, Ship ship) {
		return Map.of("x", x, "y", y, "result", result.name(), "shipName", ship == null ? "" : ship.name, "shipSunk",
				ship != null && ship.isSunk());
	}

	private static List<Map<String, Object>> shipCells(BoardState board) {
		List<Map<String, Object>> cells = new ArrayList<>();
		for (int x = 0; x < board.size; x++) {
			for (int y = 0; y < board.size; y++) {
				char icon = board.shipPositions[x][y];
				if (icon != ShipPlacement.EMPTY) {
					cells.add(Map.of("x", x, "y", y, "icon", String.valueOf(icon)));
				}
			}
		}
		return cells;
	}

	private static List<Map<String, Object>> shotCells(BoardState board) {
		List<Map<String, Object>> cells = new ArrayList<>();
		for (int x = 0; x < board.size; x++) {
			for (int y = 0; y < board.size; y++) {
				if (board.shotsFired[x][y]) {
					cells.add(Map.of("x", x, "y", y, "hit", board.isHit(x, y)));
				}
			}
		}
		return cells;
	}
}
