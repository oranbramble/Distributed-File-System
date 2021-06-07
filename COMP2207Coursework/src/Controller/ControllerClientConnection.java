package Controller;

import IndexManager.*;
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
    private ArrayList<Integer> reloadDstoresToTry;
    private ArrayList<String> queuedRequests;

    public ControllerClientConnection(Socket s, Token t, Controller controller) throws IOException {
        super(s);
        this.firstRequest = t;
        this.controller = controller;
        this.reloadDstoresToTry = new ArrayList<>();
    }

    @Override
    public void run() {
        this.handleRequest(this.firstRequest);
        try {
            while (true) {
                String req = this.inText.readLine();
                ControllerLogger.getInstance().messageReceived(this.socket, req);
                //If request is null, client has disconnected so loop will break and this thread will end
                if (req != null) {
                    //If we are rebalancing, then que requests
                    if (this.controller.getIfRebalancing()) {
                        this.queuedRequests.add(req);
                    } else {
                        //Deals with queued requests first, then does request just received
                        for (String queuedReq : this.queuedRequests) {
                            Token reqToken = Tokenizer.getToken(queuedReq);
                            this.handleRequest(reqToken);
                        }
                        Token reqToken = Tokenizer.getToken(req);
                        this.handleRequest(reqToken);
                    }
                } else {
                    break;
                }

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
            String message = this.controller.getFilesForList();
            this.sendToClient(reqToken, message);
        //If request is a store, call handleStore method to handle request
        } else if (reqToken instanceof StoreToken) {
            this.handleStore(reqToken);
        } else if (reqToken instanceof RemoveToken) {
            this.handleRemove(reqToken);
        } else if (reqToken instanceof LoadToken) {
            this.handleLoad(reqToken);
        } else if (reqToken == null){
            System.out.println("### ERROR ###   Error parsing request from client\n" +
                               "                Request: " + reqToken.req);
        }

        if (reqToken instanceof ReloadToken) {
            this.handleReload(reqToken);
        }
    }

    /**
     * Method which generalises sending data to the client. The parameters are explained below
     * @param reqToken: The tokenized request the client sent to the controller
     * @param message: String message to send to the client, usually just contains the data specific for that message
     */
    public void sendToClient(Token reqToken, String message) {
        //If request to controller was a list, we are sending back a LIST filename1 filename2 ... message
        if (reqToken instanceof ListToken) {
            message = Protocol.LIST_TOKEN + message;
        //If request was store, we are sending back a STORE_TO port1 port2 port3 ... message
        } else if (reqToken instanceof StoreToken) {
            message = Protocol.STORE_TO_TOKEN + message;
        //This catches messages where we don't want to send any data back to the client
        //Technically, not needed, just thought it shows all the messages we are sending to client to make it clear
        //what data goes to and from
        } else if (reqToken instanceof  StoreCompleteToken ||
                   reqToken instanceof FileAlreadyExistsToken ||
                   reqToken instanceof NotEnoughDStoresToken ||
                   reqToken instanceof FileNotExistToken ||
                   reqToken instanceof RemoveCompleteToken) {
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
        String filename = ((StoreToken)req).filename;
        int filesize = ((StoreToken)req).filesize;
        this.controller.store(filename, filesize, this);
    }

    /**
     * Method to handle a remove request
     * @param req
     */
    private void handleRemove(Token req) {
        //Tries to update index to set file to REMOVE_IN_PROGRESS, if that fails then we send error to client
        String fileToRemove = ((RemoveToken)req).filename;
        this.controller.remove(fileToRemove, req, this);
    }

    private void handleLoad(Token req) {
        String filename = ((LoadToken)req).filename;
        this.reloadDstoresToTry = this.controller.load(filename, this);
    }

    private void handleReload(Token req) {
        String filename = ((ReloadToken)req).filename;
        if (this.reloadDstoresToTry != null) {
            if (this.reloadDstoresToTry.size() != 0) {
                int portToTry = this.reloadDstoresToTry.get(0);
                this.reloadDstoresToTry.remove(Integer.valueOf(portToTry));
                int filesize = this.controller.getFilesize(filename);
                LoadFromToken tokenToSend = new LoadFromToken(Protocol.LOAD_FROM_TOKEN + " " + portToTry + " " + filesize, portToTry, filesize);
                this.sendToClient(tokenToSend, tokenToSend.req);
            } else {
                this.sendToClient(new ErrorLoadToken(null), Protocol.ERROR_LOAD_TOKEN);
            }
        } else {
            this.sendToClient(new ErrorLoadToken(null), Protocol.ERROR_LOAD_TOKEN);
        }
    }
}
