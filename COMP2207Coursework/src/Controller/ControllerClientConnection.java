package Controller;

import ConnectionParent.ConnectionParent;
import Loggers.ControllerLogger;
import Loggers.Protocol;
import Tokenizer.*;

import java.io.*;
import java.net.Socket;
import java.util.List;

public class ControllerClientConnection extends ConnectionParent {

    private Token firstRequest;
    private Controller controller;

    public ControllerClientConnection(Socket s, Token t, Controller controller) throws IOException {
        super(s);
        this.firstRequest = t;
        this.controller = controller;
    }

    public void run() {
        this.handleRequest(this.firstRequest);
        boolean isConnected = true;
        while (isConnected) {
            try {
                String req = this.inText.readLine();
                ControllerLogger.getInstance().messageReceived(this.socket, req);
                Token reqToken = Tokenizer.getToken(req);
                this.handleRequest(reqToken);
            } catch (IOException e) {
                /**
                 * HANDLE LOST CONNECTIONS HERE
                 */
                System.out.println("### CONTROLLER ERROR ###   Connection with client lost");
                isConnected = false;
            }
        }
    }

    private void handleRequest(Token reqToken) {
        if (reqToken instanceof ListToken) {
            String message = this.controller.getFileList();
            this.sendToClient(reqToken, message);

        } else if (reqToken instanceof StoreToken) {
            String message = this.controller.getPortsForStore(((StoreToken)reqToken).filename);
            this.sendToClient(reqToken, message);
            this.controller.setExpectedAcks((message.split(" ")).length - 1);
            new Thread( () -> {
                this.controller.storeAckChecker(((StoreToken)reqToken).filename);
                this.sendToClient(new StoreCompleteToken(Protocol.STORE_COMPLETE_TOKEN), Protocol.STORE_COMPLETE_TOKEN);
            }).start();

        } else if (reqToken instanceof RemoveToken) {

        } else if (reqToken instanceof LoadToken) {

        } else {
            System.out.println("### ERROR ###   Error parsing request from client\n" +
                               "                Request: " + reqToken.req);
        }
    }

    private void sendToClient(Token reqToken, String message) {
        if (reqToken instanceof ListToken) {
            message = Protocol.LIST_TOKEN + " " + message;

        } else if (reqToken instanceof StoreToken) {
            message = Protocol.STORE_TO_TOKEN + message;

        } else if (reqToken instanceof  StoreCompleteToken) {

        }

        this.outText.println(message);
        this.outText.flush();
        ControllerLogger.getInstance().messageSent(this.socket, message);
    }
}
