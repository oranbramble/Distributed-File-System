package Controller;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.concurrent.TimeoutException;

import ConnectionParent.*;
import Loggers.ControllerLogger;
import Loggers.Protocol;
import Tokenizer.*;

/**
 * Class to handle connection from controller to DStore (acts as interface between controller and DStore)
 */
public class ControllerToDStoreConnection extends ConnectionParent{

    private int dStorePort;
    private Controller controller;
    private ArrayList<Token> queuedRequests;

    public ControllerToDStoreConnection(Socket s, int port, Controller controller) throws IOException {
        super(s);
        this.dStorePort = port;
        this.controller = controller;
        this.queuedRequests = new ArrayList<>();
        this.startListening();
    }

    /**
     * Method which listens for all incoming traffic from a Dstore
     * Basically just listens for acknowledgements from Dstore (E.g. when storing/removing)
     */
    public void startListening() {
        new Thread (() -> {
            try {
                while (true) {
                    String msg = this.inText.readLine();
                    ControllerLogger.getInstance().messageReceived(this.socket, msg);
                    Token msgToken = Tokenizer.getToken(msg);
                    //If we are rebalancing, que requests
                    if (msg != null) {
                        if (msgToken instanceof FileListToken) {
                            this.controller.listReceived(((FileListToken) msgToken), this.dStorePort);
                        } else if (msgToken instanceof ListToken) {
                            FileListToken token = new FileListToken(Protocol.LIST_TOKEN, new StringTokenizer(""));
                            this.controller.listReceived(token, this.dStorePort);

                        } else if (this.controller.getIfRebalancing()) {
                            this.queuedRequests.add(msgToken);
                        } else {
                            //Deals with queued requests first, then does request just received
                            for (Token queuedReq : this.queuedRequests) {
                                this.handleRequest(queuedReq);
                            }
                            this.handleRequest(msgToken);
                        }
                    } else {
                        this.connectionLost();
                    }
                }
            } catch (SocketTimeoutException e) {
                System.out.println("--- TIMEOUT ---");
            } catch (IOException e) {
                this.connectionLost();
            }
        }).start();
    }

    private void handleRequest(Token msgToken) {
        if (msgToken instanceof StoreAckToken) {
            this.controller.storeAckReceived((StoreAckToken) msgToken, this.dStorePort);
        } else if (msgToken instanceof RemoveAckToken) {
            this.controller.removeAckReceived((RemoveAckToken) msgToken, this.dStorePort);
        }else if (msgToken instanceof FileListToken) {
            this.controller.listReceived((FileListToken)msgToken, this.dStorePort);
        } else if (msgToken instanceof FileNotExistFilenameToken) {
            FileNotExistFilenameToken t = (FileNotExistFilenameToken)msgToken;
            System.out.println("### ERROR ###   File " + t.filename + " does not exist on Dstore (port: " + this.dStorePort);
        }
    }

    private void connectionLost() {
        System.out.println("### ERROR ###   Connection to Dstore on port " + this.dStorePort + " lost");
        this.controller.removeDstore(this);
    }

    public int getDstorePort() {
        return dStorePort;
    }

    public void sendMessageToDstore(String msg) {
        this.outText.println(msg);
        this.outText.flush();
        ControllerLogger.getInstance().messageSent(this.socket, msg);
    }
}
