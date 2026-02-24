import java.io.*;
import java.net.*;

public class UdpServer {
    private static final int PORT = 26971;
    private static final long INITIAL_KEY = 123456789L;

    public static void main(String[] args) throws IOException {
        DatagramSocket socket = new DatagramSocket(PORT);
        System.out.println("UDP Server started on port " + PORT);

        byte[] receiveBuffer = new byte[4096]; // Large enough for all tests

        while (true) {
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket.receive(receivePacket);

            byte[] data = new byte[receivePacket.getLength()];
            System.arraycopy(receivePacket.getData(), 0, data, 0, receivePacket.getLength());

            byte[] decryptedData = cipher(data, INITIAL_KEY);
            String message = new String(decryptedData).trim();

            byte[] responseBytes;
            if (message.startsWith("THROUGHPUT")) {
                responseBytes = cipher("ACK8BYTE".getBytes(), INITIAL_KEY);
            } else {
                responseBytes = cipher(decryptedData, INITIAL_KEY); // Normal Echo
            }

            DatagramPacket sendPacket = new DatagramPacket(
                responseBytes, responseBytes.length, 
                receivePacket.getAddress(), receivePacket.getPort()
            );
            socket.send(sendPacket);
        }
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
        r ^= r << 17; 
        return r;
    }
}