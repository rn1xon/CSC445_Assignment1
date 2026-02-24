import java.io.*;
import java.net.*;

public class TcpServer {
    private static final int PORT = 27003;
    private static final long INITIAL_KEY = 123456789L;

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("TCP Server started on port " + PORT + ". Waiting for client...");

        while (true) {
            Socket clientSocket = serverSocket.accept();
            clientSocket.setTcpNoDelay(true); 
            System.out.println("Client connected.");

            DataInputStream in = new DataInputStream(clientSocket.getInputStream());
            DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

            try {
                while (true) {
                    int len = in.readInt();
                    byte[] data = new byte[len];
                    in.readFully(data);

                    byte[] decryptedData = cipher(data, INITIAL_KEY);
                    String message = new String(decryptedData).trim();

                    if (message.startsWith("THROUGHPUT")) {
                        byte[] ack = cipher("ACK8BYTE".getBytes(), INITIAL_KEY);
                        out.writeInt(ack.length);
                        out.write(ack);
                    } else {
                        System.out.println("Server received RTT payload: " + message);
                        byte[] response = cipher(decryptedData, INITIAL_KEY);
                        out.writeInt(response.length);
                        out.write(response);
                    }
                }
            } catch (EOFException e) {
                System.out.println("Client disconnected.");
            }
            clientSocket.close();
        }
    }

    // XOR is reversable (function for encrypting and decrypting)
    // takes message, breaks into chunks, xors each chunk 
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