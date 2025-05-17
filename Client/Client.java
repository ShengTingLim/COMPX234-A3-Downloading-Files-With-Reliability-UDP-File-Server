import java.io.BufferedReader;
import java.io.FileReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
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

        List<String> fileNames = new ArrayList<>();
        try (BufferedReader fileReader = new BufferedReader(new FileReader(fileList))) {
            String line;
            while ((line = fileReader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    fileNames.add(line);
                }
            }   
        } catch (Exception e) {
            System.out.println("Error reading file: " + e.getMessage());
        }

        try(DatagramSocket clientSocket = new DatagramSocket()){
            for (String fileName : fileNames) {
                System.out.println("Sending file name: " + fileName);
                String requestMessage = "DOWNLOAD " + fileName;

                byte[] sendData = requestMessage.getBytes();
                InetAddress serverAddress = InetAddress.getByName(hostname);
                DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, serverAddress, port);

                clientSocket.send(sendPacket);
                System.out.println("DOWNLOAD " + fileName + " sent to server");

                byte[] receiveBuffer = new byte[1024];
                DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                clientSocket.receive(receivePacket);

                String response = new String(receivePacket.getData(), 0, receivePacket.getLength());
                System.out.println("Received response: " + response);

                String[] responseParts = response.split(" ");
                if (responseParts[0].equals("OK") && responseParts.length == 6) {
                    String fileNameResponse = responseParts[1];
                    long fileSize = Long.parseLong(responseParts[3]);
                    int clientHandlerPort = Integer.parseInt(responseParts[5]);

                    System.out.println("File name: " + fileNameResponse);
                    System.out.println("File size: " + fileSize);
                    System.out.println("Client handler port: " + clientHandlerPort);
                    
                    long bytesReceived = 0;
                    long endOffset = Math.min(bytesReceived + 999, fileSize - 1);
                    String fileGet = "FILE " + fileNameResponse + " GET START " + bytesReceived + " END " + endOffset;
                    byte[] fileGetData = fileGet.getBytes();
                    DatagramPacket fileGetPacket = new DatagramPacket(fileGetData, fileGetData.length, serverAddress, clientHandlerPort);
                    clientSocket.send(fileGetPacket);
                    System.out.println("FILE " + fileNameResponse + " GET START " + bytesReceived + " END " + endOffset + " sent to server");
                    
                    
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
