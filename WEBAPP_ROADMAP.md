# Battleship Webapp — Roadmap (stretch goal)

Turn the desktop JavaFX game into a browser game hosted on the home network.
This is a **rebuild of the UI layer, not a port** — JavaFX doesn't run in a
browser. The good news: the v2 refactor already did the hard part.

## Key insight: the engine is already done

The "separate logic from UI" work in v2 means the game engine is pure Java with
**zero JavaFX** and carries over unchanged:

- `ShipPlacement`, `Ship`, `Fleet`, `BoardState` — all rules, fully unit-tested
- `Opponent` / `OllamaOpponent` / `RandomOpponent` — the AI, already talks to
  Ollama over HTTP server-side

Only `gameboard.java` (the JavaFX front-end) is replaced. The 19 existing tests
keep protecting the engine through the whole effort.

## Architecture

```
Browser (HTML/CSS/JS)  ---HTTP/JSON--->  Java server (Javalin)  ---HTTP--->  Ollama
   neon UI, draws grids                   wraps BoardState +                (192.168.1.210:30068)
   fetch() calls                          OllamaOpponent, holds
                                          game state in memory
```

The browser never talks to Ollama directly — the server does, exactly like the
desktop app does today. Nothing about the LAN/Ollama setup changes.

## Tech choice: Javalin (backend)

Lightweight Java web framework — one dependency, tiny learning curve. Chosen over
Spring Boot to avoid learning a heavy framework and building the app at the same
time. The engine is framework-agnostic, so moving to Spring later is low-risk.

Frontend: plain HTML/CSS/JavaScript (no framework needed for this size). The neon
theme translates directly — glows/animations/grids are easier in CSS than JavaFX.

Build: this is the point to adopt **Gradle** (or Maven) so Javalin + JUnit are
pulled in automatically instead of hand-managed jars. `./run-tests.ps1` becomes
`gradle test`.

## Proposed HTTP API

| Method + path        | Body                    | Returns                                   |
|----------------------|-------------------------|-------------------------------------------|
| `POST /api/game`     | `{difficulty}`          | `{gameId, size}` — new game, enemy placed |
| `POST /api/place`    | `{gameId, ships[...]}`  | ok / validation error (uses `fitsAt`)     |
| `POST /api/place/random` | `{gameId}`          | the randomly placed player fleet          |
| `POST /api/fire`     | `{gameId, x, y}`        | `{playerShot, aiShot, state}` — your shot + the AI's reply in one round-trip |
| `GET  /api/state`    | `{gameId}`              | full board/fleet/turn state for redraw    |

Server keeps a `Map<String, GameSession>` where a session bundles the two
`BoardState`s + the `Opponent`. `/api/fire` calls `enemyBoard.fireAt(...)`, checks
win, then `opponent.chooseTarget(playerBoard)` + `playerBoard.fireAt(...)`, checks
loss, and returns both results — the browser just renders them.

## Milestones

1. **Backend API, no UI.** Add Gradle + Javalin, wrap the engine, expose the
   endpoints above. Verify entirely with `curl` / browser dev tools. (This is the
   clean first step — prove the API before drawing anything.)
2. **Static frontend.** One HTML page that draws the two grids from `/api/state`
   and fires via `/api/fire` with `fetch()`. Function over form first.
3. **Port the neon look** to CSS: glow via `box-shadow`, hit/miss/ship colors,
   pop animations via CSS transitions/keyframes, the status banner, win/lose modal.
4. **Multiple games / sessions** so two people on the LAN can each play (the
   session map already allows this).
5. **Host on the LAN.** Run the jar on a machine (ideally next to Ollama). Bind to
   `0.0.0.0:8080`; others browse to `http://<server-ip>:8080`. Optional: a
   Dockerfile so it runs as a container alongside Ollama.

## New skills this introduces

- A web framework (Javalin) + HTTP/JSON request handling
- Front-end HTML/CSS/JavaScript and `fetch()`
- A real build tool (Gradle/Maven)

The Java server side will feel familiar; the browser side is the genuinely new
part. Reused as-is: the entire engine and its tests.

## Sensible first step when you pick this up

Stand up Javalin with just `POST /api/fire` driving the existing `BoardState` +
`OllamaOpponent`, and test with `curl`. Once the API works, the UI is "just"
drawing and fetch calls.
