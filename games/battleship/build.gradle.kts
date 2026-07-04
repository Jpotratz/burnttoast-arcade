// The battleship game engine: pure rules + AI on top of arcade.core.
// `api` (not `implementation`) because consumers of the engine also use core
// types directly (Coord, Opponent, ...).

dependencies {
    api(project(":core"))
}
