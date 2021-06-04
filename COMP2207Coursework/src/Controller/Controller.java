package Controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import IndexManager.*;
import Loggers.ControllerLogger;
import Loggers.Logger;
import Loggers.Protocol;
import Tokenizer.*;


public class Controller {

    private int cPort;
    private int R;
    private int timeout;
    private int rebalancePeriod;

    private IndexManager fileIndex;
    private Map<Integer, ControllerToDStoreConnection> dStoreConnectionMap;
    private ArrayList<ControllerToDStoreConnection> dStores;

    public Controller(int cPort, int R, int timeout, int rebalancePeriod) throws IOException {
        this.cPort = cPort;
        this.R = R;
        this.timeout = timeout;
        this.rebalancePeriod = rebalancePeriod;
        this.dStoreConnectionMap = Collections.synchronizedMap(new HashMap<>());
        this.fileIndex = new IndexManager();

        this.dStores = new ArrayList<>();

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

    /**
     * Method which gets all files currently stored on the system
     * Only includes files which are fully stored (not in the process of being stored)
     * @return
     */
    public String getFileList() {
        StringBuilder s = new StringBuilder();
        for (String file : this.fileIndex.getStoredFiles()) {
            s.append(" ").append(file);
        }
        return s.toString();
    }

    public boolean checkEnoughDstores() {
        return this.dStores.size() >= this.R;
    }

    public void removeDstore(ControllerToDStoreConnection connection) {
        this.dStores.remove(connection);
        this.dStoreConnectionMap.remove(connection.getDstorePort());
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
            return this.fileIndex.startStoring(filename);
        } else if (s == File.State.REMOVE_IN_PROGRESS) {
            return this.fileIndex.startRemoving(filename);
        }
        return false;
    }

    /**
     * Method to add the ports we expect acknowledgements back from to the index manager
     * We specify which acks we are waiting for based on the token passed in (req)
     * @param ports: ports we expect acks from
     * @param filename: name of file to expect acks for
     * @param req: token for what operation we are doing (store or remove)
     * @return true if acks are added successfully, false if not
     */
    public boolean addExpectedAcks(ArrayList<Integer> ports, String filename, Token req) {
        try {
            if (req instanceof StoreToken) {
                this.fileIndex.addStoreAcksForFile(filename, ports);
                return true;
            } else if (req instanceof RemoveToken) {
                this.fileIndex.addRemoveAcksForFile(filename, ports);
                return true;
            } else {
                return false;
            }
        } catch (NumberFormatException e) {
            System.out.println("### ERROR ###   Cannot parse ports as integers : " + ports.toString());
            return false;
        }
    }

    /**
     * Method which checks if all store acks are received by calling method in index manager
     * which waits to see if all acks are received (before timeout)
     * @param filename: Name of file that we are waiting for acks for
     * @param ports: List of port numbers we expect acks from
     * @return
     */
    public boolean ifAllStoreAcksReceived(String filename,ArrayList<Integer> ports) {
        if (this.fileIndex.listenForStoreAcks(filename, this.timeout, ports)) {
            return true;
        } else {
            this.fileIndex.removeFileFromIndex(filename);
            return false;
        }
    }

    /**
     * Method which handles receiving a store acknowledgement from Dstore (just tells index manager that ack
     * has been received)
     * @param t
     * @param port
     */
    public void storeAckReceived(StoreAckToken t, Integer port) {
        if (!this.fileIndex.storeAckReceived(t, port)) {
            System.out.println("### ERROR ###   Invalid STORE_ACK received from port " + port);
        }
    }

    public void removeAckReceived(RemoveAckToken t, Integer port) {
        if (!this.fileIndex.removeAckReceived(t, port)) {
            System.out.println("### ERROR ###   Invalid REMOVE_ACK received from port " + port);
        }
    }

    /**
     * Method which oversees removing a file from the Dstores and index (basically carries out the remove operation)
     * @param filename
     * @param req
     * @param connection
     */
    public void removeFile(String filename, Token req, ControllerClientConnection connection) {
        ArrayList<Integer> ports = this.fileIndex.getDstoresStoringFile(filename);

        //Sends the Dstore the remove instruction
        //Done in a new thread so that we are listening for remove acknowledgements immediately
        //Otherwise, could miss an ack because we are still telling other Dstores to remove the file
        //Could potentially cause issues with timeouts if there are large number of Dstores

        for (Integer port : ports) {
            new Thread(() -> this.dStoreConnectionMap.get(port).removeFile(filename)).start();
        }


        //If added acks successfully
        if (this.addExpectedAcks(ports, filename, req)) {
            //If we receive all remove acks
            if (this.fileIndex.listenForRemoveAcks(filename, this.timeout, ports)) {
                connection.sendToClient(new RemoveCompleteToken(null), Protocol.REMOVE_COMPLETE_TOKEN);
            }
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
