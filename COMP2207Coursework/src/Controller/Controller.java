package Controller;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.PortUnreachableException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

import IndexManager.*;
import Loggers.ControllerLogger;
import Loggers.Logger;
import Loggers.Protocol;
import Tokenizer.*;

import javax.sound.sampled.ReverbType;


public class Controller {

    private int cPort;
    private int R;
    private int timeout;
    private int rebalancePeriod;
    private volatile boolean ifRebalancing;
    private Rebalancer rebalancer;
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
        //Starts thread which handles the rebalancing every x seconds
        final double[] startTime = {System.currentTimeMillis()};
        new Thread(() -> {
            while (true) {
                if (startTime[0] + this.rebalancePeriod < System.currentTimeMillis()) {
                    this.beginRebalance();
                    startTime[0] = System.currentTimeMillis();
                }
            }
        }).start();
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
        this.handleFirstRequest(token, s);

    }

    /******
     * MAY NEED TO HANDLE JOIN QUEUING
     */



    private void handleFirstRequest(Token token, Socket s) throws IOException {
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

    private void beginRebalance() {
        if (!this.ifRebalancing) {
            //Waits for all files to be available before starting the rebalance operation
            while (!this.fileIndex.checkIfAllAvailable()) {}
            System.out.println();
            System.out.println("REBALANCE START AT : " + System.currentTimeMillis());
            this.ifRebalancing = true;
            this.rebalancer = new Rebalancer(this.timeout);
            this.rebalancer.rebalance(this.fileIndex.getFileObjects(),this.dStoreConnectionMap, this.R);
            System.out.println();
            System.out.println("FINISHED REBALANCE AT : " + System.currentTimeMillis());
            System.out.println();
            this.ifRebalancing = false;
        }
    }

    public boolean getIfRebalancing() {
        return this.ifRebalancing;
    }

    public boolean checkEnoughDstores() {
        return this.dStores.size() >= this.R;
    }

    /**
     * Method which gets all files currently stored on the system
     * Only includes files which are fully stored (not in the process of being stored)
     * @return
     */
    public String getFilesForList() {
        StringBuilder s = new StringBuilder();
        for (String file : this.fileIndex.getStoredFilenames()) {
            s.append(" ").append(file);
        }
        return s.toString();
    }

    public void store(String fileToStore, int filesize, ControllerClientConnection clientConnection) {
        //If we receive a store request from client, we try to add the file to the index. If it already
        //exists, we return an ERROR_FILE_ALREADY_EXISTS
        if (!this.fileIndex.startStoring(fileToStore, filesize)) {
            clientConnection.sendToClient(new FileAlreadyExistsToken(null), Protocol.ERROR_FILE_ALREADY_EXISTS_TOKEN);
            return;
        }

        //If file does not already exist, we continue and try to get a String of all ports to store
        //the file to. Then we send these ports to the client
        String message = this.getPortsForStore(fileToStore);
        clientConnection.sendToClient(new StoreToken(null,null,0), message);

        //Here we extract the port numbers from the string (req) as integers we are storing in and bundle it in
        //an ArrayList called intPorts
        String removedExtraSpace = message.substring(1);
        String[] ports = (removedExtraSpace.split(" "));
        ArrayList<Integer> intPorts = new ArrayList<>();
        for (String port : ports) {
            //Should maybe heave try/catch block here but there is no user input so I don't think it is needed
            intPorts.add(Integer.parseInt(port));
        }

        //Tells controller the what ports we expect a STORE_ACK from
        this.fileIndex.addStoreAcksForFile(fileToStore, intPorts);
        //Tells controller to start listening for the STORE_ACKs, returns true if all the acks are received within
        //the controller's timeout period
        boolean ifAcksReceived = this.ifAllStoreAcksReceived(fileToStore, intPorts);
        if (ifAcksReceived) {
            //If all the store acks are received, we send a STORE_COMPLETE to client
            clientConnection.sendToClient(new StoreCompleteToken(null), Protocol.STORE_COMPLETE_TOKEN);
        }
    }


    /**
     * Method which oversees removing a file from the Dstores and index (basically carries out the remove operation)
     * @param filename
     * @param req
     * @param clientConnection
     */
    public void remove(String filename, Token req, ControllerClientConnection clientConnection) {
        //Tries to start removing process in index manager. If it fails, the file does not exist, so send error to client
        if (!this.fileIndex.startRemoving(filename)) {
            clientConnection.sendToClient(new FileNotExistToken(Protocol.ERROR_FILE_DOES_NOT_EXIST_TOKEN), Protocol.ERROR_FILE_DOES_NOT_EXIST_TOKEN);
            return;
        }
        ArrayList<Integer> ports = this.fileIndex.getDstoresStoringFile(filename);
        //Sends the Dstore the remove instruction
        //Done in a new thread so that we are listening for remove acknowledgements immediately
        //Otherwise, could miss an ack because we are still telling other Dstores to remove the file
        //Could potentially cause issues with timeouts if there are large number of Dstores
        for (Integer port : ports) {
            new Thread(() -> {
                String msg = Protocol.REMOVE_TOKEN + " " + filename;
                this.dStoreConnectionMap.get(port).sendMessageToDstore(msg);
            }).start();
        }
        //Add expected acks to index manager (what ports to expect REMOVE_ACKs back from)
        this.fileIndex.addRemoveAcksForFile(filename, ports);
        //If we receive all remove acks
        if (this.fileIndex.listenForRemoveAcks(filename, this.timeout, ports)) {
            clientConnection.sendToClient(new RemoveCompleteToken(null), Protocol.REMOVE_COMPLETE_TOKEN);
        }
    }


    /**
     * Method to handle the LOAD operation
     * @param filename
     * @param clientConnection
     * @return
     */
    public ArrayList<Integer> load(String filename, ControllerClientConnection clientConnection) {
        //If our list of files does not contain requested file, return FILE_DOES_NOT_EXIST error to client and end
        //execution of load instruction
        if (!this.fileIndex.getStoredFilenames().contains(filename)) {
            clientConnection.sendToClient(new FileNotExistToken(null), Protocol.ERROR_FILE_DOES_NOT_EXIST_TOKEN);
            return null;
        }
        //Make a copy of list of Dstores all storing certain file
        //Need a copy because we want to alter the list, but do not want to alter the original
        //Basically, circumventing call-by-reference
        ArrayList<Integer> allDstoresStoringFile = new ArrayList<>(this.fileIndex.getDstoresStoringFile(filename));
        if (allDstoresStoringFile.size() != 0) {
            //Gets first port storing file and sends it to client. Then removes it from list of Dstores
            //so in case of a reload, we do not send the same port again
            Integer portToLoadFrom = allDstoresStoringFile.get(0);
            allDstoresStoringFile.remove(portToLoadFrom);
            //Gets filesize to send top client
            int filesize = this.fileIndex.getFile(filename).getFilesize();
            //If returned size is -1, there file does not exist at this point (remove concurrency error)
            if (filesize != -1) {
                //Creates and sends message to client
                LoadFromToken tokenToSend = new LoadFromToken(Protocol.LOAD_FROM_TOKEN + " " + portToLoadFrom
                        + " " + filesize, portToLoadFrom, filesize);
                clientConnection.sendToClient(tokenToSend, tokenToSend.req);
                //Returns list of ports that have not been checked if they store the file (in case of reload)
                return allDstoresStoringFile;
            } else {
                clientConnection.sendToClient(new FileNotExistToken(null), Protocol.ERROR_FILE_DOES_NOT_EXIST_TOKEN);
                return null;
            }
        } else {
            clientConnection.sendToClient(new ErrorLoadToken(null), Protocol.ERROR_LOAD_TOKEN);
            return null;
        }
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

    public int getFilesize(String filename) {
        DstoreFile file = this.fileIndex.getFile(filename);
        if (file != null) {
            return file.getFilesize();
        } else {
            return -1;
        }
    }

    public synchronized void removeDstore(ControllerToDStoreConnection connection) {
        this.dStores.remove(connection);
        this.dStoreConnectionMap.remove(connection.getDstorePort());
        this.fileIndex.removeDstore(connection.getDstorePort());
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

    public void listReceived(FileListToken t, Integer port) {
        this.rebalancer.listReceived(t.fileList, port);
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
