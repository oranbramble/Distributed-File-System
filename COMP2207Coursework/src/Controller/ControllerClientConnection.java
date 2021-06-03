package Controller;

import IndexManager.File;
import ConnectionParent.ConnectionParent;
import Loggers.ControllerLogger;
import Loggers.Protocol;
import Tokenizer.*;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;

public class ControllerClientConnection extends ConnectionParent {

    private Token firstRequest;
    private Controller controller;

    public ControllerClientConnection(Socket s, Token t, Controller controller) throws IOException {
        super(s);
        this.firstRequest = t;
        this.controller = controller;
    }

    @Override
    public void run() {
        this.handleRequest(this.firstRequest);
        try {
            while (true) {
                String req = this.inText.readLine();
                ControllerLogger.getInstance().messageReceived(this.socket, req);
                Token reqToken = Tokenizer.getToken(req);
                this.handleRequest(reqToken);

            }
        //When client disconnects, we ignore it as this thread will now end
        } catch (IOException e) {}

    }

    private void handleRequest(Token reqToken) {
        //For any request, checks if enough Dstores have joined. If not, sends error to client
        if (!this.controller.checkEnoughDstores()) {
            this.sendToClient(new NotEnoughDStoresToken(reqToken.req), Protocol.ERROR_NOT_ENOUGH_DSTORES_TOKEN);
            return;
        }

        //If request is a list, get list of files from controller and send them to client
        if (reqToken instanceof ListToken) {
            String message = this.controller.getFileList();
            this.sendToClient(reqToken, message);

        //If request is a store, call handleStore method to handle request
        } else if (reqToken instanceof StoreToken) {
            this.handleStore(reqToken);

        } else if (reqToken instanceof RemoveToken) {

        } else if (reqToken instanceof LoadToken) {

        } else {
            System.out.println("### ERROR ###   Error parsing request from client\n" +
                               "                Request: " + reqToken.req);
        }
    }

    /**
     * Method which generalises sending data to the client. The parameters are explained below
     * @param reqToken: The tokenized request the client sent to the controller
     * @param message: String message to send to the client, usually just contains the data specific for that message
     */
    private void sendToClient(Token reqToken, String message) {
        //If request to controller was a list, we are sending back a LIST filename1 filename2 ... message
        if (reqToken instanceof ListToken) {
            message = Protocol.LIST_TOKEN + " " + message;
        //If request was store, we are sending back a STORE_TO port1 port2 port3 ... message
        //NOTE: The string passed in here for a store method has to a=have a preceding space (E.g. " port1 port2 port3")
        } else if (reqToken instanceof StoreToken) {
            message = Protocol.STORE_TO_TOKEN + message;

        //This catches messages where we don't want to send any data back to the client
        //Technically, not needed, just thought it shows all the messages we are sending to client to make it clear
        //what data goes to and from
        } else if (reqToken instanceof  StoreCompleteToken ||
                   reqToken instanceof FileAlreadyExistsToken ||
                   reqToken instanceof NotEnoughDStoresToken) {
        }

        //Here the message actually gets sent to the client
        this.outText.println(message);
        this.outText.flush();
        ControllerLogger.getInstance().messageSent(this.socket, message);
    }

    /**
     * Method which handles a STORE request operation
     * @param req: The tokenized version of the STORE request sent by the client
     */
    private void handleStore(Token req) {
        //If we receive a store request from client, we try to add the file to the index. If it already
        //exists, we return an ERROR_FILE_ALREADY_EXISTS
        String fileToStore = ((StoreToken)req).filename;
        boolean ifIndexUpdated = this.controller.updateIndex(fileToStore, File.State.STORE_IN_PROGRESS);
        if (!ifIndexUpdated) {
            this.sendToClient(new FileAlreadyExistsToken(req.req), Protocol.ERROR_FILE_ALREADY_EXISTS_TOKEN);
            return;
        }

        //If file does not already exist, we continue and try to get a String of all ports to store
        //the file to. Then we send these ports to the client
        String message = this.controller.getPortsForStore(fileToStore);
        this.sendToClient(req, message);

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
        //Gets a boolean return, so true if we succeeded in telling the controller
        //Would fail if the port numbers were invalid
        System.out.println(intPorts.toString());
        boolean acksAdded = this.controller.addExpectedAcks(intPorts, fileToStore);
        if (acksAdded) {
            //Tells controller to start listening for the STORE_ACKs, returns true if all the acks are received within
            //the controller's timeout period
            boolean ifAcksReceived = this.controller.ifStoreAcksReceived(fileToStore, intPorts);
            if (ifAcksReceived) {
                //If all th
                this.sendToClient(new StoreCompleteToken(Protocol.STORE_COMPLETE_TOKEN), Protocol.STORE_COMPLETE_TOKEN);
            }
        }
    }
}
