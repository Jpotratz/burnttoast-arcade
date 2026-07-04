// arcade.battleship
// The battleship-specific half of the LLM opponent: describe the board to the
// model and parse its reply into a legal shot. Coordinates are presented to the
// model 1-based (models handle "1..9" better than "0..8") and converted back.
// The prompt reveals only fired cells and their hit/miss results -- never an
// unfired ship location -- so the AI plays fair.

package arcade.battleship;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import arcade.core.Coord;
import arcade.core.PromptStrategy;

public class BattleshipPrompts implements PromptStrategy<BoardState, Coord> {

	private static final Pattern NUMBER = Pattern.compile("\\d+");

	@Override
	public String buildPrompt(BoardState target) {
		int n = target.size;
		List<String> hits = new ArrayList<>();
		List<String> misses = new ArrayList<>();
		for (int x = 0; x < n; x++) {
			for (int y = 0; y < n; y++) {
				if (target.shotsFired[x][y]) {
					String cell = "(" + (x + 1) + "," + (y + 1) + ")";
					if (target.isHit(x, y)) {
						hits.add(cell);
					} else {
						misses.add(cell);
					}
				}
			}
		}
		StringBuilder sb = new StringBuilder();
		sb.append("You are playing Battleship as the attacker. ");
		sb.append("The grid has columns 1-").append(n).append(" and rows 1-").append(n).append(". ");
		sb.append("Ships are straight horizontal or vertical lines of cells. ");
		sb.append("Sink every ship in as few shots as possible. ");
		sb.append("Your hits so far: ").append(hits.isEmpty() ? "none" : String.join(",", hits)).append(". ");
		sb.append("Your misses so far: ").append(misses.isEmpty() ? "none" : String.join(",", misses)).append(". ");
		sb.append("If a hit has unsunk neighbors, fire adjacent to your hits to finish the ship. ");
		sb.append("Pick a cell you have NOT already fired at. ");
		sb.append("Respond with ONLY two integers 'column row', each between 1 and ").append(n)
				.append(", and nothing else.");
		return sb.toString();
	}

	// Parse the model's reply into a legal 0-based move, or null if unusable.
	// Reasoning models may emit lots of numbers, so we take the LAST in-range,
	// unfired pair -- that tends to be the model's final answer.
	@Override
	public Coord parseResponse(String text, BoardState target) {
		if (text == null) {
			return null;
		}
		List<Integer> nums = new ArrayList<>();
		Matcher m = NUMBER.matcher(text);
		while (m.find()) {
			try {
				nums.add(Integer.parseInt(m.group()));
			} catch (NumberFormatException ignored) {
				// skip absurdly long number-like strings
			}
		}
		for (int i = nums.size() - 2; i >= 0; i--) {
			int x = nums.get(i) - 1;
			int y = nums.get(i + 1) - 1;
			if (target.inBounds(x, y) && !target.shotsFired[x][y]) {
				return new Coord(x, y);
			}
		}
		return null;
	}
}
