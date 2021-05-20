package Client;

import Tokenizer.Token;
import Tokenizer.*;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Scanner;

public class Client {

    public static void main(String[] args) throws  IOException{

        if (args.length != 2) {
            System.out.println("Incorrect args formatting");
            return;
        }

        /**Sets up variables needed for inputting/outputting
         * socket - Socket which connects to host (Assumed my laptop) on port
         *          specified in args
         * s - Scanner to read input user types in cmd
         * out - Output stream to server
         * in - Input stream from server
         */
        Socket socket = new Socket(InetAddress.getLocalHost(), Integer.parseInt(args[0]));
        Scanner s = new Scanner(System.in);
        PrintWriter out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream()));
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));


/*
        //Thread which continuously reads from the socket input stream
        new Thread(() -> {
            try {
                String line;
                //Need this "!= null" because when connection is closed,
                //readLine will infinitely read null from the socket.
                //So when line == null, we know the connection is closed
                while (true) {
                    line = in.readLine();
                    System.out.println("RECEIVED  :  " + line);
                    Token t = Tokenizer.getToken(line);

                    if (t instanceof StoreToToken) {
                        StoreToToken token = (StoreToToken) t;
                        for (int port : token.ports) {

                        }
                    }


                }
            } catch (Exception e) {
                System.out.println("READING FAILURE");
            }
        }).start();
        */


        //While loop reads from command line and sends it to server
        //If END is type client side, program ends
        //Should type EXIT first, to exit the server, then END to terminate client side
        String input = s.nextLine();
        while (!input.equals("END")) {
            out.println(input);
            out.flush();
            System.out.println("SENDING : " + input);

            Token outputtedT = Tokenizer.getToken(input);

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
                    if (Tokenizer.getToken(ack) instanceof AckToken) {
                        StoreToken sentToken = (StoreToken) outputtedT;

                        System.out.println("WHILE CHECK");
                        //Gets file content
                        File f = new File(sentToken.filename);
                        Scanner reader = new Scanner(f);
                        String fileContent = "";
                        while (reader.hasNextLine()) {
                            fileContent += reader.nextLine();
                        }
                        System.out.println("FILE : " + fileContent);
                        outData.write(fileContent.getBytes());
                        outData.flush();
                    } else {
                        System.out.println("INCORRECT ACK RECEIVED AT CLIENT FROM DSTORE");
                    }
                }
                line = in.readLine();
                System.out.println(line);
            }

            input = s.nextLine();
        }
        out.close();
        in.close();
        socket.close();
    }
}
