package Client;

import Loggers.Protocol;
import Tokenizer.Token;
import Tokenizer.*;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Arrays;
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


        Token t = Tokenizer.getToken(command);
        out.println(command);
        out.flush();
        System.out.println("SENDING : " + command);

        if (t instanceof StoreToken) {
            this.store(command, t, in);
        } else if (t instanceof ListToken) {
            this.list(in);
        } else if (t instanceof  RemoveToken) {
            this.remove(in);
        } else if (t instanceof LoadToken) {
            this.load(t,in,out);
        }

        out.close();
        in.close();
        socket.close();
    }




    public void list(BufferedReader in) throws IOException {
        System.out.println(in.readLine());
    }


    private void remove(BufferedReader in) throws IOException {
        String t = in.readLine();
        System.out.println("RECEIVED : " + t);
    }

    private void load(Token t, BufferedReader in, PrintWriter out) {
        String command = "";
        String filename = ((LoadToken)t).filename;
        try {
            command = in.readLine();
            System.out.println("RECEIVED : " + command);
        } catch (IOException e) {
            System.out.println("### ERROR ###   Cannot read from controller");
            return;
        }
        Token cToken = Tokenizer.getToken(command);
        if (cToken instanceof LoadFromToken) {
            int filesize = ((LoadFromToken)cToken).filesize;
            int port = ((LoadFromToken)cToken).port;
            try {
                Socket dStoreSocket = new Socket(InetAddress.getLocalHost(), port);
                PrintWriter outText = new PrintWriter(new BufferedOutputStream(dStoreSocket.getOutputStream()));
                InputStream inData = dStoreSocket.getInputStream();
                outText.println(Protocol.LOAD_DATA_TOKEN + " " + filename);
                outText.flush();
                System.out.println("SENDING : " + Protocol.LOAD_DATA_TOKEN + " " + filename);
                byte[] data = inData.readNBytes(filesize);
                System.out.println("RECEIVED : " + Arrays.toString(data));
                if (data.length == filesize) {
                    File f = new File(filename);
                    FileOutputStream w = new FileOutputStream(f);
                    w.write(data);
                    w.close();
                    System.out.println("LOAD OF " + filename + " COMPLETE");
                } else {
                    out.println(Protocol.RELOAD_TOKEN + " " + filename);
                    out.flush();
                    System.out.println("SENDING : " + Protocol.RELOAD_TOKEN + " " + filename);
                    this.load(t,in,out);
               }
            } catch (IOException e) {
                System.out.println("ERRRRR : " + e);
                out.println(Protocol.RELOAD_TOKEN + " " + filename);
                out.flush();
                System.out.println("SENDING : " + Protocol.RELOAD_TOKEN + " " + filename);
                this.load(t,in,out);
            }
        } else {
            System.out.println("LOAD OF " + filename + " FAILED");
        }
    }

    private void store(String input, Token token1, BufferedReader in) throws IOException {
        StoreToken outputtedT = (StoreToken)token1;

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
                System.out.println("SENDING : " + input);
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
                    System.out.println("FILESIZE : " + sentToken.filesize);
                    byte[] fileContent = reader.readNBytes(sentToken.filesize);

                    System.out.println("SENT FILE : " + fileContent + ", LENGTH : " + fileContent.length);
                    //   System.out.println("CONNECTION FROM " + socket.getLocalPort() + " TO " + socket.getPort());
                    outData.write(fileContent);
                    outData.flush();
                    reader.close();
                } else {
                    System.out.println("INCORRECT ACK RECEIVED AT CLIENT FROM DSTORE");
                }
            }
            line = in.readLine();
            System.out.println("RECEIVED : " + line);
        }
    }
}
