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

public class Client {
    private static final int TIMEOUT = 1000;
    private static final int MAX_RETRIES = 5;

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java Client <hostname> <port> <filename>");
            return;
        }

        String hostname = args[0];
        int serverPort;
        String fileList = args[2];

        try {
            serverPort = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            System.out.println("Invalid port number. Please provide a valid integer.");
            return;
        }

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

        try(DatagramSocket clientSocket = new DatagramSocket()){
            InetAddress serverAddress = InetAddress.getByName(hostname);

            for (String fileName : filesToDownload) {
                System.out.println("Sending file name: " + fileName);
                String requestMessage = "DOWNLOAD " + fileName;
                String response = null;
                int currentTimeout = TIMEOUT;
                for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
                    response = sendReceiveRequest(clientSocket, requestMessage, serverAddress, serverPort, currentTimeout);
                    if (response != null) {
                        break;
                    } 
                    System.out.println("Retrying DOWNLOAD Attempt for " + fileName + " Attempt " + (attempt + 1) + "/" + MAX_RETRIES);
                    currentTimeout *= 2;
                }

                if (response == null) {
                    System.out.println("Failed to receive response after " + MAX_RETRIES + " attempts");
                    continue;
                }

                String[] responseParts = response.split(" ");
                if (responseParts[0].equals("OK") && responseParts.length == 6) {
                    long fileSize = Long.parseLong(responseParts[3]);
                    int clientHandlerPort = Integer.parseInt(responseParts[5]);

                    System.out.println("File name: " + fileName);
                    System.out.println("File size: " + fileSize);
                    System.out.println("Client handler port: " + clientHandlerPort);
                    
                    long bytesReceived = 0;
                    try (FileOutputStream fileWriter = new FileOutputStream(fileName)) {
                        while (bytesReceived < fileSize) {
                            long endBytes = Math.min(bytesReceived + 999, fileSize - 1);
                            System.out.println("Requesting bytes from " + bytesReceived + " to " + endBytes);
                            
                            String fileGetMessage = "FILE " + fileName + " GET START " + bytesReceived + " END " + endBytes;
                            String fileDataResponse = null;
                            currentTimeout = TIMEOUT;
                            for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
                                fileDataResponse = sendReceiveRequest(clientSocket, fileGetMessage, serverAddress, clientHandlerPort, currentTimeout);
                                if (fileDataResponse != null) {
                                    break;
                                } 
                                System.out.println("Retrying FILE GET Attempt for " + fileName + " Attempt " + (attempt + 1) + "/" + MAX_RETRIES);
                                currentTimeout *= 2;
                            }

                            if (fileDataResponse == null) {
                                System.out.println("Failed to receive response after " + MAX_RETRIES + " attempts");
                                break;
                            }
                            
                            String[] fileDataResponseParts = fileDataResponse.split(" ", 9);
                            String base64DataString = null;
                            
                            if (fileDataResponseParts[0].equals("FILE") && fileDataResponseParts[1].equals(fileName) &&
                                    fileDataResponseParts[2].equals("OK") && fileDataResponseParts[3].equals("START") &&
                                    fileDataResponseParts[5].equals("END") && fileDataResponseParts[7].equals("DATA")) {
                                
                                long startByte = Long.parseLong(fileDataResponseParts[4]);
                                long endByte = Long.parseLong(fileDataResponseParts[6]);
                                bytesReceived += (endByte - startByte + 1);
                                base64DataString = fileDataResponseParts[8];
                            } else {
                                System.out.println("Invalid response from server");
                            }
                            
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
                    for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
                        closeResponse = sendReceiveRequest(clientSocket, closeMessage, serverAddress, clientHandlerPort, currentTimeout);
                        if (closeResponse != null) {
                            break;
                        } 
                        System.out.println("Retrying FILE CLOSE Attempt for " + fileName + " Attempt " + (attempt + 1) + "/" + MAX_RETRIES);
                        currentTimeout *= 2;
                    }

                    if (closeResponse == null) {
                        System.out.println("Failed to receive response after " + MAX_RETRIES + " attempts");
                        continue;
                    }

                    System.out.println("Received close response: " + closeResponse);
                    String[] closeResponseParts = closeResponse.split(" ");
                    
                    if (closeResponseParts[0].equals("FILE") && closeResponseParts[1].equals(fileName) && closeResponseParts[2].equals("CLOSE_OK")) {
                        System.out.println("FILE " + fileName + " CLOSED_OK");
                    } else {
                        System.out.println("Error: Unexpected response from server");
                    }
                } else if (responseParts[0].equals("ERR")) {
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

    private static String sendReceiveRequest(DatagramSocket socket, String message, InetAddress serverAddress, int serverPort, int timeout){
        try {
            byte[] sendData = message.getBytes();
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, serverPort);
            socket.send(sendPacket);

            byte[] receiveBuffer = new byte[2048];
            DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
            socket.setSoTimeout(timeout);
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
