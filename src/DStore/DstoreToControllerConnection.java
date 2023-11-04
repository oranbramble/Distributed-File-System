package DStore;

import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;

import Loggers.*;
import ConnectionParent.ConnectionParent;
import Tokenizer.*;

public class DstoreToControllerConnection extends ConnectionParent{

    private final Dstore dstore;

    public DstoreToControllerConnection(Socket s, int dStorePort, Dstore dstore) throws IOException {
        super(s);
        this.dstore = dstore;
        String joinMsg = Protocol.JOIN_TOKEN + " " + dStorePort;
        this.outText.println(joinMsg);
        this.outText.flush();
        DstoreLogger.getInstance().messageSent(socket, joinMsg);
        this.startListening();
    }

    /**
     * Method which listens for incoming requests from controller threads
     * We put this method in a thread instead of making the whole class a thread because
     * I want to be able to access the connection from Dstore to Controller outside the thread
     * I.e. I want to access the PrintWriter to send acknowledgements to controller
     * If i put the whole class in a thread, I would lose access to the streams
     */
    public void startListening() {
        new Thread(() -> {
            try {
                while (true) {
                    String req = this.inText.readLine();
                    if (req != null) {
                        DstoreLogger.getInstance().messageReceived(this.socket, req);
                        Token reqToken = Tokenizer.getToken(req);
                        if (reqToken != null) {
                            //Have to put handle request in new thread since many controller threads are trying to
                            //communicate with this one connection, so we want to handle all their requests
                            //at the same time. This is different to the client connection, as there is only one client
                            //thread per connection there, so we can deal with requests sequentially
                            new Thread(() -> {
                                this.handleRequest(reqToken);
                            }).start();
                        } else {
                            System.out.println("### ERROR ###   Malformed input received on port " + this.socket.getLocalPort() +
                                    " from port " + this.socket.getPort());
                        }
                    } else {
                        this.dstore.end();
                    }
                }
            } catch (IOException e) {
                System.out.println("### ERROR ###  Dstore lost connection to controller");
                this.dstore.end();
            }
        }).start();
    }

    private void handleRequest(Token reqToken) {
        if (reqToken instanceof RemoveToken) {
            String filename = ((RemoveToken)reqToken).filename;
            boolean ifDeleted = this.dstore.removeFile(filename);
            if (ifDeleted) {
                this.sendMessageToController(Protocol.REMOVE_ACK_TOKEN + " " + filename);
            } else {
                this.sendMessageToController(Protocol.ERROR_FILE_DOES_NOT_EXIST_TOKEN + " " + filename);
            }
        } else if (reqToken instanceof ListToken) {
            ArrayList<String> files = this.dstore.getListOfFiles();
            StringBuilder message = new StringBuilder(Protocol.LIST_TOKEN);
            for (String f : files) {
                message.append(" ").append(f);
            }
            this.sendMessageToController(message.toString());
        } else if (reqToken instanceof RebalanceToken) {
            this.handleRebalance((RebalanceToken) reqToken);
        }
    }

    private void handleRebalance(RebalanceToken rebalanceToken) {
        this.handleSendingToDstores(rebalanceToken.filesToSend);
        for (String fileToRemove : rebalanceToken.filesToRemove) {
            this.dstore.removeFile(fileToRemove);
        }
        this.sendMessageToController(Protocol.REBALANCE_COMPLETE_TOKEN);
    }

    public void sendMessageToController(String msg) {
        this.outText.println(msg);
        this.outText.flush();
        DstoreLogger.getInstance().messageSent(this.socket, msg);
    }

    private void handleSendingToDstores(ArrayList<FileToSend> filesToSend) {
        try {
            for (FileToSend file : filesToSend) {
                for (int port : file.dStores) {
                    Socket s = new Socket(InetAddress.getLocalHost(), port);
                    PrintWriter storeOutText = new PrintWriter(new BufferedOutputStream(s.getOutputStream()));
                    BufferedReader storeInText = new BufferedReader(new InputStreamReader(s.getInputStream()));
                    OutputStream storeOutData = s.getOutputStream();

                    int filesize = this.dstore.getFilesize(file.filename);
                    if (filesize != -1) {

                        String rebalanceStoreMessage = Protocol.REBALANCE_STORE_TOKEN + " " + file.filename + " " + filesize;
                        storeOutText.println(rebalanceStoreMessage);
                        storeOutText.flush();
                        DstoreLogger.getInstance().messageSent(s, rebalanceStoreMessage);

                        String reply = storeInText.readLine();
                        if (reply != null) {
                            DstoreLogger.getInstance().messageReceived(s, reply);
                            Token token = Tokenizer.getToken(reply);

                            if (token instanceof AckToken) {
                                byte[] fileData = this.dstore.loadDataFromFile(file.filename);
                                if (fileData != null) {
                                    storeOutData.write(fileData);
                                    storeOutData.flush();
                                }
                            }
                        }
                    }
                    s.close();
                }
            }
        } catch (IOException e) {
            System.out.println("### ERROR ###   Could not connect to Dstore when rebalancing");
        }
    }

}
