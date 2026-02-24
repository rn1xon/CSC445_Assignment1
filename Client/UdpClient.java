package Client;

import java.io.*;
import java.net.*;

public class UdpClient {
    private static final String SERVER_HOST = "localhost";
    private static final int PORT = 27003;
    private static final long INITIAL_KEY = 123456789L;

    public static void main(String[] args) throws IOException {
        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(2000); 
        InetAddress address = InetAddress.getByName(SERVER_HOST);

        System.out.println("\n--- Starting UDP RTT Tests ---");
        int[] rttSizes = {8, 64, 256, 512};
        for (int size : rttSizes) {
            measureRTT(size, socket, address);
        }

        System.out.println("\n--- Starting UDP Throughput Tests ---");
        measureThroughput(1024, 1024, socket, address);
        measureThroughput(2048, 512, socket, address);
        measureThroughput(4096, 256, socket, address);

        socket.close();
        System.out.println("Tests complete.");
    }

    private static void measureRTT(int size, DatagramSocket socket, InetAddress address) throws IOException {
        String payload = "RTT" + generatePadding(size - 3);
        System.out.println("\n[Client] Sending " + size + " bytes: " + payload);
        byte[] encrypted = cipher(payload.getBytes(), INITIAL_KEY);

        DatagramPacket sendPacket = new DatagramPacket(encrypted, encrypted.length, address, PORT);
        byte[] receiveBuf = new byte[size];
        DatagramPacket receivePacket = new DatagramPacket(receiveBuf, receiveBuf.length);

        long start = System.nanoTime();
        socket.send(sendPacket);
        try {
            socket.receive(receivePacket);
            long end = System.nanoTime();
            byte[] decryptedResponse = cipher(receivePacket.getData(), INITIAL_KEY);
            System.out.println("[Client] Received: " + new String(decryptedResponse));
            System.out.println("-> UDP RTT for " + size + " bytes: " + (end - start) / 1_000_000.0 + " ms");
        } catch (SocketTimeoutException e) {
            System.out.println("-> Packet lost (Timeout)");
        }
    }

    private static void measureThroughput(int numMessages, int msgSize, DatagramSocket socket, InetAddress address) throws IOException {
        String testData = "THROUGHPUT" + generatePadding(msgSize - 10);
        byte[] encrypted = cipher(testData.getBytes(), INITIAL_KEY);

        System.out.println("Running UDP throughput: " + numMessages + " messages of " + msgSize + " bytes...");
        long start = System.nanoTime();
        for (int i = 0; i < numMessages; i++) {
            DatagramPacket sendPacket = new DatagramPacket(encrypted, encrypted.length, address, PORT);
            socket.send(sendPacket);

            byte[] ackBuf = new byte[8];
            DatagramPacket ackPacket = new DatagramPacket(ackBuf, ackBuf.length);
            try {
                socket.receive(ackPacket);
            } catch (SocketTimeoutException e) {
                // In UDP throughput tests, packet loss is expected.
            }
        }
        long end = System.nanoTime();

        double bits = numMessages * msgSize * 8.0;
        double seconds = (end - start) / 1_000_000_000.0;
        System.out.printf("-> UDP Throughput for %d x %dB: %.2f bps%n", numMessages, msgSize, bits / seconds);
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
