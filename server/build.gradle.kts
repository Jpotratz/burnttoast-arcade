// The web portal: Javalin HTTP server hosting every game's API plus the static
// frontend. This is what ships to BurntToast as the games.burnttoast.vip app.

plugins {
    application
}

dependencies {
    implementation(project(":games:battleship"))
    implementation("io.javalin:javalin:6.7.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.2")
    implementation("org.xerial:sqlite-jdbc:3.47.1.0")
    implementation("org.slf4j:slf4j-simple:2.0.16")
}

application {
    mainClass.set("arcade.server.ArcadeServer")
}

// A single runnable fat jar for the Docker image (no dependency wrangling
// inside the container).
tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "arcade.server.ArcadeServer"
    }
    // dependsOn + lazy resolution: the classpath jars (e.g. the battleship
    // engine) must be BUILT before this task runs -- eager .get() at
    // configuration time loses that dependency and breaks clean builds.
    dependsOn(configurations.runtimeClasspath)
    from(sourceSets.main.get().output)
    from({ configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) } }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
}
