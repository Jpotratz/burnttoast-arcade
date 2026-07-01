// James Potratz CSIS 222 - MCC (v2)
// In v1 this class held the hit/miss/win/lose logic for the single board. In v2
// that logic moved into the pure BoardState class (so it can be unit-tested), and
// this class is now just a couple of small display utilities plus the launcher the
// spec asks battleship.java to call.

package battleship;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

public class actiongame {

	// Accuracy as a string with up to two decimals: hits / shots * 100.
	public static String getAccuracy(int shotsFired, int hits) {
		DecimalFormat df = new DecimalFormat("##.##");
		df.setRoundingMode(RoundingMode.DOWN);
		double accuracy = ((double) hits / (double) shotsFired) * 100;
		return df.format(accuracy);
	}

	// Round a double to the given number of decimal places.
	public static double round(double d, int decimalPlace) {
		BigDecimal bd = new BigDecimal(Double.toString(d));
		bd = bd.setScale(decimalPlace, RoundingMode.HALF_UP);
		return bd.doubleValue();
	}

	// Convert a 1-based y coordinate to its display letter (1 -> A, 2 -> B, ...).
	public static char convertCoord(int ycoord) {
		return (char) ('A' + (ycoord - 1));
	}

	// Launch the JavaFX game. battleship.main delegates here per the assignment spec.
	// We hand off to gameboard.main so JavaFX's launch() infers the correct
	// Application subclass (gameboard) from its caller.
	public static void main(String[] args) {
		gameboard.main(args);
	}
}
