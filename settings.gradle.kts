// BurntToast Arcade -- multi-game portal monorepo.
//   core/             game-agnostic building blocks (opponents, scoring, Ollama)
//   games/battleship/ the battleship engine (pure rules + AI)
//   desktop/          legacy JavaFX client (maintenance-only; dies when the web UI ships)
// A server/ module (Javalin webapp) arrives in Phase C.

rootProject.name = "burnttoast-arcade"

include("core")
include("games:battleship")
include("desktop")
