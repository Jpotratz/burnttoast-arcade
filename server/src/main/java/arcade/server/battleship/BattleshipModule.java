// arcade.server.battleship
// Battleship's HTTP API, namespaced under /api/battleship/. The flow mirrors a
// physical game: create -> place fleet (manual or random) -> alternate fire
// (the AI's reply rides back on the same response) -> game over -> initials if
// the score cracks the top 10.

package arcade.server.battleship;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import arcade.battleship.BattleshipPrompts;
import arcade.battleship.BoardState;
import arcade.battleship.GameConfig;
import arcade.battleship.HuntTargetOpponent;
import arcade.battleship.Ship;
import arcade.battleship.ShipPlacement;
import arcade.battleship.Shot;
import arcade.core.Coord;
import arcade.core.MoveInfo;
import arcade.core.OllamaClient;
import arcade.core.OllamaOpponent;
import arcade.core.Opponent;
import arcade.server.GameModule;
import arcade.server.ScoreDb;
import arcade.server.SessionManager;
import io.javalin.Javalin;
import io.javalin.http.Context;

public class BattleshipModule implements GameModule {

	private static final Set<Integer> BOARD_SIZES = Set.of(6, 9, 12);
	private static final int LEADERBOARD_SIZE = 10;
	private static final long SESSION_TTL_MS = 2 * 60 * 60 * 1000; // 2h idle

	// ---- request bodies ------------------------------------------------------
	public record NewGameReq(Integer boardSize, String opponent, Boolean cheat) {
	}

	public record PlacedShip(int x, int y, boolean horizontal) {
	}

	public record PlaceReq(String gameId, List<PlacedShip> ships) {
	}

	public record GameIdReq(String gameId) {
	}

	public record FireReq(String gameId, Integer x, Integer y) {
	}

	public record InitialsReq(String gameId, String initials) {
	}

	private final SessionManager<BattleshipSession> sessions = new SessionManager<>(SESSION_TTL_MS);
	private final ScoreDb db;
	private final String ollamaUrl;
	private final String ollamaModel;
	private final Random rng = new Random();

	public BattleshipModule(ScoreDb db, String ollamaUrl, String ollamaModel) {
		this.db = db;
		this.ollamaUrl = ollamaUrl;
		this.ollamaModel = ollamaModel;
	}

	@Override
	public String id() {
		return "battleship";
	}

	@Override
	public String title() {
		return "BATTLESHIP";
	}

	@Override
	public String description() {
		return "Sink the fleet before it sinks you. Tactical AI or a live LLM opponent.";
	}

	@Override
	public void registerRoutes(Javalin app) {
		app.post("/api/battleship/game", this::newGame);
		app.post("/api/battleship/place", this::placeFleet);
		app.post("/api/battleship/place/random", this::placeRandom);
		app.post("/api/battleship/fire", this::fire);
		app.get("/api/battleship/state", this::state);
		app.post("/api/battleship/initials", this::initials);
		app.get("/api/battleship/leaderboard", this::leaderboard);
		app.get("/api/battleship/history", this::history);
	}

	// ---- handlers ------------------------------------------------------------

	private void newGame(Context ctx) {
		NewGameReq req = ctx.bodyAsClass(NewGameReq.class);
		int size = req.boardSize() == null ? 9 : req.boardSize();
		if (!BOARD_SIZES.contains(size)) {
			throw badRequest("boardSize must be one of " + BOARD_SIZES);
		}
		boolean cheat = Boolean.TRUE.equals(req.cheat());
		boolean llm = "ollama".equalsIgnoreCase(req.opponent());
		GameConfig config = new GameConfig(size, cheat,
				llm ? GameConfig.OpponentKind.OLLAMA : GameConfig.OpponentKind.HUNT_TARGET);

		Opponent<BoardState, Coord> opponent = llm
				? new OllamaOpponent<>(new OllamaClient(ollamaUrl, ollamaModel), new BattleshipPrompts(),
						new HuntTargetOpponent(rng))
				: new HuntTargetOpponent(rng);
		BattleshipSession session = new BattleshipSession(config, opponent, llm ? "ollama" : "tactical", rng);
		String gameId = sessions.create(session);

		if (llm) {
			Thread warmup = new Thread(opponent::warmUp, "ollama-warmup");
			warmup.setDaemon(true);
			warmup.start();
		}
		ctx.json(session.stateView(gameId));
	}

	private void placeFleet(Context ctx) {
		PlaceReq req = ctx.bodyAsClass(PlaceReq.class);
		BattleshipSession s = require(req.gameId());
		synchronized (s) {
			requirePhase(s, BattleshipSession.Phase.PLACING);
			Ship[] fleet = s.playerBoard.fleet.ships;
			if (req.ships() == null || req.ships().size() != fleet.length) {
				throw badRequest("ships must list all " + fleet.length + " ships in fleet order");
			}
			ShipPlacement.clear(s.playerBoard.shipPositions);
			for (int i = 0; i < fleet.length; i++) {
				PlacedShip p = req.ships().get(i);
				if (!ShipPlacement.fitsAt(s.playerBoard.shipPositions, s.playerBoard.size, p.x(), p.y(),
						p.horizontal(), fleet[i].length)) {
					ShipPlacement.clear(s.playerBoard.shipPositions);
					throw badRequest(fleet[i].name + " does not fit at (" + p.x() + "," + p.y() + ")");
				}
				ShipPlacement.place(s.playerBoard.shipPositions, p.x(), p.y(), p.horizontal(), fleet[i].length,
						fleet[i].icon);
			}
			s.phase = BattleshipSession.Phase.BATTLE;
			ctx.json(s.stateView(req.gameId()));
		}
	}

	private void placeRandom(Context ctx) {
		GameIdReq req = ctx.bodyAsClass(GameIdReq.class);
		BattleshipSession s = require(req.gameId());
		synchronized (s) {
			requirePhase(s, BattleshipSession.Phase.PLACING);
			s.playerBoard.placeFleetRandomly(rng);
			s.phase = BattleshipSession.Phase.BATTLE;
			ctx.json(s.stateView(req.gameId()));
		}
	}

	private void fire(Context ctx) {
		FireReq req = ctx.bodyAsClass(FireReq.class);
		BattleshipSession s = require(req.gameId());
		synchronized (s) {
			requirePhase(s, BattleshipSession.Phase.BATTLE);
			if (req.x() == null || req.y() == null || !s.enemyBoard.inBounds(req.x(), req.y())) {
				throw badRequest("x and y must be on the board");
			}
			Shot result = s.enemyBoard.fireAt(req.x(), req.y());
			if (result == Shot.ALREADY_FIRED) {
				throw badRequest("already fired at (" + req.x() + "," + req.y() + ")");
			}
			Ship hitShip = result == Shot.MISS ? null
					: s.enemyBoard.fleet.shipForIcon(s.enemyBoard.shipPositions[req.x()][req.y()]);
			int points = s.scorer.onShotResult(result, hitShip);
			Map<String, Object> playerShot = BattleshipSession.shotView(req.x(), req.y(), result, hitShip);

			Map<String, Object> aiShot = null;
			if (s.enemyBoard.fleet.isDestroyed()) {
				finish(s, true);
			} else {
				// The AI's reply rides back on the same response. For the Ollama
				// opponent this call can take seconds -- the frontend shows a
				// "thinking" state until this request returns.
				Coord aiMove = s.opponent.chooseMove(s.playerBoard);
				if (aiMove != null) {
					Shot aiResult = s.playerBoard.fireAt(aiMove.x(), aiMove.y());
					Ship aiHitShip = aiResult == Shot.MISS ? null
							: s.playerBoard.fleet.shipForIcon(s.playerBoard.shipPositions[aiMove.x()][aiMove.y()]);
					MoveInfo info = s.opponent.lastMoveInfo();
					aiShot = new java.util.HashMap<>(
							BattleshipSession.shotView(aiMove.x(), aiMove.y(), aiResult, aiHitShip));
					aiShot.put("source", info.source());
					aiShot.put("millis", info.millis());
					if (s.playerBoard.fleet.isDestroyed()) {
						finish(s, false);
					}
				}
			}

			Map<String, Object> out = new java.util.HashMap<>();
			out.put("playerShot", playerShot);
			out.put("aiShot", aiShot);
			out.put("points", points);
			out.put("state", s.stateView(req.gameId()));
			ctx.json(out);
		}
	}

	private void state(Context ctx) {
		String gameId = ctx.queryParam("gameId");
		BattleshipSession s = require(gameId);
		synchronized (s) {
			ctx.json(s.stateView(gameId));
		}
	}

	private void initials(Context ctx) {
		InitialsReq req = ctx.bodyAsClass(InitialsReq.class);
		BattleshipSession s = require(req.gameId());
		synchronized (s) {
			requirePhase(s, BattleshipSession.Phase.FINISHED);
			if (s.dbRowId < 0 || s.initialsSubmitted) {
				throw badRequest("initials already submitted or game not recorded");
			}
			String initials = req.initials() == null ? "" : req.initials().trim().toUpperCase();
			if (!initials.matches("[A-Z0-9]{1,3}")) {
				throw badRequest("initials must be 1-3 letters/digits");
			}
			db.setInitials(s.dbRowId, initials);
			s.initialsSubmitted = true;
			ctx.json(Map.of("ok", true, "initials", initials));
		}
	}

	private void leaderboard(Context ctx) {
		Integer boardSize = ctx.queryParamAsClass("boardSize", Integer.class).getOrDefault(null);
		ctx.json(db.top(id(), boardSize, LEADERBOARD_SIZE));
	}

	private void history(Context ctx) {
		int limit = ctx.queryParamAsClass("limit", Integer.class).getOrDefault(100);
		ctx.json(db.history(id(), Math.min(limit, 1000)));
	}

	// ---- helpers ---------------------------------------------------------------

	private void finish(BattleshipSession s, boolean playerWon) {
		s.playerWon = playerWon;
		s.endBonus = s.scorer.onGameEnd(playerWon, s.playerBoard.fleet.shipsRemaining(), s.enemyBoard.shots,
				s.enemyBoard.hits);
		s.phase = BattleshipSession.Phase.FINISHED;
		s.dbRowId = db.recordGame(id(), s.config.boardSize(), s.opponentName, s.config.revealCheat(), playerWon,
				s.scorer.score(), s.enemyBoard.shots, s.enemyBoard.hits, System.currentTimeMillis() - s.startedAt);
		s.qualifiesForLeaderboard = db.qualifiesForTop(id(), s.scorer.score(), LEADERBOARD_SIZE);
	}

	private BattleshipSession require(String gameId) {
		BattleshipSession s = sessions.get(gameId);
		if (s == null) {
			throw new io.javalin.http.NotFoundResponse("unknown or expired gameId");
		}
		return s;
	}

	private static void requirePhase(BattleshipSession s, BattleshipSession.Phase expected) {
		if (s.phase != expected) {
			throw badRequest("wrong phase: game is " + s.phase + ", expected " + expected);
		}
	}

	private static io.javalin.http.BadRequestResponse badRequest(String message) {
		return new io.javalin.http.BadRequestResponse(message);
	}
}
