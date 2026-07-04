// arcade.desktop
// The JavaFX front-end for Battleship -- neon-arcade style, three scenes swapped
// on one Stage: Start -> Placement -> Battle. All rules live in arcade.battleship;
// the AI lives behind arcade.core.Opponent. This class is only presentation,
// animation, and turn flow.
//
// NOTE: this desktop client is in maintenance mode -- it is the dev harness until
// the web UI (games.burnttoast.vip) is playable, then it gets deleted. No new
// features land here; build them in the webapp.

package arcade.desktop;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import arcade.battleship.BattleshipPrompts;
import arcade.battleship.BattleshipScorer;
import arcade.battleship.BoardState;
import arcade.battleship.GameConfig;
import arcade.battleship.HuntTargetOpponent;
import arcade.battleship.Ship;
import arcade.battleship.ShipPlacement;
import arcade.battleship.Shot;
import arcade.core.Coord;
import arcade.core.MoveInfo;
import arcade.core.OllamaClient;
import arcade.core.OllamaOpponent;
import arcade.core.Opponent;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ScaleTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.effect.DropShadow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

public class BattleshipApp extends Application {

	// ---- neon palette --------------------------------------------------------
	private static final Color BG = Color.web("#0b0e17");
	private static final Color PANEL_BG = Color.web("#121627");
	private static final Color CYAN = Color.web("#25e7ff");
	private static final Color MAGENTA = Color.web("#ff3ea5");
	private static final Color GREEN = Color.web("#38e07b");
	private static final Color YELLOW = Color.web("#ffd23e");
	private static final Color RED = Color.web("#ff4d5e");
	private static final Color WATER = Color.web("#0f1c34");
	private static final Color GRID = Color.web("#1f6f8b");
	private static final Color MISS_MARK = Color.web("#6f8bb0");
	private static final Color SHIP_FILL = Color.web("#2a1533");
	private static final Color SHIP_BODY = Color.web("#b04cff");

	// ---- layout budget (logical px) -----------------------------------------
	private static final int PANEL_W = 300;
	private static final int GAP = 26;
	private static final int BANNER_H = 62;

	// ---- Ollama opponent config ---------------------------------------------
	private static final String OLLAMA_URL = "http://192.168.1.210:30068";
	private static final String OLLAMA_MODEL = "qwen2.5:7b";

	// ---- shared state --------------------------------------------------------
	private Stage stage;
	private final Random rng = new Random();
	private final ExecutorService aiExec = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "ai-turn");
		t.setDaemon(true);
		return t;
	});
	private GameConfig config;
	private int tileSize = 46; // recomputed per screen to fit the display

	// ---- placement-screen state ---------------------------------------------
	private Tile[][] placeTiles;
	private int placeIndex;
	private boolean placeHorizontal = true;
	private Label placePrompt;
	private Button startBattleBtn;

	// ---- battle state --------------------------------------------------------
	private BoardState playerBoard;
	private BoardState enemyBoard;
	private Tile[][] playerTiles;
	private Tile[][] enemyTiles;
	private Opponent<BoardState, Coord> opponent;
	private BattleshipScorer scorer;
	private boolean inputLocked = false;
	private StackPane battleStack; // holds the game + the win/lose overlay
	private Label banner;
	private FadeTransition bannerPulse;
	private Label lblScore;
	private Label lblYourShips;
	private Label lblEnemyShips;
	private Label lblAccuracy;
	private Label lblYou;
	private Label lblAi;

	private interface CellHandler {
		void handle(int x, int y);
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		this.stage = primaryStage;
		stage.setTitle("Battleship v2");
		showStartScreen();
		stage.show();
		stage.centerOnScreen();
		stage.toFront();
	}

	// =========================================================================
	// Screen 1: Start
	// =========================================================================

	private void showStartScreen() {
		Label title = neonLabel("BATTLESHIP", 60, CYAN);
		Label subtitle = neonLabel("// v2  ::  neon fleet command", 18, MAGENTA);

		ToggleGroup difficulty = new ToggleGroup();
		RadioButton beginner = radioOption("BEGINNER   6 x 6", 6, difficulty);
		RadioButton standard = radioOption("STANDARD   9 x 9", 9, difficulty);
		RadioButton advanced = radioOption("ADVANCED   12 x 12", 12, difficulty);
		standard.setSelected(true);

		ToggleGroup opponentPick = new ToggleGroup();
		RadioButton tactical = radioOption("TACTICAL AI   (hunt & target)", GameConfig.OpponentKind.HUNT_TARGET,
				opponentPick);
		RadioButton llm = radioOption("OLLAMA LLM   (" + OLLAMA_MODEL + ")", GameConfig.OpponentKind.OLLAMA,
				opponentPick);
		tactical.setSelected(true);

		CheckBox cheat = new CheckBox("Reveal the enemy fleet (cheat, disables scoring)");
		styleCheck(cheat);

		Button startBtn = neonButton("DEPLOY  ▶", CYAN);
		startBtn.setOnAction(e -> {
			config = new GameConfig((int) difficulty.getSelectedToggle().getUserData(), cheat.isSelected(),
					(GameConfig.OpponentKind) opponentPick.getSelectedToggle().getUserData());
			showPlacementScreen();
		});

		VBox box = new VBox(16, title, subtitle, spacer(8), neonLabel("CHOOSE DIFFICULTY", 16, CYAN), beginner,
				standard, advanced, spacer(4), neonLabel("CHOOSE OPPONENT", 16, CYAN), tactical, llm, spacer(4), cheat,
				spacer(8), startBtn);
		box.setAlignment(Pos.CENTER);
		box.setPadding(new Insets(40));
		box.setBackground(solid(BG));
		stage.setScene(new Scene(box, 640, 680));
	}

	private RadioButton radioOption(String text, Object value, ToggleGroup group) {
		RadioButton rb = new RadioButton(text);
		rb.setUserData(value);
		rb.setToggleGroup(group);
		rb.setTextFill(Color.web("#c9e9ff"));
		rb.setFont(mono(16, FontWeight.NORMAL));
		return rb;
	}

	// =========================================================================
	// Screen 2: Placement
	// =========================================================================

	private void showPlacementScreen() {
		int size = config.boardSize();
		tileSize = computeTileSize(size, 1);
		playerBoard = new BoardState(size);
		placeIndex = 0;
		placeHorizontal = true;

		Pane boardPane = new Pane();
		placeTiles = buildGrid(boardPane, size, this::handlePlacementClick);

		placePrompt = new Label();
		placePrompt.setWrapText(true);
		placePrompt.setPrefWidth(260);
		placePrompt.setTextFill(Color.web("#c9e9ff"));
		placePrompt.setFont(mono(15, FontWeight.NORMAL));

		Button rotateBtn = neonButton("ROTATE  (R)", CYAN);
		rotateBtn.setOnAction(e -> rotatePlacement());
		Button randomBtn = neonButton("RANDOMIZE", YELLOW);
		randomBtn.setOnAction(e -> randomPlacement());
		Button clearBtn = neonButton("CLEAR", MAGENTA);
		clearBtn.setOnAction(e -> clearPlacement());
		startBattleBtn = neonButton("START BATTLE  ▶", GREEN);
		startBattleBtn.setDisable(true);
		startBattleBtn.setOnAction(e -> showBattleScreen());

		VBox controls = new VBox(14, neonLabel("PLACE YOUR FLEET", 20, CYAN), placePrompt, rotateBtn, randomBtn,
				clearBtn, spacer(8), startBattleBtn);
		controls.setPadding(new Insets(10, 10, 10, 24));
		controls.setPrefWidth(300);
		controls.setAlignment(Pos.TOP_LEFT);

		HBox rootBox = new HBox(20, wrapBoard("YOUR WATERS", boardPane), controls);
		rootBox.setAlignment(Pos.CENTER);
		rootBox.setPadding(new Insets(24));
		rootBox.setBackground(solid(BG));

		Scene scene = new Scene(rootBox);
		scene.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.R) {
				rotatePlacement();
			}
		});
		stage.setScene(scene);
		sizeToScene();
		renderPlacement();
		updatePlacementPrompt();
	}

	private void handlePlacementClick(int x, int y) {
		if (placeIndex >= playerBoard.fleet.size()) {
			return;
		}
		Ship ship = playerBoard.fleet.ships[placeIndex];
		if (ShipPlacement.fitsAt(playerBoard.shipPositions, playerBoard.size, x, y, placeHorizontal, ship.length)) {
			ShipPlacement.place(playerBoard.shipPositions, x, y, placeHorizontal, ship.length, ship.icon);
			placeIndex++;
			renderPlacement();
			if (placeIndex == playerBoard.fleet.size()) {
				startBattleBtn.setDisable(false);
			}
			updatePlacementPrompt();
		} else {
			placePrompt.setText("Won't fit there (off-board or overlapping).\n\n" + promptForCurrentShip());
		}
	}

	private void rotatePlacement() {
		placeHorizontal = !placeHorizontal;
		updatePlacementPrompt();
	}

	private void randomPlacement() {
		playerBoard.placeFleetRandomly(rng);
		placeIndex = playerBoard.fleet.size();
		renderPlacement();
		startBattleBtn.setDisable(false);
		updatePlacementPrompt();
	}

	private void clearPlacement() {
		ShipPlacement.clear(playerBoard.shipPositions);
		placeIndex = 0;
		renderPlacement();
		startBattleBtn.setDisable(true);
		updatePlacementPrompt();
	}

	private void renderPlacement() {
		for (int x = 0; x < playerBoard.size; x++) {
			for (int y = 0; y < playerBoard.size; y++) {
				if (playerBoard.shipPositions[x][y] != ShipPlacement.EMPTY) {
					placeTiles[x][y].showShip();
				} else {
					placeTiles[x][y].setWater();
				}
			}
		}
	}

	private void updatePlacementPrompt() {
		if (placeIndex >= playerBoard.fleet.size()) {
			placePrompt.setText("Fleet ready! Hit START BATTLE when you're set.");
		} else {
			placePrompt.setText(promptForCurrentShip());
		}
	}

	private String promptForCurrentShip() {
		Ship ship = playerBoard.fleet.ships[placeIndex];
		return "Placing: " + ship.name + "\nLength: " + ship.length + "\nOrientation: "
				+ (placeHorizontal ? "HORIZONTAL" : "VERTICAL") + "\n\nClick a starting cell (R to rotate).";
	}

	// =========================================================================
	// Screen 3: Battle
	// =========================================================================

	private void showBattleScreen() {
		int size = playerBoard.size;
		tileSize = computeTileSize(size, 2);
		enemyBoard = new BoardState(size);
		enemyBoard.placeFleetRandomly(rng);
		scorer = new BattleshipScorer(size, config.revealCheat());
		opponent = buildOpponent();
		inputLocked = false;

		Pane playerPane = new Pane();
		playerTiles = buildGrid(playerPane, size, null);
		revealFleet(playerTiles, playerBoard);

		Pane enemyPane = new Pane();
		enemyTiles = buildGrid(enemyPane, size, this::handlePlayerShot);
		if (config.revealCheat()) {
			revealFleet(enemyTiles, enemyBoard);
		}

		HBox boards = new HBox(GAP, wrapBoard("YOUR FLEET", playerPane), wrapBoard("ENEMY WATERS", enemyPane),
				buildPanel());
		boards.setAlignment(Pos.CENTER);
		boards.setPadding(new Insets(GAP));

		banner = new Label();
		banner.setMaxWidth(Double.MAX_VALUE);
		banner.setAlignment(Pos.CENTER);
		banner.setPrefHeight(BANNER_H);
		banner.setFont(mono(26, FontWeight.BOLD));

		VBox layout = new VBox(banner, boards);
		layout.setAlignment(Pos.TOP_CENTER);
		layout.setBackground(solid(BG));

		battleStack = new StackPane(layout);
		battleStack.setBackground(solid(BG));

		aiExec.submit(opponent::warmUp);

		setBanner("YOUR TURN  —  fire at the enemy waters", CYAN, false);
		refreshPanel("Fire at the enemy waters on the right.", "Waiting for your first shot...");

		stage.setScene(new Scene(battleStack));
		sizeToScene();
	}

	private Opponent<BoardState, Coord> buildOpponent() {
		if (config.opponentKind() == GameConfig.OpponentKind.OLLAMA) {
			return new OllamaOpponent<>(new OllamaClient(OLLAMA_URL, OLLAMA_MODEL), new BattleshipPrompts(),
					new HuntTargetOpponent(rng));
		}
		return new HuntTargetOpponent(rng);
	}

	private void handlePlayerShot(int x, int y) {
		if (inputLocked) {
			return;
		}
		Shot result = enemyBoard.fireAt(x, y);
		if (result == Shot.ALREADY_FIRED) {
			lblYou.setText("You already fired at " + coord(x, y) + ".");
			return;
		}
		Ship ship = result == Shot.MISS ? null : enemyBoard.fleet.shipForIcon(enemyBoard.shipPositions[x][y]);
		int points = scorer.onShotResult(result, ship);
		renderShot(enemyTiles[x][y], result);
		String pts = points > 0 ? "  [+" + points + "]" : "";
		lblYou.setText("You fired at " + coord(x, y) + " - " + describePlayerResult(result, ship) + pts);
		updateStats();

		if (enemyBoard.fleet.isDestroyed()) {
			endGame(true);
			return;
		}
		aiTurn();
	}

	private void aiTurn() {
		inputLocked = true;
		setBanner("ENEMY AI IS THINKING…", MAGENTA, true);
		aiExec.submit(() -> {
			Coord cell = opponent.chooseMove(playerBoard);
			MoveInfo info = opponent.lastMoveInfo();
			Platform.runLater(() -> resolveAiShot(cell, info));
		});
	}

	private void resolveAiShot(Coord cell, MoveInfo info) {
		if (cell == null) {
			inputLocked = false;
			return;
		}
		Shot result = playerBoard.fireAt(cell.x(), cell.y());
		Ship ship = result == Shot.MISS ? null
				: playerBoard.fleet.shipForIcon(playerBoard.shipPositions[cell.x()][cell.y()]);
		renderShot(playerTiles[cell.x()][cell.y()], result);

		String timing = info.millis() >= 0 ? " (" + info.millis() + "ms)" : "";
		lblAi.setText("AI [" + info.source() + "] fired at " + coord(cell.x(), cell.y()) + " - " + resultWord(result)
				+ timing);
		lblYou.setText(describeAiResult(result, ship));
		updateStats();

		if (playerBoard.fleet.isDestroyed()) {
			endGame(false);
			return;
		}
		inputLocked = false;
		setBanner("YOUR TURN  —  fire at the enemy waters", CYAN, false);
	}

	private void renderShot(Tile tile, Shot result) {
		switch (result) {
		case MISS:
			tile.showMiss();
			break;
		case HIT:
			tile.showHit(false);
			break;
		case SUNK:
			tile.showHit(true);
			break;
		default:
			break;
		}
	}

	// =========================================================================
	// Win / lose overlay
	// =========================================================================

	private void endGame(boolean playerWon) {
		stopPulse();
		inputLocked = true;
		int endBonus = scorer.onGameEnd(playerWon, playerBoard.fleet.shipsRemaining(), enemyBoard.shots,
				enemyBoard.hits);
		updateStats();
		setBanner(playerWon ? "VICTORY" : "DEFEAT", playerWon ? GREEN : RED, false);

		Label big = neonLabel(playerWon ? "VICTORY!" : "DEFEAT", 72, playerWon ? GREEN : RED);
		Label sub = neonLabel(playerWon ? "You sank the entire enemy fleet." : "The enemy sank your entire fleet.", 20,
				CYAN);
		Label score = neonLabel("FINAL SCORE: " + scorer.score(), 30, YELLOW);
		Label bonus = neonLabel("(end-of-game bonus +" + endBonus + ")", 14, MAGENTA);
		Button again = neonButton("PLAY AGAIN", CYAN);
		again.setOnAction(e -> showStartScreen());
		Button quit = neonButton("QUIT", MAGENTA);
		quit.setOnAction(e -> Platform.exit());

		HBox buttons = new HBox(16, again, quit);
		buttons.setAlignment(Pos.CENTER);
		VBox card = new VBox(16, big, sub, spacer(6), score, bonus, spacer(10), buttons);
		card.setAlignment(Pos.CENTER);
		card.setPadding(new Insets(40));
		card.setMaxSize(560, 420);
		card.setBackground(new Background(new BackgroundFill(PANEL_BG, new CornerRadii(16), Insets.EMPTY)));
		card.setBorder(neonBorder(playerWon ? GREEN : RED, 16));
		card.setEffect(glow(playerWon ? GREEN : RED, 30));

		StackPane overlay = new StackPane(card);
		overlay.setBackground(solid(Color.web("#000000cc")));
		FadeTransition ft = new FadeTransition(Duration.millis(260), overlay);
		ft.setFromValue(0);
		ft.setToValue(1);
		battleStack.getChildren().add(overlay);
		ft.play();
	}

	// =========================================================================
	// Side panel (score + log)
	// =========================================================================

	private VBox buildPanel() {
		lblScore = statLabel(YELLOW);
		lblYourShips = statLabel(GREEN);
		lblEnemyShips = statLabel(RED);
		lblAccuracy = statLabel(CYAN);
		lblYou = logLabel(CYAN);
		lblAi = logLabel(MAGENTA);

		VBox panel = new VBox(10, neonLabel("BATTLESHIP v2", 22, CYAN), neonLabel("by James Potratz", 12, MAGENTA),
				spacer(6), lblScore, lblYourShips, lblEnemyShips, lblAccuracy, spacer(6), sectionLabel("YOU"), lblYou,
				spacer(4), sectionLabel("ENEMY AI"), lblAi);
		panel.setPadding(new Insets(16));
		panel.setPrefWidth(PANEL_W);
		panel.setMinWidth(PANEL_W);
		panel.setBackground(new Background(new BackgroundFill(PANEL_BG, new CornerRadii(14), Insets.EMPTY)));
		panel.setBorder(neonBorder(CYAN, 14));
		return panel;
	}

	private void refreshPanel(String you, String ai) {
		lblYou.setText(you);
		lblAi.setText(ai);
		updateStats();
	}

	private void updateStats() {
		int fleetSize = playerBoard.fleet.size();
		String streak = scorer.streakFactor() > 1 ? "  (streak x" + scorer.streakFactor() + ")" : "";
		lblScore.setText("Score: " + scorer.score() + streak);
		lblYourShips.setText("Your ships:    " + playerBoard.fleet.shipsRemaining() + " / " + fleetSize);
		lblEnemyShips.setText("Enemy ships:  " + enemyBoard.fleet.shipsRemaining() + " / " + fleetSize);
		lblAccuracy.setText("Your accuracy: " + BattleshipScorer.accuracyPercent(enemyBoard.shots, enemyBoard.hits)
				+ "%");
	}

	private void setBanner(String text, Color color, boolean pulse) {
		stopPulse();
		banner.setText(text);
		banner.setTextFill(color);
		banner.setEffect(glow(color, 18));
		if (pulse) {
			bannerPulse = new FadeTransition(Duration.millis(650), banner);
			bannerPulse.setFromValue(1.0);
			bannerPulse.setToValue(0.35);
			bannerPulse.setAutoReverse(true);
			bannerPulse.setCycleCount(FadeTransition.INDEFINITE);
			bannerPulse.play();
		}
	}

	private void stopPulse() {
		if (bannerPulse != null) {
			bannerPulse.stop();
			banner.setOpacity(1.0);
			bannerPulse = null;
		}
	}

	// =========================================================================
	// Board building + rendering helpers
	// =========================================================================

	// Pick a tile size so `boards` boards (plus a label gutter each) and the side
	// panel fit on this screen.
	private int computeTileSize(int size, int boards) {
		Rectangle2D vb = Screen.getPrimary().getVisualBounds();
		double availW = vb.getWidth() - PANEL_W - (boards + 1) * GAP - 40;
		double availH = vb.getHeight() - BANNER_H - 90;
		int byW = (int) Math.floor(availW / (boards * (size + 1)));
		int byH = (int) Math.floor(availH / (size + 1));
		return Math.max(18, Math.min(52, Math.min(byW, byH)));
	}

	// Build a size x size grid of tiles with a label gutter on the top and left.
	// Returns the tiles indexed [x][y], 0-based, matching BoardState.
	private Tile[][] buildGrid(Pane pane, int size, CellHandler handler) {
		int pad = tileSize; // gutter for the coordinate labels
		Tile[][] tiles = new Tile[size][size];
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				Tile tile = new Tile(x, y, handler);
				tile.setTranslateX(pad + x * tileSize);
				tile.setTranslateY(pad + y * tileSize);
				pane.getChildren().add(tile);
				tiles[x][y] = tile;
			}
		}
		for (int i = 0; i < size; i++) {
			pane.getChildren().add(gridLabel(String.valueOf(i + 1), pad + i * tileSize, 0));
			pane.getChildren().add(gridLabel(String.valueOf((char) ('A' + i)), 0, pad + i * tileSize));
		}
		pane.setPrefSize(pad + size * tileSize, pad + size * tileSize);
		pane.setMinSize(pad + size * tileSize, pad + size * tileSize);
		return tiles;
	}

	private Label gridLabel(String text, double x, double y) {
		Label l = new Label(text);
		l.setFont(mono(tileSize * 0.4, FontWeight.BOLD));
		l.setTextFill(Color.web("#8fd7ea"));
		l.setPrefSize(tileSize, tileSize);
		l.setAlignment(Pos.CENTER);
		l.setTranslateX(x);
		l.setTranslateY(y);
		return l;
	}

	private void revealFleet(Tile[][] tiles, BoardState board) {
		for (int x = 0; x < board.size; x++) {
			for (int y = 0; y < board.size; y++) {
				if (board.shipPositions[x][y] != ShipPlacement.EMPTY) {
					tiles[x][y].showShip();
				}
			}
		}
	}

	// Wrap a board pane under a neon title.
	private VBox wrapBoard(String title, Pane board) {
		VBox col = new VBox(6, neonLabel(title, 16, CYAN), board);
		col.setAlignment(Pos.TOP_CENTER);
		return col;
	}

	// =========================================================================
	// Text helpers
	// =========================================================================

	private String coord(int x, int y) {
		return (x + 1) + "," + (char) ('A' + y);
	}

	private String resultWord(Shot result) {
		switch (result) {
		case HIT:
			return "HIT";
		case SUNK:
			return "SUNK";
		case MISS:
			return "MISS";
		default:
			return "";
		}
	}

	private String describePlayerResult(Shot result, Ship ship) {
		if (result == Shot.MISS) {
			return "miss.";
		}
		if (result == Shot.SUNK) {
			return "you SANK their " + ship.name + "!";
		}
		return "hit their " + ship.name + "!";
	}

	private String describeAiResult(Shot result, Ship ship) {
		if (result == Shot.MISS) {
			return "The enemy fired at your waters and missed.";
		}
		if (result == Shot.SUNK) {
			return "The enemy SANK your " + ship.name + "!";
		}
		return "The enemy hit your " + ship.name + "!";
	}

	// =========================================================================
	// Styling factory helpers
	// =========================================================================

	private Font mono(double size, FontWeight weight) {
		return Font.font("Consolas", weight, size);
	}

	private Label neonLabel(String text, double size, Color color) {
		Label l = new Label(text);
		l.setFont(mono(size, FontWeight.BOLD));
		l.setTextFill(color);
		l.setEffect(glow(color, Math.max(8, size * 0.35)));
		return l;
	}

	private Label sectionLabel(String text) {
		Label l = new Label(text);
		l.setFont(mono(13, FontWeight.BOLD));
		l.setTextFill(Color.web("#7fbfe0"));
		return l;
	}

	private Label statLabel(Color color) {
		Label l = new Label();
		l.setFont(mono(16, FontWeight.BOLD));
		l.setTextFill(color);
		return l;
	}

	private Label logLabel(Color color) {
		Label l = new Label();
		l.setWrapText(true);
		l.setPrefWidth(PANEL_W - 34);
		l.setFont(mono(13, FontWeight.NORMAL));
		l.setTextFill(color);
		return l;
	}

	private Button neonButton(String text, Color color) {
		Button b = new Button(text);
		b.setFont(mono(15, FontWeight.BOLD));
		b.setTextFill(color);
		b.setBackground(solid(Color.web("#0e1424")));
		b.setBorder(neonBorder(color, 8));
		b.setPadding(new Insets(8, 16, 8, 16));
		b.setEffect(glow(color, 8));
		b.setOnMouseEntered(e -> b.setBackground(solid(Color.web("#182238"))));
		b.setOnMouseExited(e -> b.setBackground(solid(Color.web("#0e1424"))));
		return b;
	}

	private void styleCheck(CheckBox c) {
		c.setTextFill(Color.web("#c9e9ff"));
		c.setFont(mono(14, FontWeight.NORMAL));
	}

	private static DropShadow glow(Color color, double radius) {
		DropShadow d = new DropShadow(radius, color);
		d.setSpread(0.35);
		return d;
	}

	private static Background solid(Color c) {
		return new Background(new BackgroundFill(c, null, Insets.EMPTY));
	}

	private static Border neonBorder(Color c, double radius) {
		return new Border(
				new BorderStroke(c, BorderStrokeStyle.SOLID, new CornerRadii(radius), new BorderWidths(2)));
	}

	private Node spacer(double h) {
		Pane p = new Pane();
		p.setMinHeight(h);
		p.setPrefHeight(h);
		return p;
	}

	// Size the window to the scene's preferred size and center it.
	private void sizeToScene() {
		stage.sizeToScene();
		stage.centerOnScreen();
	}

	// =========================================================================
	// Tile: one grid cell with neon styling, hover, shape markers, animations
	// =========================================================================

	public class Tile extends StackPane {
		private final Rectangle bg;
		private final boolean clickable;
		private boolean spent = false; // already shot or occupied by a shown ship

		public Tile(int x, int y, CellHandler handler) {
			clickable = handler != null;

			double arc = tileSize * 0.28;
			bg = new Rectangle(tileSize - 2, tileSize - 2);
			bg.setArcWidth(arc);
			bg.setArcHeight(arc);
			bg.setFill(WATER);
			bg.setStroke(GRID);
			bg.setStrokeWidth(1.5);

			setAlignment(Pos.CENTER);
			getChildren().add(bg);

			if (clickable) {
				bg.setEffect(glow(GRID, 5));
				setOnMouseEntered(e -> {
					if (!spent) {
						bg.setStroke(CYAN);
						bg.setEffect(glow(CYAN, 14));
						setScaleX(1.08);
						setScaleY(1.08);
					}
				});
				setOnMouseExited(e -> {
					if (!spent) {
						bg.setStroke(GRID);
						bg.setEffect(glow(GRID, 5));
						setScaleX(1.0);
						setScaleY(1.0);
					}
				});
				setOnMouseClicked(e -> {
					if (e.getButton() == MouseButton.PRIMARY) {
						handler.handle(x, y);
					}
				});
			}
		}

		public void setWater() {
			spent = false;
			bg.setFill(WATER);
			bg.setStroke(GRID);
			removeMarkers();
		}

		public void showShip() {
			spent = true;
			bg.setFill(SHIP_FILL);
			bg.setStroke(SHIP_BODY);
			bg.setEffect(glow(SHIP_BODY, 8));
			removeMarkers();
			double s = tileSize;
			Rectangle body = new Rectangle(s * 0.5, s * 0.5, SHIP_BODY);
			body.setArcWidth(s * 0.3);
			body.setArcHeight(s * 0.3);
			body.setEffect(glow(SHIP_BODY, 6));
			getChildren().add(body);
			pop(body);
		}

		public void showMiss() {
			spent = true;
			resetHover();
			removeMarkers();
			double r = tileSize * 0.16;
			Circle splash = new Circle(r, MISS_MARK);
			splash.setStroke(Color.web("#a9c6e6"));
			splash.setStrokeWidth(1.5);
			getChildren().add(splash);
			pop(splash);
		}

		public void showHit(boolean sunk) {
			spent = true;
			resetHover();
			Color c = sunk ? Color.web("#7a1f2a") : RED;
			bg.setFill(c.deriveColor(0, 1, 0.5, 1));
			// a burst: a filled core plus two crossing beams
			double s = tileSize;
			Circle core = new Circle(s * 0.2, c);
			core.setEffect(glow(RED, 12));
			Line a = beam(-s * 0.28, -s * 0.28, s * 0.28, s * 0.28, c);
			Line b = beam(-s * 0.28, s * 0.28, s * 0.28, -s * 0.28, c);
			getChildren().addAll(a, b, core);
			pop(core);
			pop(a);
			pop(b);
		}

		private Line beam(double x1, double y1, double x2, double y2, Color c) {
			Line l = new Line(x1, y1, x2, y2);
			l.setStroke(c.brighter());
			l.setStrokeWidth(Math.max(2, tileSize * 0.06));
			l.setEffect(glow(RED, 8));
			return l;
		}

		private void resetHover() {
			setScaleX(1.0);
			setScaleY(1.0);
			bg.setEffect(null);
		}

		private void removeMarkers() {
			getChildren().removeIf(n -> n != bg);
		}

		private void pop(Node n) {
			n.setScaleX(0.15);
			n.setScaleY(0.15);
			ScaleTransition st = new ScaleTransition(Duration.millis(200), n);
			st.setToX(1);
			st.setToY(1);
			st.setInterpolator(Interpolator.EASE_OUT);
			st.play();
		}
	}

	public static void main(String[] args) {
		launch(args);
	}
}
