package Controller;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;

import ConnectionParent.*;
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

    public void startListening() {
        new Thread (() -> {
            try {
                while (true) {
                    String msg = this.inText.readLine();
                    Token msgToken = Tokenizer.getToken(msg);
                    if (msgToken instanceof StoreAckToken) {
                        this.controller.storeAckReceived((StoreAckToken) msgToken, this.dStorePort);
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
}
