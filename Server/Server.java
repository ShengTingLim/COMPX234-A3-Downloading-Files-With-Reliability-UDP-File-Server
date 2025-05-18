import java.io.File;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

public class Server {
    private static final Set<Integer> usedPorts = new HashSet<>();
    private static final int CLIENT_MIN_PORT = 50000;
    private static final int CLIENT_MAX_PORT = 51000;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java Server <port>");
            return;
        }

        int port;

        try {
            port = Integer.parseInt(args[0]);
            usedPorts.add(port);
        } catch (NumberFormatException e) {
            System.out.println("Invalid port number. Please provide a valid integer.");
            return;
        }

        try (DatagramSocket serverSocket = new DatagramSocket(port)) {
            System.out.println("Server is listening on port " + port);
            
            byte[] buffer = new byte[2048];

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
                    String fileName = requestParts[1].trim();
                    File file = new File(fileName);
                    String response;
                    
                    if (file.exists() && file.isFile()) {
                        long fileSize = file.length();
                        int clientHandlerPort = selectRandomPort();

                        response = "OK " + fileName + " SIZE " + fileSize + " PORT " + clientHandlerPort;
                        byte[] responseData = response.getBytes();
                        DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, clientAddress, clientPort);
                        serverSocket.send(responsePacket);

                        ClientHandler clientHandler = new ClientHandler(fileName, clientHandlerPort, clientAddress, clientPort);
                        clientHandler.start();

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
            System.out.println("Server error: " + e.getMessage());
        }
    }

    private static int selectRandomPort() {
        int randomPort = (int) (Math.random() * (CLIENT_MAX_PORT - CLIENT_MIN_PORT + 1)) + CLIENT_MIN_PORT;
        while (usedPorts.contains(randomPort)) {
            randomPort = (int) (Math.random() * (CLIENT_MAX_PORT - CLIENT_MIN_PORT + 1)) + CLIENT_MIN_PORT;
        }
        usedPorts.add(randomPort);
        System.out.println("Random port: " + randomPort);
        return randomPort;
    }

    private static void releasePort(int port) {
        usedPorts.remove(port);
    }

    static class ClientHandler extends Thread {
        private final String fileName;
        private final InetAddress clientAddress;
        private final int clientHandlerPort;
        private final int clientPort;

        public ClientHandler(String fileName, int clientHandlerPort, InetAddress clientAddress, int clientPort) {
            this.fileName = fileName;
            this.clientHandlerPort = clientHandlerPort;
            this.clientAddress = clientAddress;
            this.clientPort = clientPort;
        }

        @Override
        public void run() {
            try (DatagramSocket socket = new DatagramSocket(clientHandlerPort)) {
                System.out.println("ClientHandler started for file: " + fileName + " on port: " + clientHandlerPort);
                byte[] buffer = new byte[2048];
                
                while (true) {
                    DatagramPacket requestPacket = new DatagramPacket(buffer, buffer.length);
                    socket.receive(requestPacket);
                    System.out.println("File data received from client: " + fileName);
                    System.out.println("Client address: " + clientAddress + ", Client port: " + clientPort);
                    
                    String requestData = new String(requestPacket.getData(), 0, requestPacket.getLength());
                    String[] requestParts = requestData.split(" ");

                    if (requestParts.length == 7 && requestParts[0].equals("FILE") && 
                        requestParts[1].equals(fileName) && requestParts[2].equals("GET") && 
                        requestParts[3].equals("START") && requestParts[5].equals("END")) {

                        long startByte = Long.parseLong(requestParts[4]);
                        long endByte = Long.parseLong(requestParts[6]);

                        try (RandomAccessFile file = new RandomAccessFile(fileName, "r")) {
                            file.seek(startByte);
                            int bytesToRead = (int)(endByte - startByte + 1);
                            byte[] fileChunk = new byte[bytesToRead];
                            int bytesRead = file.read(fileChunk);
                            System.out.println("Read " + bytesRead + " bytes from file: " + fileName);
                            String base64String = Base64.getEncoder().encodeToString(fileChunk);

                            String responseData = "FILE " + fileName + " OK START " + startByte + " END " + endByte + " DATA " + base64String;
                            byte[] responseBytes = responseData.getBytes();
                            DatagramPacket responsePacket = new DatagramPacket(responseBytes, responseBytes.length, clientAddress, clientPort);
                            socket.send(responsePacket);
                        } catch (Exception e) {
                            System.out.println("Error reading file: " + e.getMessage());
                        }
                    }
                    else if (requestParts.length == 3 && requestParts[0].equals("FILE") && 
                        requestParts[1].equals(fileName) && requestParts[2].equals("CLOSE")){
                        break;
                    }
                }
                
                String closeResponse = "FILE " + fileName + " CLOSE_OK";
                byte[] closeResponseBytes = closeResponse.getBytes();
                DatagramPacket closeResponsePacket = new DatagramPacket(closeResponseBytes, closeResponseBytes.length, clientAddress, clientPort);
                socket.send(closeResponsePacket);
            } catch (Exception e) {
                System.out.println("Error sending file data: " + e.getMessage());
            } finally {
                System.out.println("ClientHandler for file: " + fileName + " on port: " + clientHandlerPort + " finished");
                releasePort(clientHandlerPort);
            }
        }
    }
}