package DStore;

import java.io.*;
import java.net.Socket;
import ConnectionParent.*;
import Loggers.DstoreLogger;
import Loggers.Protocol;
import Tokenizer.*;

public class DstoreToControllerConnection extends ConnectionParent{

    private Dstore dstore;

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
                    DstoreLogger.getInstance().messageReceived(this.socket, req);
                    Token reqToken = Tokenizer.getToken(req);
                    if (reqToken != null) {
                        //Have to put handle request in new thread since many controller threads are trying to
                        //communicate with this one connection, so we want to handle all their requests
                        //at the same time. This is different to the client connection, as there is only one client
                        //thread per connection there, so we can deal with requests sequentially
                        new Thread (() -> {
                            this.handleRequest(reqToken);
                        }).start();

                    } else {
                        System.out.println("### ERROR ###   Malformed input received on port " + this.socket.getLocalPort() +
                                " from port " + this.socket.getPort());
                    }
                }
            } catch (IOException e) {
                System.out.println("### ERROR ###  Dstore lost connection to controller");
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
        }
    }

    public void sendMessageToController(String msg) {
        this.outText.println(msg);
        this.outText.flush();
        DstoreLogger.getInstance().messageSent(this.socket, msg);
    }
}
