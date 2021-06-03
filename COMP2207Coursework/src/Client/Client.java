package Client;

import Tokenizer.Token;
import Tokenizer.*;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Scanner;

public class Client {

    public void run(String command, String portString) throws  IOException{

        /**Sets up variables needed for inputting/outputting
         * socket - Socket which connects to host (Assumed my laptop) on port
         *          specified in args
         * s - Scanner to read input user types in cmd
         * out - Output stream to server
         * in - Input stream from server
         */
        Socket socket = new Socket(InetAddress.getLocalHost(), Integer.parseInt(portString));
        Scanner s = new Scanner(System.in);
        PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));


        String input = command;
        while (!input.equals("END")) {
            out.println(input);
            out.flush();
            System.out.println("SENDING : " + input);

            StoreToken outputtedT = (StoreToken)Tokenizer.getToken(input);

            String line = in.readLine();
            System.out.println("RECEIVED  :  " + line);
            Token t = Tokenizer.getToken(line);

            if (t instanceof StoreToToken) {
                StoreToToken token = (StoreToToken) t;
                for (int port : token.ports) {
                    Socket dStoreSocket = new Socket(InetAddress.getLocalHost(), port);
                    PrintWriter outText = new PrintWriter(new BufferedOutputStream(dStoreSocket.getOutputStream()));
                    BufferedReader inText = new BufferedReader(new InputStreamReader(dStoreSocket.getInputStream()));
                    OutputStream outData = dStoreSocket.getOutputStream();

                    outText.println(input);
                    outText.flush();

                    String ack = inText.readLine();
                    System.out.println("RECEIVED : " + ack);
                    if (Tokenizer.getToken(ack) instanceof AckToken) {
                        StoreToken sentToken = outputtedT;

                        //Gets file content
                        assert sentToken != null;
                        File f = new File(sentToken.filename);
                        FileInputStream reader = new FileInputStream(f);
                        byte[] fileContent = reader.readNBytes(sentToken.filesize);

                        System.out.println("SENT FILE : " + fileContent + ", LENGTH : " + fileContent.length);
                     //   System.out.println("CONNECTION FROM " + socket.getLocalPort() + " TO " + socket.getPort());
                        outData.write(fileContent);
                        outData.flush();
                    } else {
                        System.out.println("INCORRECT ACK RECEIVED AT CLIENT FROM DSTORE");
                    }
                }
                line = in.readLine();
                System.out.println("RECEIVED : " + line);
            }
            input = s.nextLine();
        }
        out.close();
        in.close();
        socket.close();
    }
}
