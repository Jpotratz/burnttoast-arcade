// arcade.server
// The portal entry point: one Javalin server hosting the static frontend and
// every registered game's API. Adding a game to MODULES is all the wiring a new
// game needs; /api/games drives the landing page automatically.
//
// Configuration (env vars, all optional):
//   GAMES_PORT    HTTP port                       (default 8080)
//   GAMES_DB      SQLite file path                (default data/arcade.db)
//   OLLAMA_URL    Ollama server for LLM opponents (default http://192.168.1.210:30068)
//   OLLAMA_MODEL  model name                      (default qwen2.5:7b)

package arcade.server;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import arcade.server.battleship.BattleshipModule;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

public class ArcadeServer {

	public static void main(String[] args) {
		// Port: first CLI arg wins, then GAMES_PORT, then 8080.
		int port = args.length > 0 ? Integer.parseInt(args[0]) : Integer.parseInt(env("GAMES_PORT", "8080"));
		ScoreDb db = new ScoreDb(Path.of(env("GAMES_DB", "data/arcade.db")));
		String ollamaUrl = env("OLLAMA_URL", "http://192.168.1.210:30068");
		String ollamaModel = env("OLLAMA_MODEL", "qwen2.5:7b");

		List<GameModule> modules = List.of(new BattleshipModule(db, ollamaUrl, ollamaModel));

		Javalin app = Javalin.create(cfg -> {
			cfg.showJavalinBanner = false;
			cfg.staticFiles.add("/public", Location.CLASSPATH);
		});

		app.get("/api/games", ctx -> ctx.json(modules.stream()
				.map(m -> Map.of("id", m.id(), "title", m.title(), "description", m.description(), "path",
						"/" + m.id() + "/"))
				.toList()));

		modules.forEach(m -> m.registerRoutes(app));

		app.start(port);
		System.out.println("BurntToast Arcade listening on :" + port);
	}

	private static String env(String name, String fallback) {
		String v = System.getenv(name);
		return v == null || v.isBlank() ? fallback : v;
	}
}
