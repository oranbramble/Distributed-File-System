package DStore;

import ConnectionParent.ConnectionParent;
import Loggers.DstoreLogger;
import Loggers.Protocol;
import Tokenizer.*;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class DstoreClientConnection extends ConnectionParent {

    private Dstore dStore;
    private int controllerPort;
    private int timeout;

    public DstoreClientConnection(Socket s, Dstore dStore, int controllerPort, int timeout) throws IOException {
        super(s);
        this.dStore = dStore;
        this.controllerPort = controllerPort;
        this.timeout = timeout;
    }

    @Override
    public void run() {
        try {
            while (true) {
                String req = this.inText.readLine();
                DstoreLogger.getInstance().messageReceived(this.socket, req);
                Token reqToken = Tokenizer.getToken(req);
                if (reqToken != null) {
                    this.handleRequest(reqToken);

                } else {
                    System.out.println("### ERROR ###   Malformed input received on port " + this.socket.getLocalPort() +
                            " from port " + this.socket.getPort());
                }
            }
        } catch (IOException e) {
        }
    }


    public void handleRequest(Token reqToken) {
        if (reqToken instanceof StoreToken) {
            //Sends acknowledgement to client
            this.sendToClient(Protocol.ACK_TOKEN, reqToken);
            //Gets file content from client
            String fileContent = getFileContentFromSocket(((StoreToken)reqToken).filesize);
            //Writes data to file
            if (fileContent != null) {
                this.dStore.storeToFile(((StoreToken)reqToken).filename, fileContent);
                //Once file is stored, send acknowledgement to controller
                this.dStore.sendAckToController(Protocol.STORE_ACK_TOKEN + " " + ((StoreToken)reqToken).filename);
            }

        }
    }

    private void sendToClient(String message, Token reqReceivedToken) {
        if (reqReceivedToken instanceof StoreToken) {
        }
        outText.println(message);
        outText.flush();
        DstoreLogger.getInstance().messageSent(this.socket, message);
    }


    private String getFileContentFromSocket(int filesize) {
        try {
            //We set a timeout for just this reading in, so that if client does not send data then we can still
            //continue
            this.socket.setSoTimeout(this.timeout);
            byte[] bytes = this.inData.readNBytes(filesize);
            this.socket.setSoTimeout(0);
            return new String(bytes);
        } catch (SocketException e) {
            System.out.println("--- TIMEOUT ---   Dstore (port:" + this.socket.getLocalPort() + ") timed out waiting" +
                               " for file data from client (port:" + this.socket.getPort());
            return null;
        } catch (IOException e) {
            System.out.println("### ERROR ###   Connection to client lost when Dstore expecting file data");
            return null;
        }
    }

}
