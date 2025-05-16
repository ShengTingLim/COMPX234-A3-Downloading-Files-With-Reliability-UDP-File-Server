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
            }
        }
        catch (Exception e){
            
        }
    }
}
