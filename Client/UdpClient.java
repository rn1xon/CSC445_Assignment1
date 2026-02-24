package Client;

import java.io.*;
import java.net.*;
import java.util.ArrayList;

class UdpMeasurement {
    String type;
    int size;
    double value;

    UdpMeasurement(String type, int size, double value) {
        this.type = type; this.size = size; this.value = value;
    }
}

public class UdpClient {
    private static final String SERVER_HOST = "moxie.cs.oswego.edu"; 
    private static final int PORT = 26971;
    private static final long INITIAL_KEY = 123456789L;
    private static final int SAMPLES = 30;

    private static ArrayList<UdpMeasurement> results = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        DatagramSocket socket = new DatagramSocket();
        socket.setSoTimeout(1000); // 1-second timeout for UDP lost packets
        InetAddress address = InetAddress.getByName(SERVER_HOST);

        System.out.println("--- Starting UDP Tests (n=" + SAMPLES + ") ---");

        // RTT Tests
        int[] rttSizes = {8, 64, 256, 512};
        for (int s : rttSizes) {
            double totalMs = 0;
            int successfulSamples = 0;
            for (int i = 0; i < SAMPLES; i++) {
                double ms = measureRTT(s, socket, address);
                if (ms > 0) { // Only count if packet wasn't lost
                    totalMs += ms;
                    successfulSamples++;
                }
            }
            if (successfulSamples > 0) {
                double avgMs = totalMs / successfulSamples;
                results.add(new UdpMeasurement("RTT", s, avgMs));
                System.out.printf("Avg RTT for %db: %.4f ms (%d/%d arrived)%n", s, avgMs, successfulSamples, SAMPLES);
            }
        }

        // Throughput Tests
        int[][] tpTests = {{1024, 1024}, {2048, 512}, {4096, 256}};
        for (int[] t : tpTests) {
            double totalBps = 0;
            for (int i = 0; i < SAMPLES; i++) {
                totalBps += measureThroughput(t[0], t[1], socket, address);
            }
            double avgBps = totalBps / SAMPLES;
            results.add(new UdpMeasurement("Throughput", t[1], avgBps));
            System.out.printf("Avg Throughput for %dx%db: %.2f bps%n", t[0], t[1], avgBps);
        }

        printChartData();
        socket.close();
    }

    private static void printChartData() {
        System.out.println("\n--- COPY DATA FOR CHART.JS ---");
        System.out.print("UDP RTT Labels: [");
        results.stream().filter(m -> m.type.equals("RTT")).forEach(m -> System.out.print(m.size + ", "));
        System.out.print("]\nUDP RTT Data: [");
        results.stream().filter(m -> m.type.equals("RTT")).forEach(m -> System.out.print(m.value + ", "));
        
        System.out.print("]\n\nUDP Throughput Labels: [");
        results.stream().filter(m -> m.type.equals("Throughput")).forEach(m -> System.out.print(m.size + ", "));
        System.out.print("]\nUDP Throughput Data: [");
        results.stream().filter(m -> m.type.equals("Throughput")).forEach(m -> System.out.print((m.value / 1000000.0) + ", "));
        System.out.println("]\n------------------------------");
    }

    private static double measureRTT(int size, DatagramSocket socket, InetAddress address) throws IOException {
        String payload = "A".repeat(size);
        byte[] encrypted = cipher(payload.getBytes(), INITIAL_KEY);
        DatagramPacket sendPacket = new DatagramPacket(encrypted, encrypted.length, address, PORT);
        
        byte[] receiveBuf = new byte[size + 100];
        DatagramPacket receivePacket = new DatagramPacket(receiveBuf, receiveBuf.length);

        long start = System.nanoTime();
        socket.send(sendPacket);
        try {
            socket.receive(receivePacket);
            return (System.nanoTime() - start) / 1_000_000.0;
        } catch (SocketTimeoutException e) {
            return -1.0; // Packet loss
        }
    }

    private static double measureThroughput(int count, int size, DatagramSocket socket, InetAddress address) throws IOException {
        byte[] encrypted = cipher("THROUGHPUT".repeat(size).substring(0, size).getBytes(), INITIAL_KEY);
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            DatagramPacket sendPacket = new DatagramPacket(encrypted, encrypted.length, address, PORT);
            socket.send(sendPacket);
            byte[] ackBuf = new byte[8];
            DatagramPacket ackPacket = new DatagramPacket(ackBuf, ackBuf.length);
            try { socket.receive(ackPacket); } catch (SocketTimeoutException e) { /* Ignore dropped ACKs in throughput */ }
        }
        double seconds = (System.nanoTime() - start) / 1_000_000_000.0;
        return (count * size * 8.0) / seconds;
    }

    private static byte[] cipher(byte[] input, long key) {
        byte[] output = new byte[input.length];
        for (int i = 0; i < input.length; i += 8) {
            long block = 0;
            int chunk = Math.min(8, input.length - i);
            for (int j = 0; j < chunk; j++) block |= ((long) input[i + j] & 0xFF) << (j * 8);
            long ciphered = block ^ key;
            key = xorShift(key);
            for (int j = 0; j < chunk; j++) output[i + j] = (byte) ((ciphered >> (j * 8)) & 0xFF);
        }
        return output;
    }

    private static long xorShift(long r) {
        r ^= r << 13; r ^= r >>> 7; r ^= r << 17; return r;
    }
}