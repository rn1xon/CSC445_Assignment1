package Client;

import java.io.*;
import java.net.*;
import java.util.ArrayList;

class Measurement {
    String type;
    int size;
    double value;

    Measurement(String type, int size, double value) {
        this.type = type;
        this.size = size;
        this.value = value;
    }
}

public class TcpClient {
    private static final String SERVER_HOST = "moxie.cs.oswego.edu"; 
    private static final int PORT = 26971;
    private static final long INITIAL_KEY = 123456789L;
    private static final int SAMPLES = 30; // 30 iterations

    private static ArrayList<Measurement> results = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        try (Socket socket = new Socket(SERVER_HOST, PORT)) {
            socket.setTcpNoDelay(true);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            System.out.println("--- Starting TCP Tests (n=" + SAMPLES + ") ---");

            // Run Latency Tests
            int[] rttSizes = {8, 64, 256, 512};
            for (int s : rttSizes) {
                double totalMs = 0;
                for (int i = 0; i < SAMPLES; i++) {
                    totalMs += measureRTT(s, in, out);
                }
                double avgMs = totalMs / SAMPLES;
                results.add(new Measurement("RTT", s, avgMs));
                System.out.printf("Avg RTT for %db: %.4f ms%n", s, avgMs);
            }

            // Run Throughput Tests
            int[][] tpTests = {{1024, 1024}, {2048, 512}, {4096, 256}};
            for (int[] t : tpTests) {
                double totalBps = 0;
                for (int i = 0; i < SAMPLES; i++) {
                    totalBps += measureThroughput(t[0], t[1], in, out);
                }
                double avgBps = totalBps / SAMPLES;
                results.add(new Measurement("Throughput", t[1], avgBps));
                System.out.printf("Avg Throughput for %dx%db: %.2f bps%n", t[0], t[1], avgBps);
            }

            printChartData();
        } catch (ConnectException e) {
            System.out.println("Could not connect! Check your Server and SSH tunnel.");
        }
    }

    private static void printChartData() {
        System.out.println("\n--- COPY DATA FOR CHART.JS ---");
        System.out.print("TCP RTT Labels: [");
        results.stream().filter(m -> m.type.equals("RTT")).forEach(m -> System.out.print(m.size + ", "));
        System.out.print("]\nTCP RTT Data: [");
        results.stream().filter(m -> m.type.equals("RTT")).forEach(m -> System.out.print(m.value + ", "));
        
        System.out.print("]\n\nTCP Throughput Labels: [");
        results.stream().filter(m -> m.type.equals("Throughput")).forEach(m -> System.out.print(m.size + ", "));
        System.out.print("]\nTCP Throughput Data: [");
        // Dividing by 1,000,000 to convert to Mbps for a cleaner graph
        results.stream().filter(m -> m.type.equals("Throughput")).forEach(m -> System.out.print((m.value / 1000000.0) + ", "));
        System.out.println("]\n------------------------------");
    }

    private static double measureRTT(int size, DataInputStream in, DataOutputStream out) throws IOException {
        String payload = "A".repeat(size);
        byte[] encrypted = cipher(payload.getBytes(), INITIAL_KEY);
        long start = System.nanoTime();
        out.writeInt(encrypted.length);
        out.write(encrypted);
        in.readInt();
        in.readFully(new byte[size]);
        return (System.nanoTime() - start) / 1_000_000.0;
    }

    private static double measureThroughput(int count, int size, DataInputStream in, DataOutputStream out) throws IOException {
        byte[] encrypted = cipher("THROUGHPUT".repeat(size).substring(0, size).getBytes(), INITIAL_KEY);
        long start = System.nanoTime();
        for (int i = 0; i < count; i++) {
            out.writeInt(encrypted.length);
            out.write(encrypted);
            in.readInt();
            in.readFully(new byte[8]);
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
        r ^= r << 13; 
        r ^= r >>> 7; 
        r ^= r << 17; return r;
    }
}