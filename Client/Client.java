import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class Client {
    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Usage: java Client <hostname> <port> <filename>");
            return;
        }

        String hostname = args[0];
        int port;
        String fileList = args[2];

        try {
            port = Integer.parseInt(args[1]);
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
            System.out.println("Error reading file: " + e.getMessage());
        }

        try(DatagramSocket clientSocket = new DatagramSocket()){
            InetAddress serverAddress = InetAddress.getByName(hostname);

            for (String fileName : filesToDownload) {
                System.out.println("Sending file name: " + fileName);
                String requestMessage = "DOWNLOAD " + fileName;

                byte[] sendData = requestMessage.getBytes();
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, port);

                clientSocket.send(sendPacket);
                System.out.println("DOWNLOAD " + fileName + " sent to server");

                byte[] receiveBuffer = new byte[2024];
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                clientSocket.receive(receivePacket);

                String response = new String(receivePacket.getData(), 0, receivePacket.getLength());
                System.out.println("Received response: " + response);

                String[] responseParts = response.split(" ");
                if (responseParts[0].equals("OK") && responseParts.length == 6) {
                    long fileSize = Long.parseLong(responseParts[3]);
                    int clientHandlerPort = Integer.parseInt(responseParts[5]);

                    System.out.println("File name: " + fileName);
                    System.out.println("File size: " + fileSize);
                    System.out.println("Client handler port: " + clientHandlerPort);
                    
                    long bytesReceived = 0;
                    try (FileOutputStream fileWriter = new FileOutputStream(fileName, true)) {
                        while (bytesReceived < fileSize) {
                            long endBytes = Math.min(bytesReceived + 999, fileSize - 1);
                            System.out.println("Requesting bytes from " + bytesReceived + " to " + endBytes);
                            
                            String fileGet = "FILE " + fileName + " GET START " + bytesReceived + " END " + endBytes;
                            byte[] fileGetData = fileGet.getBytes();
                            DatagramPacket fileGetPacket = new DatagramPacket(fileGetData, fileGetData.length, serverAddress, clientHandlerPort);
                            
                            clientSocket.send(fileGetPacket);
                            System.out.println("FILE " + fileName + " GET START " + bytesReceived + " END " + endBytes + " sent to server");
                            
                            byte[] fileDataReceiveBuffer = new byte[2048];
                            DatagramPacket fileDataReceivePacket = new DatagramPacket(fileDataReceiveBuffer, fileDataReceiveBuffer.length);
                            
                            clientSocket.receive(fileDataReceivePacket);
                            String fileDataResponse = new String(fileDataReceivePacket.getData(), 0, fileDataReceivePacket.getLength());
                            System.out.println("Received file data: " + fileDataResponse);
                            
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
                                System.out.println("File data received: " + new String(fileData));
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
                    byte[] closeData = closeMessage.getBytes();
                    DatagramPacket closePacket = new DatagramPacket(closeData, closeData.length, serverAddress, clientHandlerPort);
                    clientSocket.send(closePacket);
                    System.out.println("FILE " + fileName + " CLOSE sent to server");
                    
                } else if (responseParts[0].equals("ERR")) {
                    System.out.println("Error: " + responseParts[1] + " " + responseParts[2]);
                } else {
                    System.out.println("Invalid response from server");
                }
            }
        }
        catch (Exception e){
            
        }
    }
}
