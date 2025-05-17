import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;

public class Server {
    private static Set<Integer> usedPorts = new HashSet<>();

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

                String[] requestParts = receivedData.split(" ", 2);
                if (requestParts[0].equals("DOWNLOAD")) {
                    String fileName = requestParts[1];
                    File file = new File(fileName);
                    String response;
                    
                    if (file.exists() && file.isFile()) {
                        long fileSize = file.length();
                        
                        int clientHandlerPort = selectRandomPort();

                    } else {
                        response = "ERR " + fileName + " NOT_FOUND";
                        byte[] responseData = response.getBytes();
                        DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, clientAddress, clientPort);
                        serverSocket.send(responsePacket);
                    }
                }
            }
        }
        catch (Exception e){
            
        }
    }

    private static int selectRandomPort() {
        int minPort = 50000;
        int maxPort = 51000;
        int randomPort = (int) (Math.random() * (maxPort - minPort + 1)) + minPort;
        while (usedPorts.contains(randomPort)) {
            randomPort = (int) (Math.random() * (maxPort - minPort + 1)) + minPort;
        }
        usedPorts.add(randomPort);
        System.out.println("Random port: " + randomPort);
        return randomPort;
    }

    static class ClientHandler extends Thread {
        private String fileName;
        private InetAddress clientAddress;
        private int clientHandlerPort;

        public ClientHandler(String fileName, InetAddress clientAddress, int clientHandlerPort) {
            this.fileName = fileName;
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