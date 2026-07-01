// James Potratz CSIS 222 - MCC (v2)
// The JavaFX front-end for Battleship v2 -- a neon-arcade redesign. Three scenes
// swapped on one Stage: Start -> Placement -> Battle. The board tile size is
// computed from the actual screen so the layout fits (important on high-DPI
// displays where the logical canvas is small). All game rules live in the pure
// classes (BoardState, Fleet, Ship, ShipPlacement); the computer's targeting lives
// behind the Opponent interface (OllamaOpponent + RandomOpponent fallback). This
// class is only presentation, animation, and turn flow.

package battleship;

import java.util.Random;

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
import javafx.scene.text.Text;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

public class gameboard extends Application {

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
	public static boolean revealMapCheat = false;
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
	private Opponent opponent;
	private boolean inputLocked = false;
	private StackPane battleStack; // holds the game + the win/lose overlay
	private Label banner;
	private FadeTransition bannerPulse;
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
		RadioButton beginner = difficultyOption("BEGINNER   6 x 6", 1, difficulty);
		RadioButton standard = difficultyOption("STANDARD   9 x 9", 2, difficulty);
		RadioButton advanced = difficultyOption("ADVANCED   12 x 12", 3, difficulty);
		standard.setSelected(true);

		CheckBox cheat = new CheckBox("Reveal the enemy fleet (cheat)");
		styleCheck(cheat);

		Button startBtn = neonButton("DEPLOY  ▶", CYAN);
		startBtn.setOnAction(e -> {
			battleship.difficulty = (int) difficulty.getSelectedToggle().getUserData();
			revealMapCheat = cheat.isSelected();
			showPlacementScreen();
		});

		Label pick = neonLabel("CHOOSE DIFFICULTY", 16, CYAN);
		VBox box = new VBox(18, title, subtitle, spacer(10), pick, beginner, standard, advanced, spacer(4), cheat,
				spacer(10), startBtn);
		box.setAlignment(Pos.CENTER);
		box.setPadding(new Insets(40));
		box.setBackground(solid(BG));
		stage.setScene(new Scene(box, 620, 560));
	}

	private RadioButton difficultyOption(String text, int value, ToggleGroup group) {
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
		int size = boardSizeForDifficulty(battleship.difficulty);
		tileSize = computeTileSize(size, 1);
		playerBoard = new BoardState(size);
		placeIndex = 0;
		placeHorizontal = true;

		Pane boardPane = new Pane();
		placeTiles = buildGrid(boardPane, size, this::handlePlacementClick);
		drawCoordinates(placeTiles, size);
		boardPane.setPrefSize(size * tileSize, size * tileSize);

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
		if (placeIndex >= playerBoard.fleet.ships.length) {
			return;
		}
		Ship ship = playerBoard.fleet.ships[placeIndex];
		if (ShipPlacement.fitsAt(playerBoard.shipPositions, playerBoard.size, x, y, placeHorizontal, ship.length)) {
			ShipPlacement.place(playerBoard.shipPositions, x, y, placeHorizontal, ship.length, ship.icon);
			placeIndex++;
			renderPlacement();
			if (placeIndex == playerBoard.fleet.ships.length) {
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
		placeIndex = playerBoard.fleet.ships.length;
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
		for (int x = 1; x < playerBoard.size; x++) {
			for (int y = 1; y < playerBoard.size; y++) {
				char c = playerBoard.shipPositions[x][y];
				if (c != ShipPlacement.EMPTY) {
					placeTiles[x][y].showShip(c);
				} else {
					placeTiles[x][y].setWater();
				}
			}
		}
	}

	private void updatePlacementPrompt() {
		if (placeIndex >= playerBoard.fleet.ships.length) {
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
		inputLocked = false;

		Pane playerPane = new Pane();
		playerTiles = buildGrid(playerPane, size, null);
		drawCoordinates(playerTiles, size);
		playerPane.setPrefSize(size * tileSize, size * tileSize);
		revealFleet(playerTiles, playerBoard);

		Pane enemyPane = new Pane();
		enemyTiles = buildGrid(enemyPane, size, this::handlePlayerShot);
		drawCoordinates(enemyTiles, size);
		enemyPane.setPrefSize(size * tileSize, size * tileSize);
		if (revealMapCheat) {
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

		opponent = new OllamaOpponent(OLLAMA_URL, OLLAMA_MODEL);
		new Thread(opponent::warmUp, "ollama-warmup").start();

		setBanner("YOUR TURN  —  fire at the enemy waters", CYAN, false);
		refreshPanel("Fire at the enemy waters on the right.", "Waiting for your first shot...");

		stage.setScene(new Scene(battleStack));
		sizeToScene();
	}

	private void handlePlayerShot(int x, int y) {
		if (inputLocked) {
			return;
		}
		BoardState.Shot result = enemyBoard.fireAt(x, y);
		if (result == BoardState.Shot.ALREADY_FIRED) {
			lblYou.setText("You already fired at " + coord(x, y) + ".");
			return;
		}
		renderShot(enemyTiles[x][y], result, enemyBoard, x, y);
		lblYou.setText("You fired at " + coord(x, y) + " - " + describePlayerResult(result, enemyBoard, x, y));
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
		new Thread(() -> {
			int[] cell = opponent.chooseTarget(playerBoard);
			boolean fromModel = opponent.lastMoveFromModel();
			long ms = opponent.lastMoveMillis();
			Platform.runLater(() -> resolveAiShot(cell, fromModel, ms));
		}, "ollama-move").start();
	}

	private void resolveAiShot(int[] cell, boolean fromModel, long ms) {
		if (cell == null) {
			inputLocked = false;
			return;
		}
		int x = cell[0];
		int y = cell[1];
		BoardState.Shot result = playerBoard.fireAt(x, y);
		renderShot(playerTiles[x][y], result, playerBoard, x, y);

		String source = fromModel ? "[" + OLLAMA_MODEL + "]" : "[random]";
		lblAi.setText("AI " + source + " fired at " + coord(x, y) + " - " + resultWord(result) + " (" + ms + "ms)");
		lblYou.setText(describeAiResult(result, playerBoard, x, y));
		updateStats();

		if (playerBoard.fleet.isDestroyed()) {
			endGame(false);
			return;
		}
		inputLocked = false;
		setBanner("YOUR TURN  —  fire at the enemy waters", CYAN, false);
	}

	private void renderShot(Tile tile, BoardState.Shot result, BoardState board, int x, int y) {
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
		setBanner(playerWon ? "VICTORY" : "DEFEAT", playerWon ? GREEN : RED, false);

		Label big = neonLabel(playerWon ? "VICTORY!" : "DEFEAT", 72, playerWon ? GREEN : RED);
		Label sub = neonLabel(playerWon ? "You sank the entire enemy fleet." : "The enemy sank your entire fleet.", 20,
				CYAN);
		Button again = neonButton("PLAY AGAIN", CYAN);
		again.setOnAction(e -> showStartScreen());
		Button quit = neonButton("QUIT", MAGENTA);
		quit.setOnAction(e -> Platform.exit());

		VBox card = new VBox(18, big, sub, spacer(10), new HBox(16, again, quit) {
			{
				setAlignment(Pos.CENTER);
			}
		});
		card.setAlignment(Pos.CENTER);
		card.setPadding(new Insets(40));
		card.setMaxSize(560, 360);
		card.setBackground(new javafx.scene.layout.Background(new javafx.scene.layout.BackgroundFill(PANEL_BG,
				new javafx.scene.layout.CornerRadii(16), Insets.EMPTY)));
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
		lblYourShips = statLabel(GREEN);
		lblEnemyShips = statLabel(RED);
		lblAccuracy = statLabel(YELLOW);
		lblYou = logLabel(CYAN);
		lblAi = logLabel(MAGENTA);

		VBox panel = new VBox(10, neonLabel("BATTLESHIP v2", 22, CYAN), neonLabel("by James Potratz", 12, MAGENTA),
				spacer(6), lblYourShips, lblEnemyShips, lblAccuracy, spacer(6), sectionLabel("YOU"), lblYou, spacer(4),
				sectionLabel("ENEMY AI"), lblAi);
		panel.setPadding(new Insets(16));
		panel.setPrefWidth(PANEL_W);
		panel.setMinWidth(PANEL_W);
		panel.setBackground(new javafx.scene.layout.Background(new javafx.scene.layout.BackgroundFill(PANEL_BG,
				new javafx.scene.layout.CornerRadii(14), Insets.EMPTY)));
		panel.setBorder(neonBorder(CYAN, 14));
		return panel;
	}

	private void refreshPanel(String you, String ai) {
		lblYou.setText(you);
		lblAi.setText(ai);
		updateStats();
	}

	private void updateStats() {
		String accuracy = enemyBoard.shots == 0 ? "100" : actiongame.getAccuracy(enemyBoard.shots, enemyBoard.hits);
		lblYourShips.setText("Your ships:    " + playerBoard.fleet.shipsRemaining() + " / 5");
		lblEnemyShips.setText("Enemy ships:  " + enemyBoard.fleet.shipsRemaining() + " / 5");
		lblAccuracy.setText("Your accuracy: " + accuracy + "%");
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

	private int boardSizeForDifficulty(int difficulty) {
		if (difficulty == 1) {
			return 7;
		}
		if (difficulty == 3) {
			return 13;
		}
		return 10;
	}

	// Pick a tile size so `boards` boards plus the side panel fit on this screen.
	private int computeTileSize(int size, int boards) {
		Rectangle2D vb = Screen.getPrimary().getVisualBounds();
		double availW = vb.getWidth() - PANEL_W - (boards + 1) * GAP - 40;
		double availH = vb.getHeight() - BANNER_H - 90;
		int byW = (int) Math.floor(availW / (boards * size));
		int byH = (int) Math.floor(availH / size);
		return Math.max(18, Math.min(52, Math.min(byW, byH)));
	}

	private Tile[][] buildGrid(Pane pane, int size, CellHandler handler) {
		Tile[][] tiles = new Tile[size][size];
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				Tile tile = new Tile(x, y, handler);
				tile.setTranslateX(x * tileSize);
				tile.setTranslateY(y * tileSize);
				pane.getChildren().add(tile);
				tiles[x][y] = tile;
			}
		}
		return tiles;
	}

	private void drawCoordinates(Tile[][] tiles, int size) {
		char letter = 'A';
		int number = 1;
		for (int i = 1; i < size; i++) {
			tiles[0][i].drawLabel("" + letter);
			tiles[i][0].drawLabel("" + number);
			letter += 1;
			number += 1;
		}
	}

	private void revealFleet(Tile[][] tiles, BoardState board) {
		for (int x = 1; x < board.size; x++) {
			for (int y = 1; y < board.size; y++) {
				if (board.shipPositions[x][y] != ShipPlacement.EMPTY) {
					tiles[x][y].showShip(board.shipPositions[x][y]);
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
		return x + "," + actiongame.convertCoord(y);
	}

	private String resultWord(BoardState.Shot result) {
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

	private String describePlayerResult(BoardState.Shot result, BoardState board, int x, int y) {
		if (result == BoardState.Shot.MISS) {
			return "miss.";
		}
		Ship ship = board.fleet.shipForIcon(board.shipPositions[x][y]);
		if (result == BoardState.Shot.SUNK) {
			return "you SANK their " + ship.name + "!";
		}
		return "hit their " + ship.name + "!";
	}

	private String describeAiResult(BoardState.Shot result, BoardState board, int x, int y) {
		if (result == BoardState.Shot.MISS) {
			return "The enemy fired at your waters and missed.";
		}
		Ship ship = board.fleet.shipForIcon(board.shipPositions[x][y]);
		if (result == BoardState.Shot.SUNK) {
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

	private static javafx.scene.layout.Background solid(Color c) {
		return new javafx.scene.layout.Background(new javafx.scene.layout.BackgroundFill(c, null, Insets.EMPTY));
	}

	private static javafx.scene.layout.Border neonBorder(Color c, double radius) {
		return new javafx.scene.layout.Border(new javafx.scene.layout.BorderStroke(c,
				javafx.scene.layout.BorderStrokeStyle.SOLID, new javafx.scene.layout.CornerRadii(radius),
				new javafx.scene.layout.BorderWidths(2)));
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
		private final Text label = new Text();
		private final boolean playable;
		private final boolean clickable;
		private boolean spent = false; // already shot or occupied by a shown ship

		public Tile(int x, int y, CellHandler handler) {
			playable = (x != 0 && y != 0);
			clickable = playable && handler != null;

			double arc = tileSize * 0.28;
			bg = new Rectangle(tileSize - 2, tileSize - 2);
			bg.setArcWidth(arc);
			bg.setArcHeight(arc);
			bg.setFill(playable ? WATER : Color.TRANSPARENT);
			bg.setStroke(playable ? GRID : Color.TRANSPARENT);
			bg.setStrokeWidth(1.5);

			label.setFont(mono(tileSize * 0.42, FontWeight.BOLD));
			label.setFill(Color.web("#8fd7ea"));

			setAlignment(Pos.CENTER);
			getChildren().addAll(bg, label);

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

		public void drawLabel(String s) {
			label.setText(s);
		}

		public void setWater() {
			spent = false;
			bg.setFill(WATER);
			bg.setStroke(GRID);
			removeMarkers();
		}

		public void showShip(char icon) {
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
			getChildren().removeIf(n -> n != bg && n != label);
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
