// arcade.server
// One game in the portal. A module owns its API routes (namespaced under
// /api/<id>/...) and describes itself for the landing page. Adding a new game
// to the portal == implementing this and adding it to the list in ArcadeServer;
// nothing else changes.

package arcade.server;

import io.javalin.Javalin;

public interface GameModule {

	// URL-safe identifier: API lives at /api/<id>/, frontend at /<id>/.
	String id();

	String title();

	String description();

	void registerRoutes(Javalin app);
}
