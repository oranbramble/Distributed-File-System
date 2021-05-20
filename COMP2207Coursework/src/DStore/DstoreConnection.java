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

public class DstoreConnection extends ConnectionParent {

    private Dstore dStore;
    private int controllerPort;

    public DstoreConnection(Socket s, Dstore dStore, int controllerPort) throws IOException {
        super(s);
        this.dStore = dStore;
        this.controllerPort = controllerPort;
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
            /**
             * HANDLE LOST CONNECTIONS HERE
             */
            System.out.println("### ERROR ###   Error on connection from port "
                                                       + this.socket.getLocalPort() + " to port "
                                                       + this.socket.getPort());
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
            }
            this.sendAckToController(Protocol.STORE_ACK_TOKEN + " " + ((StoreToken)reqToken).filename);
        }
    }

    private void sendToClient(String message, Token reqToken) {
        if (reqToken instanceof StoreToken) {
        }
        outText.println(message);
        outText.flush();
        DstoreLogger.getInstance().messageSent(this.socket, message);
    }


    private String getFileContentFromSocket(int filesize) {
        try {
            byte[] bytes = inData.readNBytes(filesize);
            String data = new String(bytes);
            return data;
        } catch (IOException e) {
            System.out.println("### ERROR ###   Cannot read from InputStream from client");
            return null;
        }
    }

    private void sendAckToController(String ack) {
        try {
            Socket controllerSocket = new Socket(InetAddress.getLocalHost(), this.controllerPort);
            PrintWriter outText = new PrintWriter(new BufferedOutputStream(controllerSocket.getOutputStream()));
            outText.println(ack);
            outText.flush();
            DstoreLogger.getInstance().messageSent(this.socket, ack);
        } catch (IOException e) {
            System.out.println("### ERROR ###   Cannot connect to controller to send acknowledgement");
        }
    }
}
