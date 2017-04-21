import java.net.Socket;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.lang.StringBuilder;

public class PhysLayerClient {
	private static Map<String, Integer> bitTransTable = new HashMap<String, Integer>();
	private static int[] receivedBytes = new int[320];
	private static String[] signals = new String[320];
	private static byte[] decodedBytes = new byte[32];
	private static double baseline;

	public static void main(String[] args) {
		try (Socket socket = new Socket("codebank.xyz", 38002)) {
			String NRZIback = "";
			System.out.println("Connected to server.");

			// Create byte streams to communicate to Server
			InputStream is = socket.getInputStream();
			OutputStream os = socket.getOutputStream();

			create4B5BTable();
			baseline = calcBaseline(is);
			getData(is);
			getSignal(320);
			NRZIback = decodeNRZI();
			reverseTranslate(NRZIback);
			printDecode();

			// Send decoded array to Server
			for (byte b : decodedBytes) {
				os.write(b);
			}

			// Check Server response
			int check = is.read();
			getResponse(check);

		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("Disconnected from Server.");
	}

	private static void create4B5BTable() {
		bitTransTable.put("11110", 0);
		bitTransTable.put("01001", 1);
		bitTransTable.put("10100", 2);
		bitTransTable.put("10101", 3);
		bitTransTable.put("01010", 4);
		bitTransTable.put("01011", 5);
		bitTransTable.put("01110", 6);
		bitTransTable.put("01111", 7);
		bitTransTable.put("10010", 8);
		bitTransTable.put("10011", 9);
		bitTransTable.put("10110", 10);
		bitTransTable.put("10111", 11);
		bitTransTable.put("11010", 12);
		bitTransTable.put("11011", 13);
		bitTransTable.put("11100", 14);
		bitTransTable.put("11101", 15);
	}

	private static double calcBaseline(InputStream is) throws IOException {
		double average = 0;
		for (int i = 0; i < 64; i++) {
			double signal = (double) is.read();
			average += signal;
		}
		average = average / 64.0;
		System.out.printf("Baseline established from preamble: %.2f", average);
		System.out.println();
		return average;
	}

	private static void getSignal(int numSignals) {
		int value;
		for (int i = 0; i < numSignals; i++) {
			value = receivedBytes[i];

			if (value > baseline) {
				signals[i] = "H";
			} else {
				signals[i] = "L";
			}
		}
	}

	private static String decodeNRZI() {
		StringBuilder decode = new StringBuilder();
		// Set initial signal in decode string
		if (signals[0].equals("L")) {
			decode.append("0");
		} else {
			decode.append("1");
		}

		// Apply NRZI backwards for the remainder of signals
		for (int i = 1; i < 320; i++) {
			if (signals[i].equals("L")) {
				if (signals[i - 1].equals("L")) {
					decode.append("0");
				} else if (signals[i - 1].equals("H")) {
					decode.append("1");
				}
			} else if (signals[i].equals("H")) {
				if (signals[i - 1].equals("L")) {
					decode.append("1");
				} else if (signals[i - 1].equals("H")) {
					decode.append("0");
				}
			}
		}
		return decode.toString();
	}

	private static void reverseTranslate(String NRZIback) {
		String upperByte = "";
		String lowerByte = "";
		int upperIndex = 0;
		int lowerIndex = 5;

		// Read 10 signals each iteration
		for (int i = 0; i < 32; i++) {
			while (upperIndex < (5 * ((i * 2) + 1))) {
				upperByte += NRZIback.charAt(upperIndex);
				upperIndex++;
			}
			upperIndex += 5;

			while (lowerIndex < (10 * (i + 1))) {
				lowerByte += NRZIback.charAt(lowerIndex);
				lowerIndex++;
			}
			lowerIndex += 5;

			int upperBits = bitTransTable.get(upperByte);
			int lowerBits = bitTransTable.get(lowerByte);

			decodedBytes[i] = (byte) (((upperBits << 4) | lowerBits) & 0xFF);

			upperByte = "";
			lowerByte = "";
		}
	}

	private static void printDecode() {
		System.out.print("Received 32 bytes: ");
		for (byte b : decodedBytes) {
			System.out.printf("%02X", b);
		}
		System.out.println();
	}

	private static void getData(InputStream is) throws IOException {
		int value;
		for (int i = 0; i < 320; i++) {
			value = (int) is.read();
			receivedBytes[i] = value;
		}
	}

	private static void getResponse(int i) {
		if (i == 1) {
			System.out.println("Response good.");
		} else {
			System.out.println("Response bad.");
		}
	}
}