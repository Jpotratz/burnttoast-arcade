// BurntToast Arcade -- multi-game portal monorepo.
//   core/             game-agnostic building blocks (opponents, scoring, Ollama)
//   games/battleship/ the battleship engine (pure rules + AI)
//   server/           the Javalin webapp: every game's API + the static frontend

rootProject.name = "burnttoast-arcade"

include("core")
include("games:battleship")
include("server")
