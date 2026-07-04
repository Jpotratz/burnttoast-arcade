// arcade.desktop
// Plain (non-Application) entry point. Launching a JavaFX Application subclass
// directly from the classpath trips the "JavaFX runtime components are missing"
// startup check; routing main() through a plain class is the standard fix for
// classpath (non-modular) JavaFX apps.

package arcade.desktop;

public final class Launcher {

	public static void main(String[] args) {
		BattleshipApp.main(args);
	}
}
