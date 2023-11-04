package Controller;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;

import Tokenizer.*;
import Loggers.*;
import ConnectionParent.ConnectionParent;

public class ControllerClientConnection extends ConnectionParent {

    private Token firstRequest;
    private Controller controller;
    private ArrayList<Integer> reloadDstoresToTry;
    private ArrayList<Token> queuedRequests;

    public ControllerClientConnection(Socket s, Token t, Controller controller) throws IOException {
        super(s);
        this.firstRequest = t;
        this.controller = controller;
        this.reloadDstoresToTry = new ArrayList<>();
        this.queuedRequests = new ArrayList<>();
    }

    @Override
    public void run() {
        try {
            //Tries to handles first request. However, if we are currently rebalancing, we just que it
            if (this.firstRequest != null) {
                if (this.controller.getIfRebalancing()) {
                    this.queuedRequests.add(this.firstRequest);
                } else {
                    this.handleRequest(this.firstRequest);
                }
            }
            while (true) {
                //If controller is currently rebalancing, we want to que all requests
                if (this.controller.getIfRebalancing()) {
                    try {
                        //Adding a timeout so that we continuously check if the controller has finished rebalancing
                        //so that we can deal with queued requests. If we didn't, a new request would have to come
                        //in before the queued requests are dealt with
                        this.socket.setSoTimeout(1000);
                        String req = this.inText.readLine();
                        this.socket.setSoTimeout(0);
                        //If connection with client is over, a null will be sent, so we check connection is still up
                        if (req != null) {
                            ControllerLogger.getInstance().messageReceived(this.socket, req);
                            //Tokenize request and que it
                            Token reqToken = Tokenizer.getToken(req);
                            this.queuedRequests.add(reqToken);
                        } else {
                            //If null received, connection with client over so break
                            break;
                        }
                    } catch (SocketTimeoutException ignored) {
                        this.socket.setSoTimeout(0);
                    }

                    //If we are not currently rebalancing.
                    //NOTE : This below segment could be running whilst a rebalance operation begins in the controller
                    //because readLine() pauses the program waiting for input, so we have to check again if we
                    //are mid rebalance
                } else {
                    //If handleRequests returns true, we have handled all queued requests without another
                    //rebalance starting. if it's false, we know another rebalance has started to do not try to
                    //handle requests here
                    if (this.handleQueuedRequests()) {
                        String req = this.inText.readLine();
                        if (req != null) {
                            ControllerLogger.getInstance().messageReceived(this.socket, req);
                            Token reqToken = Tokenizer.getToken(req);
                            //Need another check to see if we are rebalancing, in case request received mid rebalance while
                            //control flow is within this portion, not the segment above
                            if (this.controller.getIfRebalancing()) {
                                this.queuedRequests.add(reqToken);
                            } else {
                                //If passed both rebalance checks, we can handle the request
                                this.handleRequest(reqToken);
                            }
                        } else {
                            break;
                        }
                    }
                }
            }
            //When client disconnects, we ignore it as this thread will now end
        } catch (IOException ignored) {
        }

    }

    private boolean handleQueuedRequests() {
        //Deals with queued requests if we are not currently rebalancing
        while (this.queuedRequests.size() != 0) {
            if (!this.controller.getIfRebalancing()) {
                //Handles the first request in list, which is the first in the que
                Token reqToken = this.queuedRequests.get(0);
                this.handleRequest(reqToken);
                this.queuedRequests.remove(0);
            } else {
                //A rebalance operation has begun, so we quit
                return false;
            }
        }
        return true;
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
            System.out.println("### ERROR ###   Malformed input received by Controller from Client");
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
        } else if (reqToken instanceof StoreCompleteToken ||
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
        // CORRECT FILESIZE
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
                if (filesize != -1) {
                    LoadFromToken tokenToSend = new LoadFromToken(Protocol.LOAD_FROM_TOKEN + " " + portToTry + " " + filesize, portToTry, filesize);
                    this.sendToClient(tokenToSend, tokenToSend.req);
                } else {
                    this.sendToClient(new FileNotExistToken(null), Protocol.ERROR_FILE_DOES_NOT_EXIST_TOKEN);
                }
            } else {
                this.sendToClient(new ErrorLoadToken(null), Protocol.ERROR_LOAD_TOKEN);
            }
        } else {
            this.sendToClient(new ErrorLoadToken(null), Protocol.ERROR_LOAD_TOKEN);
        }
    }
}
