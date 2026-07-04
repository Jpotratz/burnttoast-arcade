# BurntToast Arcade

A self-hosted, multi-game web arcade. Battleship is the first cabinet; the
architecture is built so the next game (chess? checkers?) is a new module, not
a rewrite.

![tests](https://github.com/Jpotratz/burnttoast-arcade/actions/workflows/test.yml/badge.svg)

Grew out of a CSIS 222 JavaFX battleship project — the engine survived, got
refactored to pure 0-based logic with a real AI, and the UI moved to the
browser.

## Play

```
./gradlew :server:run          # http://localhost:8080
```

- **Portal** (`/`) lists every registered game.
- **Battleship** (`/battleship/`): pick 6×6 / 9×9 / 12×12, place your fleet
  (ghost preview, R to rotate), and fight either the **tactical AI**
  (hunt/target with parity search) or a **local LLM** via Ollama.
- Scoring: hits +100 on a streak multiplier (×1–×5), sunk bonus by ship
  length, difficulty multiplier, end-of-game accuracy/survival/victory
  bonuses. Crack the top 10 and you get the arcade three-initials moment.
- Every finished game is stored in SQLite — leaderboards are queries, full
  history is kept forever.

## Layout

| Module | What lives there |
|---|---|
| `core/` | Game-agnostic: `Opponent<S,M>`, `OllamaClient` + `PromptStrategy`, `ScoreKeeper` (streak/multiplier math) |
| `games/battleship/` | The engine: `BoardState`, `ShipPlacement`, `HuntTargetOpponent`, `BattleshipScorer` — pure, no UI, fully unit-tested |
| `server/` | Javalin webapp: `GameModule` registry, `/api/<game>/...`, sessions, SQLite, static neon frontend |

## Configuration (env)

| Var | Default | |
|---|---|---|
| `GAMES_PORT` | `8080` | HTTP port (or pass as first CLI arg) |
| `GAMES_DB` | `data/arcade.db` | SQLite file |
| `OLLAMA_URL` | `http://192.168.1.210:30068` | Ollama server for the LLM opponent |
| `OLLAMA_MODEL` | `qwen2.5:7b` | model name |

If Ollama is unreachable or answers nonsense, the LLM opponent silently falls
back to the tactical AI — the game never stalls.

## Development

```
./gradlew test                 # full suite (engine + server)
./gradlew :server:fatJar       # single runnable jar for deployment
```

Java 21 (Gradle toolchains fetch it), no other local dependencies.
