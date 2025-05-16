import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class Server {
    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java Server <port>");
            return;
        }

        int port;

        try {
            port = Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            System.out.println("Invalid port number. Please provide a valid integer.");
            return;
        }

        try (DatagramSocket serverSocket = new DatagramSocket(port)) {
            System.out.println("Server is listening on port " + port);
            
            byte[] buffer = new byte[1024];

            while (true) {
                DatagramPacket requestPacket = new DatagramPacket(buffer, buffer.length);
                serverSocket.receive(requestPacket);

                String receivedData = new String(requestPacket.getData(), 0, requestPacket.getLength());
                System.out.println("Received request: " + receivedData);

                InetAddress clientAddress = requestPacket.getAddress();
                int clientPort = requestPacket.getPort();
                System.out.println("Client address: " + clientAddress + ", Client port: " + clientPort);
                

            }
        }
        catch (Exception e){
            
        }
    }

    static class ClientHandler extends Thread {
        private String fileName;
        private String filePath;
        private InetAddress clientAddress;
        private int clientHandlerPort;

        public ClientHandler(String fileName, String filePath, InetAddress clientAddress, int clientHandlerPort) {
            this.fileName = fileName;
            this.filePath = filePath;
            this.clientAddress = clientAddress;
            this.clientHandlerPort = clientHandlerPort;
            System.out.println("ClientHandler created for file: " + fileName + " on port: " + clientHandlerPort);
        }

        @Override
        public void run() {
            try (DatagramSocket socket = new DatagramSocket(clientHandlerPort)) {
                System.out.println("ClientHandler started for file: " + fileName + " on port: " + clientHandlerPort);
                byte[] buffer = new byte[1024];
                
                while (true) {
                    DatagramPacket filePacket = new DatagramPacket(buffer, buffer.length);
                    socket.receive(filePacket);
                    System.out.println("File data received from client: " + fileName);
                }
            } catch (Exception e) {
                System.out.println("Error sending file data: " + e.getMessage());
            }
        }
    }
}