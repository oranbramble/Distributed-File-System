package Controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import Loggers.ControllerLogger;
import Loggers.Logger;
import Tokenizer.*;


public class Controller {

    private int cPort;
    private int R;
    private int timeout;
    private int rebalancePeriod;
    private int expectedAcks;

    private ArrayList<Integer> dStores;
    private ArrayList<String> files;

    public Controller(int cPort, int R, int timeout, int rebalancePeriod) throws IOException {
        this.cPort = cPort;
        this.R = R;
        this.timeout = timeout;
        this.rebalancePeriod = rebalancePeriod;
        this.expectedAcks = 0;

        this.dStores = new ArrayList<>();
        this.files = new ArrayList<>();

        ControllerLogger.init(Logger.LoggingType.ON_TERMINAL_ONLY);
    }

    public void startListening() throws IOException {
        ServerSocket listener = new ServerSocket(this.cPort);
        //First R connections are DStores
        while (true) {
            //How to differentiate between client and DStore?
            Socket connection = listener.accept();
            this.startConnection(connection);
        }
    }

    private void startConnection(Socket s) throws IOException{
        //Sets up input stream, waits for first message to be send
        //When message receiver, logs the message and tokenizes it
        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
        String line = in.readLine();
        ControllerLogger.getInstance().messageReceived(s, line);
        Token token = Tokenizer.getToken(line);

        //We tokenize the first command received. IF it is a JOIN command, we know the connection is to
        //a DStore, if not then it must be a client
        if (token instanceof JoinToken) {
            //If Dstore joining, log the join and save the port
            int DstorePort = ((JoinToken) token).port;
            ControllerLogger.getInstance().dstoreJoined(s, DstorePort);
            this.dStores.add(DstorePort);

        } else if (token instanceof StoreAckToken) {
            this.expectedAcks -= 1;

        } else if (token != null) {
            //If its a client request, create a new connection thread to handle that
            //request and further requests from that client
            new ControllerClientConnection(s, token, this).start();
        }
    }

    public String getFileList() {
        return "File1 File2 File3";
    }


    public String getPortsForStore(String filename) {
        try {
            StringBuilder portsToSend = new StringBuilder();
            for (int x = 0; x < this.R; x++) {
                int port = this.dStores.get(x);
                portsToSend.append(" ").append(port);
            }
            return portsToSend.toString();

        } catch (NullPointerException e) {
            System.out.println("### STORE ERROR ###");
            return "";
        }
    }

    public void storeAckChecker(String filename) {
        while (this.expectedAcks != 0) {
            System.out.println(this.expectedAcks);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        this.files.add(filename);
    }

    public void setExpectedAcks(int acksExpected) {
        this.expectedAcks = acksExpected;
    }

    /** Args layout:
     *  args[0] = cport           -> port for controller to listen on
     *  args[1] = R               -> Replication factor for files
     *  args[2] = timeout         -> How long connection held with client/DStore
     *  args[3] = rebalancePeriod -> How long between rebalance operations
     */
    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Incorrect args structure");
            return;
        }
        Integer[] i = Stream.of(args).map(Integer::valueOf).toArray(Integer[]::new);
        try {
            new Controller(i[0], i[1], i[2], i[3]).startListening();
        } catch (IOException e) {
            System.out.println("### ERROR ###  " + e);
        }
    }
}
