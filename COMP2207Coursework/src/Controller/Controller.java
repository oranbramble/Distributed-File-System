package Controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.stream.Stream;

import IndexManager.*;
import Loggers.ControllerLogger;
import Loggers.Logger;
import Tokenizer.*;


public class Controller {

    private int cPort;
    private int R;
    private int timeout;
    private int rebalancePeriod;

    private IndexManager fileIndex;

    private Map<Integer, ControllerToDStoreConnection> dStoreConnectionMap;

    private ArrayList<ControllerToDStoreConnection> dStores;
    private ArrayList<String> files;

    public Controller(int cPort, int R, int timeout, int rebalancePeriod) throws IOException {
        this.cPort = cPort;
        this.R = R;
        this.timeout = timeout;
        this.rebalancePeriod = rebalancePeriod;
        this.dStoreConnectionMap = Collections.synchronizedMap(new HashMap<>());
        this.fileIndex = new IndexManager();

        this.dStores = new ArrayList<>();
        this.files = new ArrayList<>();

        ControllerLogger.init(Logger.LoggingType.ON_TERMINAL_ONLY);
    }

    public void startListening() throws IOException {
        ServerSocket listener = new ServerSocket(this.cPort);
        //Listens for any connection attempted by Dstopre or Client
        while (true) {
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

        //We tokenize the first command received. Then run checks on it to see what type of request it is.
        //If its a JOIN, we know the connection is a Dstore trying to join for the first time
        //If its none of these, it must be a client request.
        if (token instanceof JoinToken) {
            //If Dstore joining, log the join and save the port
            int dstorePort = ((JoinToken) token).port;
            ControllerLogger.getInstance().dstoreJoined(s, dstorePort);
            ControllerToDStoreConnection dstoreConnection = new ControllerToDStoreConnection(s, dstorePort, this);
            this.dStores.add(dstoreConnection);
            this.dStoreConnectionMap.put(dstorePort, dstoreConnection);


        } else if (token != null) {
            //If its a client request, create a new connection thread to handle that
            //request and further requests from that client
            new ControllerClientConnection(s, token, this).start();
        }
    }

    public String getFileList() {
        return this.files.toString();
    }

    public boolean checkEnoughDstores() {
        return this.dStores.size() >= this.R;
    }

    public void removeDstore(ControllerToDStoreConnection connection) {
        this.dStores.remove(connection);
        this.fileIndex.removeDstore(connection.getDstorePort());
    }

    /**
     * Method which gets R Dstore ports to send to the client, so the client can store the file on those Dstores
     * @param filename
     * @return ports: returns string of port numbers, where each port number is separated by spaces
     */
    public String getPortsForStore(String filename) {
        try {
            StringBuilder portsToSend = new StringBuilder();
            for (int x = 0; x < this.R; x++) {
                int port = this.dStores.get(x).getDstorePort();
                portsToSend.append(" ").append(port);
            }
            return portsToSend.toString();

        } catch (NullPointerException e) {
            //If exception thrown, then there is not enough Dstores to store on, so we return null
            return null;
        }
    }

    public boolean updateIndex(String filename, File.State s) {
        if (s == File.State.STORE_IN_PROGRESS) {
            return this.fileIndex.addFileToIndex(filename);
        }
        return false;
    }

    public boolean addExpectedAcks(ArrayList<Integer> ports, String filename) {
        try {
            this.fileIndex.addExpectedAcksForFile(filename, ports);
            return true;
        } catch (NumberFormatException e) {
            System.out.println("### ERROR ###   Cannot parse ports as integers : " + ports.toString());
            return false;
        }
    }

    public boolean ifStoreAcksReceived(String filename,ArrayList<Integer> ports) {
        if (this.fileIndex.listenForAcks(filename, this.timeout, ports)) {
            this.fileIndex.changeState(filename, File.State.AVAILABLE);
            return true;
        } else {
            this.fileIndex.removeFileFromIndex(filename);
            return false;
        }
    }

    public void storeAckReceived(StoreAckToken t, Integer port) {
        if (!this.fileIndex.storeAckReceived(t, port)) {
            System.out.println("### ERROR ###   Invalid STORE_ACK received from port " + port);
        }
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
