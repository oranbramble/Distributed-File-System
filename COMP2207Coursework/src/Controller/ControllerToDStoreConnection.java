package Controller;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
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

    public ControllerToDStoreConnection(Socket s, int port, Controller controller) throws IOException {
        super(s);
        this.dStorePort = port;
        this.controller = controller;
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
                    if (msgToken instanceof StoreAckToken) {
                        this.controller.storeAckReceived((StoreAckToken) msgToken, this.dStorePort);
                    } else if (msgToken instanceof RemoveAckToken) {
                        this.controller.removeAckReceived((RemoveAckToken)msgToken, this.dStorePort);
                    } else if (msgToken instanceof FileNotExistFilenameToken) {
                        FileNotExistFilenameToken t = (FileNotExistFilenameToken)msgToken;
                        System.out.println("### ERROR ###   File " + t.filename + " does not exist on Dstore (port: " + this.dStorePort);
                    }
                }
            } catch (SocketTimeoutException e) {
                System.out.println("TIMEOUT");
            } catch (IOException e) {
                this.connectionLost();
            }
        }).start();
    }

    private void connectionLost() {
        System.out.println("### ERROR ###   Connection to Dstore on port " + this.dStorePort + " lost");
        this.controller.removeDstore(this);
    }

    public int getDstorePort() {
        return dStorePort;
    }

    public void removeFile(String filename) {
        String msg = Protocol.REMOVE_TOKEN + " " + filename;
        this.outText.println(msg);
        this.outText.flush();
        ControllerLogger.getInstance().messageSent(this.socket, msg);
    }
}
