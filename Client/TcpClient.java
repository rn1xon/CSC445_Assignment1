package Client;

import java.io.*;
import java.net.*;

public class TcpClient {
    private static final String SERVER_HOST = "localhost"; 
    private static final int PORT = 27003;
    private static final long INITIAL_KEY = 123456789L;

    public static void main(String[] args) throws IOException {
        Socket socket = new Socket(SERVER_HOST, PORT);
        socket.setTcpNoDelay(true);
        System.out.println("Connected to server at " + SERVER_HOST + ":" + PORT);

        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        DataInputStream in = new DataInputStream(socket.getInputStream());

        System.out.println("\n--- Starting RTT Tests ---");
        int[] rttSizes = {8, 64, 256, 512};
        for (int size : rttSizes) {
            measureRTT(size, in, out);
        }

        System.out.println("\n--- Starting Throughput Tests ---");
        measureThroughput(1024, 1024, in, out);
        measureThroughput(2048, 512, in, out);
        measureThroughput(4096, 256, in, out);

        socket.close();
        System.out.println("Tests complete. Disconnected.");
    }

    private static void measureRTT(int size, DataInputStream in, DataOutputStream out) throws IOException {
        String payload = "RTT" + generatePadding(size - 3);
        System.out.println("\n[Client] Sending " + size + " bytes: " + payload);
        
        byte[] encrypted = cipher(payload.getBytes(), INITIAL_KEY);

        long start = System.nanoTime();
        out.writeInt(encrypted.length);
        out.write(encrypted);

        int responseLen = in.readInt();
        byte[] responseBytes = new byte[responseLen];
        in.readFully(responseBytes);
        long end = System.nanoTime();

        byte[] decryptedResponse = cipher(responseBytes, INITIAL_KEY);
        System.out.println("[Client] Received " + responseLen + " bytes: " + new String(decryptedResponse));
        System.out.println("-> RTT for " + size + " bytes: " + (end - start) / 1_000_000.0 + " ms");
    }

    private static void measureThroughput(int numMessages, int msgSize, DataInputStream in, DataOutputStream out) throws IOException {
        String testData = "THROUGHPUT" + generatePadding(msgSize - 10);
        byte[] encrypted = cipher(testData.getBytes(), INITIAL_KEY);

        System.out.println("Running throughput test: " + numMessages + " messages of " + msgSize + " bytes...");
        long start = System.nanoTime();
        for (int i = 0; i < numMessages; i++) {
            out.writeInt(encrypted.length);
            out.write(encrypted);

            int ackLen = in.readInt();
            byte[] ackBytes = new byte[ackLen];
            in.readFully(ackBytes);
        }
        long end = System.nanoTime();

        double bits = numMessages * msgSize * 8.0;
        double seconds = (end - start) / 1_000_000_000.0;
        System.out.printf("-> Throughput for %d x %dB: %.2f bps%n", numMessages, msgSize, bits / seconds);
    }

    private static String generatePadding(int length) {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < length) sb.append("A");
        return sb.toString();
    }

    private static byte[] cipher(byte[] input, long key) {
        byte[] output = new byte[input.length];
        for (int i = 0; i < input.length; i += 8) {
            long block = 0;
            int chunk = Math.min(8, input.length - i);
            for (int j = 0; j < chunk; j++) {
                block |= ((long) input[i + j] & 0xFF) << (j * 8);
            }
            long ciphered = block ^ key;
            key = xorShift(key);
            for (int j = 0; j < chunk; j++) {
                output[i + j] = (byte) ((ciphered >> (j * 8)) & 0xFF);
            }
        }
        return output;
    }

    private static long xorShift(long r) {
        r ^= r << 13;
        r ^= r >>> 7;
        r ^= r << 17;
        return r;
    }
}