import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

/**
 * Server class for handling file download requests from clients
 */
public class Server {
    private static final Set<Integer> usedPorts = new HashSet<>();
    private static final int CLIENT_MIN_PORT = 50000;
    private static final int CLIENT_MAX_PORT = 51000;

    /**
     * Starts the server on the specified port and listens for incoming requests.
     * Creates a new thread to handle client requests for file downloads.
     *
     * @param args Command line arguments: port
     */
    public static void main(String[] args) {
        // Check if the correct number of arguments is provided
        if (args.length != 1) {
            System.out.println("Usage: java Server <port>");
            return;
        }

        int port;

        // Parse the port number
        try {
            port = Integer.parseInt(args[0]);
            usedPorts.add(port);
        } catch (NumberFormatException e) {
            System.out.println("Invalid port number. Please provide a valid integer.");
            return;
        }

        // Start the server on a new datagram socket with the specified port
        try (DatagramSocket serverSocket = new DatagramSocket(port)) {
            System.out.println("Server is listening on port " + port);
            
            byte[] buffer = new byte[2048];

            // Continuously listen for incoming requests
            while (true) {
                // Create a DatagramPacket to receive requests
                DatagramPacket requestPacket = new DatagramPacket(buffer, buffer.length);
                serverSocket.receive(requestPacket);

                // Process the received request
                String receivedData = new String(requestPacket.getData(), 0, requestPacket.getLength());
                System.out.println("Received request: " + receivedData);

                // Get the client address and port from the request packet
                InetAddress clientAddress = requestPacket.getAddress();
                int clientPort = requestPacket.getPort();
                System.out.println("Client address: " + clientAddress + ", Client port: " + clientPort);

                // Split the received data and check if it is a DOWNLOAD request
                String[] requestParts = receivedData.split(" ", 2);
                if (requestParts[0].equals("DOWNLOAD")) {
                    // Get the file name
                    String fileName = requestParts[1].trim();
                    File file = new File(fileName);
                    String response;
                    
                    // Check if the file exists
                    if (file.exists() && file.isFile()) {
                        // Get the file size and generate a randonm port for the client handler
                        long fileSize = file.length();
                        int clientHandlerPort = selectRandomPort();

                        // Send a response back to the client
                        response = "OK " + fileName + " SIZE " + fileSize + " PORT " + clientHandlerPort;
                        byte[] responseData = response.getBytes();
                        DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, clientAddress, clientPort);
                        serverSocket.send(responsePacket);

                        // Start a new thread to handle the client request
                        ClientHandler clientHandler = new ClientHandler(fileName, clientHandlerPort, clientAddress, clientPort);
                        clientHandler.start();

                    } else {
                        // If the file does not exist, send an error response
                        response = "ERR " + fileName + " NOT_FOUND";
                        byte[] responseData = response.getBytes();
                        DatagramPacket responsePacket = new DatagramPacket(responseData, responseData.length, clientAddress, clientPort);
                        serverSocket.send(responsePacket);
                    }
                }
            }
        }
        catch (IOException e) {
            System.out.println("IO Exception in server: " + e.getMessage());
        }
        catch (Exception e) {
            System.out.println("Unexpected server error: " + e.getMessage());
        }
    }

    /**
     * Selects a random port between CLIENT_MIN_PORT and CLIENT_MAX_PORT that is not in use
     * If all ports have been used, returns -1.
     * @return A random unused port number or -1 if all ports have been used
     */
    private static int selectRandomPort() {
        if (usedPorts.size() >= (CLIENT_MAX_PORT - CLIENT_MIN_PORT + 1)) {
            System.out.println("No available ports left");
            return -1;
        }
        int randomPort = (int) (Math.random() * (CLIENT_MAX_PORT - CLIENT_MIN_PORT + 1)) + CLIENT_MIN_PORT;
        while (usedPorts.contains(randomPort)) {
            randomPort = (int) (Math.random() * (CLIENT_MAX_PORT - CLIENT_MIN_PORT + 1)) + CLIENT_MIN_PORT;
        }
        usedPorts.add(randomPort);
        System.out.println("Random port: " + randomPort);
        return randomPort;
    }


    /**
     * Releases a port that was previously used by selectRandomPort
     * @param port The port number to release
     */
    private static void releasePort(int port) {
        synchronized (usedPorts) {
            usedPorts.remove(port);
        }
    }

    /**
     * ClientHandler class to handle a single file download request from a client
     * Runs in a new thread and listens for requests on a specific port
     */
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

        
        /**
         * Handles a single file download request from a client
         * Listens for requests on a specific port and sends the requested file data
         * If the client requests a file chunk, reads the chunk from the file and sends it back encoded in Base64
         * If the client requests to close the connection, sends a confirmation response and releases the port
         */
        @Override
        public void run() {
            // Start a new DatagramSocket on the client handler port
            try (DatagramSocket socket = new DatagramSocket(clientHandlerPort)) {
                System.out.println("ClientHandler started for file: " + fileName + " on port: " + clientHandlerPort + "\nFor Client: " + clientAddress + ":" + clientPort);
                byte[] buffer = new byte[2048];
                
                // Continuously listen for requests from the client
                while (true) {
                    // Create a DatagramPacket and receive a request
                    DatagramPacket requestPacket = new DatagramPacket(buffer, buffer.length);
                    socket.receive(requestPacket);
                    
                    // Process the received request
                    String requestData = new String(requestPacket.getData(), 0, requestPacket.getLength());
                    String[] requestParts = requestData.split(" ");
                    System.out.println("ClientHandler (" + this.fileName + ") received from " + this.clientAddress.getHostAddress() + ":" + this.clientPort + " -> \"" + requestData + "\"");

                    // Check if the request fits the expected format for file chunk requests
                    if (requestParts.length == 7 && requestParts[0].equals("FILE") && 
                        requestParts[1].equals(fileName) && requestParts[2].equals("GET") && 
                        requestParts[3].equals("START") && requestParts[5].equals("END")) {
                        
                        long startByte;
                        long endByte;

                        // Parse the start and end byte positions from the request
                        try {
                            startByte = Long.parseLong(requestParts[4]);
                            endByte = Long.parseLong(requestParts[6]);

                            // Read the requested chunk from the file
                            try (RandomAccessFile file = new RandomAccessFile(fileName, "r")) {
                                file.seek(startByte);
                                int bytesToRead = (int)(endByte - startByte + 1);
                                byte[] fileChunk = new byte[bytesToRead];
                                int bytesRead = file.read(fileChunk);
                                System.out.println("Read " + bytesRead + " bytes from file: " + fileName);

                                // Encode the chunk of data in Base64
                                String base64String = Base64.getEncoder().encodeToString(fileChunk);

                                // Send the chunk of data back to the client
                                String responseData = "FILE " + fileName + " OK START " + startByte + " END " + endByte + " DATA " + base64String;
                                byte[] responseBytes = responseData.getBytes();
                                DatagramPacket responsePacket = new DatagramPacket(responseBytes, responseBytes.length, clientAddress, clientPort);
                                socket.send(responsePacket);
                            } catch (Exception e) {
                                System.out.println("Error reading file: " + e.getMessage());
                            }
                        } catch (NumberFormatException e) {
                            System.out.println("Error parsing start and end bytes: " + e.getMessage());
                        }                        
                    }
                    // Check if the request is to close the file connection
                    else if (requestParts.length == 3 && requestParts[0].equals("FILE") && 
                        requestParts[1].equals(fileName) && requestParts[2].equals("CLOSE")){
                        break;
                    }
                }
                // If the request is to close the file connection send a confirmation response
                String closeResponse = "FILE " + fileName + " CLOSE_OK";
                byte[] closeResponseBytes = closeResponse.getBytes();
                DatagramPacket closeResponsePacket = new DatagramPacket(closeResponseBytes, closeResponseBytes.length, clientAddress, clientPort);
                socket.send(closeResponsePacket);
            } catch (Exception e) {
                System.out.println("Error sending file data: " + e.getMessage());
            } finally {
                System.out.println("ClientHandler for file: " + fileName + " on port: " + clientHandlerPort + " finished");
                // Release the port after finishing the client handler
                releasePort(clientHandlerPort);
            }
        }
    }
}