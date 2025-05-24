import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Client class to download files from a server using UDP
 * Reads a list of file names from a specified file, sends download requests,
 * and handles the responses to retrieve the files.
 */
public class Client {
    private static final int TIMEOUT = 200;
    private static final int MAX_RETRIES = 5;

    /**
     * Main method to run the client.
     * Sends download requests for files listed in a specified file to the server
     * 
     * @param args Command line arguments: hostname, port, and file list filename.
     */
    public static void main(String[] args) {
        // Check if the correct number of arguments is provided
        if (args.length != 3) {
            System.out.println("Usage: java Client <hostname> <port> <filename>");
            return;
        }

        // Parse command line arguments
        String hostname = args[0];
        int serverPort;
        String fileList = args[2];

        // Parse the server port number
        try {
            serverPort = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.out.println("Invalid port number. Please provide a valid integer.");
            return;
        }

        // Read the list of files to download from the specified file
        List<String> filesToDownload = new ArrayList<>();
        try (BufferedReader fileReader = new BufferedReader(new FileReader(fileList))) {
            String line;
            while ((line = fileReader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    filesToDownload.add(line);
                }
            }   
        } catch (Exception e) {
            System.out.println("Error reading files list: " + e.getMessage());
        }

        // Create a DatagramSocket to communicate with the server
        try(DatagramSocket clientSocket = new DatagramSocket()){
            InetAddress serverAddress = InetAddress.getByName(hostname);

            // Loop through each file name and send a download request sequentially
            for (String fileName : filesToDownload) {
                System.out.println("Sending file name: " + fileName);
                String requestMessage = "DOWNLOAD " + fileName;
                String response = null;
                int currentTimeout = TIMEOUT;

                // Retry sending the request up to MAX_RETRIES times if no response is received
                for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
                    response = sendReceiveRequest(clientSocket, requestMessage, serverAddress, serverPort, currentTimeout);
                    if (response != null) {
                        break;
                    } 
                    System.out.println("Retrying DOWNLOAD Attempt for " + fileName + " Timeout:" + currentTimeout + "ms Attempt " + (attempt + 1) + "/" + MAX_RETRIES);
                    currentTimeout *= 2;
                }

                // If no response is received, continue to the next file
                if (response == null) {
                    System.out.println("Failed to receive response after " + MAX_RETRIES + " attempts");
                    continue;
                }

                // Split the response into parts to check
                String[] responseParts = response.split(" ");
                // Check if the server responds with OK and the expected number of parts
                if (responseParts[0].equals("OK") && responseParts.length == 6) {
                    // Parse the file size and client handler port from the response
                    long fileSize = Long.parseLong(responseParts[3]);
                    int clientHandlerPort = Integer.parseInt(responseParts[5]);

                    System.out.println("File name: " + fileName);
                    System.out.println("File size: " + fileSize);
                    System.out.println("Client handler port: " + clientHandlerPort);
                    
                    long bytesReceived = 0;
                    // Open a FileOutputStream to write the received data
                    try (FileOutputStream fileWriter = new FileOutputStream(fileName)) {
                        // Download the file in chunks of 1000 bytes
                        while (bytesReceived < fileSize) {
                            long endBytes = Math.min(bytesReceived + 999, fileSize - 1);
                            System.out.println("Requesting bytes from " + bytesReceived + " to " + endBytes);

                            // Prepare the request message for the file data
                            String fileGetMessage = "FILE " + fileName + " GET START " + bytesReceived + " END " + endBytes;
                            String fileDataResponse = null;
                            currentTimeout = TIMEOUT;

                            // Retry sending the file data request up to MAX_RETRIES times
                            for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
                                fileDataResponse = sendReceiveRequest(clientSocket, fileGetMessage, serverAddress, clientHandlerPort, currentTimeout);
                                if (fileDataResponse != null) {
                                    break;
                                } 
                                System.out.println("Retrying FILE GET Attempt for " + fileName + " Timeout:" + currentTimeout + "ms Attempt " + (attempt + 1) + "/" + MAX_RETRIES);
                                currentTimeout *= 2;
                            }

                            // If no response is received stop downloading the file
                            if (fileDataResponse == null) {
                                System.out.println("Failed to receive response after " + MAX_RETRIES + " attempts");
                                break;
                            }
                            
                            // Split the file data response into parts to check
                            String[] fileDataResponseParts = fileDataResponse.split(" ", 9);
                            String base64DataString = null;
                            
                            // Check if the response is valid and contains the expected parts
                            if (fileDataResponseParts[0].equals("FILE") && fileDataResponseParts[1].equals(fileName) &&
                                    fileDataResponseParts[2].equals("OK") && fileDataResponseParts[3].equals("START") &&
                                    fileDataResponseParts[5].equals("END") && fileDataResponseParts[7].equals("DATA")) {
                                
                                // Parse the start and end byte positions and the base64 encoded data
                                long startByte = Long.parseLong(fileDataResponseParts[4]);
                                long endByte = Long.parseLong(fileDataResponseParts[6]);
                                bytesReceived += (endByte - startByte + 1);
                                base64DataString = fileDataResponseParts[8];
                            } else {
                                System.out.println("Invalid response from server");
                            }
                            
                            // Decode the base64 data string and write it to the file
                            if (base64DataString != null) {
                                byte[] fileData = Base64.getDecoder().decode(base64DataString);
                                System.out.println("File data received: " + fileData.length + " bytes");

                                fileWriter.write(fileData);
                                System.out.println("Written " + fileData.length + " bytes to file: " + fileName);
                                System.out.println("Total bytes received: " + bytesReceived);
                            } else {
                                System.out.println("No file data received");
                            }
                        }
                    }
                    System.out.println("File " + fileName + " downloaded successfully");
                    String closeMessage = "FILE " + fileName + " CLOSE";

                    String closeResponse = null;
                    currentTimeout = TIMEOUT;
                    // Send a request to close the file after downloading up to MAX_RETRIES times
                    for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
                        closeResponse = sendReceiveRequest(clientSocket, closeMessage, serverAddress, clientHandlerPort, currentTimeout);
                        if (closeResponse != null) {
                            break;
                        } 
                        System.out.println("Retrying FILE CLOSE Attempt for " + fileName + " Timeout:" + currentTimeout + "ms Attempt " + (attempt + 1) + "/" + MAX_RETRIES);
                        currentTimeout *= 2;
                    }

                    if (closeResponse == null) {
                        System.out.println("Failed to receive response after " + MAX_RETRIES + " attempts");
                        continue;
                    }

                    System.out.println("Received close response: " + closeResponse);
                    String[] closeResponseParts = closeResponse.split(" ");
                    
                    // Check if the close response was successful
                    if (closeResponseParts[0].equals("FILE") && closeResponseParts[1].equals(fileName) && closeResponseParts[2].equals("CLOSE_OK")) {
                        System.out.println("FILE " + fileName + " CLOSED_OK");
                    } else {
                        System.out.println("Error: Unexpected response from server");
                    }
                } else if (responseParts[0].equals("ERR")) {
                    // Print an error message if the server responds with an error
                    System.out.println("Error: " + responseParts[1] + " " + responseParts[2]);
                } else {
                    System.out.println("Invalid response from server");
                }
            }
        }
        catch (Exception e){
            System.out.println("Error in client: " + e.getMessage());
        }
    }

    /**
     * Sends a request to the server and waits for a response
     * 
     * @param socket The DatagramSocket to use for communication
     * @param message The request message to send
     * @param serverAddress The server's address to send to
     * @param serverPort The server's port to send to
     * @param timeout Timeout in milliseconds 
     * @return The response from the server or null if timed out or error
     */
    private static String sendReceiveRequest(DatagramSocket socket, String message, InetAddress serverAddress, int serverPort, int timeout){
        try {
            // Prepare the request message as a byte array and send it to the server
            byte[] sendData = message.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
            socket.send(sendPacket);

            // Prepare to receive the response from the server
            byte[] receiveBuffer = new byte[2048];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket.setSoTimeout(timeout);

            // Receive the response from the server
            socket.receive(receivePacket);
            String response = new String(receivePacket.getData(), 0, receivePacket.getLength());
            return response;
        } catch (SocketTimeoutException e) {
            System.out.println("Timeout occured for request: " + message);
            return null;
        } catch (IOException e) {
            System.out.println("Error sending/receiving request: " + e.getMessage());
            return null;
        }
    }
}
