// James Potratz CSIS 222 - MCC (v2)
// The JavaFX front-end for Battleship v2. Three scenes, swapped on one Stage:
//   1) Start   - pick difficulty + cheat, in-scene (no more popup dialogs).
//   2) Placement - click to place your own five ships (R to rotate).
//   3) Battle  - your fleet (left) vs enemy waters (right); you and the computer
//                fire on alternating turns, results shown in color.
// All rules live in the pure classes (BoardState, Fleet, Ship, ShipPlacement) and
// the computer's targeting lives behind the Opponent interface (OllamaOpponent,
// backed by RandomOpponent). This class is only the UI + turn flow.

package battleship;

import java.util.Random;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Stage;

public class gameboard extends Application {

	// ---- layout + color constants -------------------------------------------
	private static final int TILE = 50; // pixel size of one grid cell
	private static final int GAP = 60; // gap between the two battle boards
	private static final int TITLE_H = 34; // headroom above the boards for titles

	private static final Color WATER = Color.web("#9fd3e0"); // unshot water
	private static final Color MISS = Color.web("#b8c2c9"); // shot, empty
	private static final Color HIT = Color.web("#d64545"); // shot a ship
	private static final Color SUNK = Color.web("#7a1f1f"); // a fully sunk ship
	private static final Color OWN_SHIP = Color.web("#51606b"); // your unhit ship

	// ---- Ollama opponent config (easy to change) ----------------------------
	private static final String OLLAMA_URL = "http://192.168.1.210:30068";
	private static final String OLLAMA_MODEL = "qwen2.5:7b";

	// ---- shared state --------------------------------------------------------
	private Stage stage;
	private final Random rng = new Random();
	public static boolean revealMapCheat = false;
	public static Scoreboard scoreboard = new Scoreboard();

	// ---- placement-screen state ---------------------------------------------
	private Tile[][] placeTiles;
	private int placeIndex; // which ship (0..4) we're placing next
	private boolean placeHorizontal = true; // current placement orientation
	private Label placePrompt;
	private Button startBattleBtn;

	// ---- battle state --------------------------------------------------------
	private Pane battleRoot;
	private BoardState playerBoard; // your fleet; the AI fires here
	private BoardState enemyBoard; // enemy fleet; you fire here
	private Tile[][] playerTiles;
	private Tile[][] enemyTiles;
	private Opponent opponent;
	private boolean inputLocked = false; // true while the AI is taking its turn
	private String playerAction = "Fire at the enemy waters on the right.";
	private String aiAction = "Waiting for your first shot...";

	// A cell-click callback, so a grid can do different things on different screens.
	private interface CellHandler {
		void handle(int x, int y);
	}

	@Override
	public void start(Stage primaryStage) throws Exception {
		this.stage = primaryStage;
		stage.setTitle("Battleship v2");
		showStartScreen();
		stage.show();
		stage.toFront();
	}

	// =========================================================================
	// Screen 1: Start
	// =========================================================================

	private void showStartScreen() {
		Text title = new Text("BATTLESHIP v2");
		title.setFont(Font.font(40));
		Text credit = new Text("by James Potratz");
		credit.setFont(Font.font(16));

		ToggleGroup difficulty = new ToggleGroup();
		RadioButton beginner = difficultyOption("Beginner  (6 x 6)", 1, difficulty);
		RadioButton standard = difficultyOption("Standard  (9 x 9)", 2, difficulty);
		RadioButton advanced = difficultyOption("Advanced  (12 x 12)", 3, difficulty);
		standard.setSelected(true);

		CheckBox cheat = new CheckBox("Reveal the enemy fleet (cheat)");

		Button startBtn = new Button("Start");
		startBtn.setFont(Font.font(18));
		startBtn.setOnAction(e -> {
			battleship.difficulty = (int) difficulty.getSelectedToggle().getUserData();
			revealMapCheat = cheat.isSelected();
			showPlacementScreen();
		});

		VBox box = new VBox(14, title, credit, new Label("Choose your difficulty:"), beginner, standard, advanced,
				cheat, startBtn);
		box.setAlignment(Pos.CENTER);
		box.setPadding(new Insets(30));
		stage.setScene(new Scene(box, 520, 460));
	}

	private RadioButton difficultyOption(String text, int value, ToggleGroup group) {
		RadioButton rb = new RadioButton(text);
		rb.setUserData(value);
		rb.setToggleGroup(group);
		rb.setFont(Font.font(16));
		return rb;
	}

	// =========================================================================
	// Screen 2: Placement
	// =========================================================================

	private void showPlacementScreen() {
		int size = boardSizeForDifficulty(battleship.difficulty);
		playerBoard = new BoardState(size);
		placeIndex = 0;
		placeHorizontal = true;

		Pane boardPane = new Pane();
		placeTiles = buildGrid(boardPane, size, 0, this::handlePlacementClick);
		drawCoordinates(placeTiles, size);
		addTitle(boardPane, "PLACE YOUR FLEET", 0);
		boardPane.setPrefSize(size * TILE + 10, TITLE_H + size * TILE + 10);

		placePrompt = new Label();
		placePrompt.setFont(Font.font(15));
		placePrompt.setWrapText(true);
		placePrompt.setPrefWidth(260);

		Button rotateBtn = new Button("Rotate (R)");
		rotateBtn.setOnAction(e -> rotatePlacement());
		Button randomBtn = new Button("Place randomly");
		randomBtn.setOnAction(e -> randomPlacement());
		Button clearBtn = new Button("Clear");
		clearBtn.setOnAction(e -> clearPlacement());
		startBattleBtn = new Button("Start Battle");
		startBattleBtn.setFont(Font.font(16));
		startBattleBtn.setDisable(true);
		startBattleBtn.setOnAction(e -> showBattleScreen());

		VBox controls = new VBox(12, placePrompt, rotateBtn, randomBtn, clearBtn, startBattleBtn);
		controls.setPadding(new Insets(TITLE_H + 6, 12, 12, 18));
		controls.setPrefWidth(300);

		BorderPane rootPane = new BorderPane();
		rootPane.setCenter(boardPane);
		rootPane.setRight(controls);
		BorderPane.setMargin(boardPane, new Insets(0, 0, 0, 10));

		Scene scene = new Scene(rootPane);
		// Rotate with the R key too.
		scene.setOnKeyPressed(e -> {
			if (e.getCode() == KeyCode.R) {
				rotatePlacement();
			}
		});
		stage.setScene(scene);
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
			placePrompt.setText("That won't fit there (off-board or overlapping). Try another cell.\n\n"
					+ promptForCurrentShip());
		}
	}

	private void rotatePlacement() {
		placeHorizontal = !placeHorizontal;
		updatePlacementPrompt();
	}

	// Clear everything and drop the whole fleet down at random.
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

	// Repaint the placement grid from the ship-position array.
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
			placePrompt.setText("Fleet ready! Click \"Start Battle\" when you're set.");
		} else {
			placePrompt.setText(promptForCurrentShip());
		}
	}

	private String promptForCurrentShip() {
		Ship ship = playerBoard.fleet.ships[placeIndex];
		return "Place your " + ship.name + " (length " + ship.length + ").\nOrientation: "
				+ (placeHorizontal ? "Horizontal" : "Vertical") + "  -  click a starting cell, or press R to rotate.";
	}

	// =========================================================================
	// Screen 3: Battle
	// =========================================================================

	private void showBattleScreen() {
		int size = playerBoard.size;
		enemyBoard = new BoardState(size);
		enemyBoard.placeFleetRandomly(rng);
		inputLocked = false;
		playerAction = "Fire at the enemy waters on the right.";
		aiAction = "Waiting for your first shot...";

		battleRoot = new Pane();

		int boardPixel = size * TILE;
		int playerX = 0;
		int enemyX = boardPixel + GAP;
		int scoreX = 2 * boardPixel + 2 * GAP;

		playerTiles = buildGrid(battleRoot, size, playerX, null); // your board: not clickable
		enemyTiles = buildGrid(battleRoot, size, enemyX, this::handlePlayerShot);

		drawCoordinates(playerTiles, size);
		drawCoordinates(enemyTiles, size);
		addTitle(battleRoot, "YOUR FLEET", playerX);
		addTitle(battleRoot, "ENEMY WATERS", enemyX);

		revealFleet(playerTiles, playerBoard);
		if (revealMapCheat) {
			revealFleet(enemyTiles, enemyBoard);
		}

		scoreboard.setTranslateX(scoreX);
		scoreboard.setTranslateY(TITLE_H);
		battleRoot.getChildren().add(scoreboard);
		battleRoot.setPrefSize(scoreX + 300, Math.max(TITLE_H + boardPixel, 700));

		opponent = new OllamaOpponent(OLLAMA_URL, OLLAMA_MODEL);
		new Thread(opponent::warmUp, "ollama-warmup").start();

		updateScoreboard();
		stage.setScene(new Scene(battleRoot));
	}

	// Called when the player clicks an enemy-waters tile.
	private void handlePlayerShot(int x, int y) {
		if (inputLocked) {
			return;
		}
		BoardState.Shot result = enemyBoard.fireAt(x, y);
		if (result == BoardState.Shot.ALREADY_FIRED) {
			playerAction = "You already fired at " + coord(x, y) + ".";
			updateScoreboard();
			return;
		}
		renderShot(enemyTiles[x][y], result, enemyBoard, x, y);
		playerAction = "You fired at " + coord(x, y) + " - " + describePlayerResult(result, enemyBoard, x, y);
		updateScoreboard();

		if (enemyBoard.fleet.isDestroyed()) {
			endGame(true);
			return;
		}
		aiTurn();
	}

	// Run the computer's turn off the FX thread (the Ollama call can be slow), then
	// apply the result back on the FX thread.
	private void aiTurn() {
		inputLocked = true;
		aiAction = "Enemy AI is thinking...";
		updateScoreboard();
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
		aiAction = "Enemy AI " + source + " fired at " + coord(x, y) + " - " + resultWord(result) + " (" + ms + "ms)";
		playerAction = describeAiResult(result, playerBoard, x, y);
		updateScoreboard();

		if (playerBoard.fleet.isDestroyed()) {
			endGame(false);
			return;
		}
		inputLocked = false;
	}

	private void renderShot(Tile tile, BoardState.Shot result, BoardState board, int x, int y) {
		switch (result) {
		case MISS:
			tile.setFill(MISS);
			tile.drawChar('o');
			break;
		case HIT:
			tile.setFill(HIT);
			tile.drawChar(board.shipPositions[x][y]);
			break;
		case SUNK:
			tile.setFill(SUNK);
			tile.drawChar(board.shipPositions[x][y]);
			break;
		default:
			break;
		}
	}

	private void endGame(boolean playerWon) {
		String message = playerWon ? "You sank the entire enemy fleet! You win!"
				: "The enemy sank your entire fleet. You lose!";
		Alert alert = new Alert(AlertType.INFORMATION, message + "\nThanks for playing!");
		alert.setHeaderText(playerWon ? "Victory" : "Defeat");
		alert.showAndWait();
		Platform.exit();
	}

	// =========================================================================
	// Shared board-building + rendering helpers
	// =========================================================================

	// The playing grid is one larger than the visible board because row/col 0 hold
	// coordinate labels: 6x6 -> 7, 9x9 -> 10, 12x12 -> 13.
	private int boardSizeForDifficulty(int difficulty) {
		if (difficulty == 1) {
			return 7;
		}
		if (difficulty == 3) {
			return 13;
		}
		return 10; // standard (difficulty 2)
	}

	// Create one size x size grid of Tiles at the given x pixel offset, add them to
	// the given pane, and return the array (indexed [x][y]). A null handler makes a
	// non-interactive grid.
	private Tile[][] buildGrid(Pane pane, int size, int xOffset, CellHandler handler) {
		Tile[][] tiles = new Tile[size][size];
		for (int y = 0; y < size; y++) {
			for (int x = 0; x < size; x++) {
				Tile tile = new Tile(x, y, handler);
				tile.setTranslateX(xOffset + x * TILE);
				tile.setTranslateY(TITLE_H + y * TILE);
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
			tiles[0][i].drawChar(letter);
			tiles[i][0].drawInt(number);
			letter += 1;
			number += 1;
		}
	}

	private void addTitle(Pane pane, String words, int xOffset) {
		Text title = new Text(words);
		title.setFont(Font.font(20));
		title.setTranslateX(xOffset + 4);
		title.setTranslateY(24);
		pane.getChildren().add(title);
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

	// =========================================================================
	// Scoreboard + text helpers
	// =========================================================================

	public void updateScoreboard() {
		String accuracy = enemyBoard.shots == 0 ? "100" : actiongame.getAccuracy(enemyBoard.shots, enemyBoard.hits);
		String text = "Battleship v2\nby James Potratz\n\n" + "Your ships:   " + playerBoard.fleet.shipsRemaining()
				+ " / 5\n" + "Enemy ships:  " + enemyBoard.fleet.shipsRemaining() + " / 5\n" + "Your accuracy: "
				+ accuracy + "%\n\n" + "YOU:\n" + playerAction + "\n\n" + "ENEMY AI:\n" + aiAction;
		scoreboard.drawScore(text);
	}

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
	// Inner UI classes
	// =========================================================================

	public static class ScoreText extends Text {
		public ScoreText() {
			this.setWrappingWidth(300);
		}
	}

	public static class Scoreboard extends StackPane {
		private ScoreText scoretext = new ScoreText();

		public Scoreboard() {
			Rectangle border = new Rectangle(300, 700);
			border.setFill(null);
			scoretext.setFont(Font.font(20));
			setAlignment(Pos.TOP_LEFT);
			getChildren().addAll(border, scoretext);
		}

		private void drawScore(String words) {
			scoretext.setText(words);
		}
	}

	// One grid cell. Holds its own coordinates and, when given a handler, invokes it
	// on a left click of a playable (non-label) cell.
	public class Tile extends StackPane {
		private Text text = new Text();
		private Rectangle border = new Rectangle(TILE, TILE);
		public int xcoord;
		public int ycoord;

		public Tile(int x, int y, CellHandler handler) {
			xcoord = x;
			ycoord = y;
			border.setFill((x == 0 || y == 0) ? null : WATER);
			border.setStroke(Color.BLACK);
			text.setFont(Font.font(28));
			setAlignment(Pos.CENTER);
			getChildren().addAll(border, text);

			if (handler != null) {
				setOnMouseClicked(event -> {
					if (event.getButton() == MouseButton.PRIMARY && xcoord != 0 && ycoord != 0) {
						handler.handle(xcoord, ycoord);
					}
				});
			}
		}

		public void setFill(Color color) {
			border.setFill(color);
		}

		public void setWater() {
			border.setFill(WATER);
			text.setText("");
		}

		public void showShip(char icon) {
			border.setFill(OWN_SHIP);
			drawChar(icon);
		}

		public void drawChar(char letter) {
			text.setText("" + letter);
		}

		private void drawInt(int number) {
			text.setText("" + number);
		}
	}

	public static void main(String[] args) {
		launch(args);
	}
}
