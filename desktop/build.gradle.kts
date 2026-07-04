// Legacy JavaFX desktop client -- maintenance-only dev harness until the web UI
// (Phase C) is playable, then this whole module gets deleted.
//
// JavaFX is pulled from Maven with an explicit platform classifier instead of
// the openjfx Gradle plugin: fewer moving parts for a module on death row. The
// `win` classifier matches the only machine this still runs on; CI merely
// compiles it (the API jars are identical across platforms).

plugins {
    application
}

val javafxVersion = "21.0.6"
val javafxPlatform = "win"

dependencies {
    implementation(project(":games:battleship"))
    implementation("org.openjfx:javafx-base:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-graphics:$javafxVersion:$javafxPlatform")
    implementation("org.openjfx:javafx-controls:$javafxVersion:$javafxPlatform")
}

application {
    mainClass.set("arcade.desktop.Launcher")
}
