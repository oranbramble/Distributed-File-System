package DStore;

import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Arrays;

import Tokenizer.*;
import Loggers.DstoreLogger;
import Loggers.Protocol;
import ConnectionParent.ConnectionParent;

public class DstoreClientConnection extends ConnectionParent {

    private final Dstore dStore;
    private final int timeout;

    public DstoreClientConnection(Socket s, Dstore dStore, int timeout) throws IOException {
        super(s);
        this.dStore = dStore;
        this.timeout = timeout;
    }

    @Override
    public void run() {
        try {
            while (true) {
                String req = this.inText.readLine();
                if (req != null) {
                    DstoreLogger.getInstance().messageReceived(this.socket, req);
                    Token reqToken = Tokenizer.getToken(req);
                    if (reqToken != null) {
                        boolean ifBreak = this.handleRequest(reqToken);
                        if (ifBreak) {
                            break;
                        }
                    } else {
                        System.out.println("### ERROR ###   Malformed input received by Dstore from Client");
                    }
                }else {
                    break;
                }
            }
        } catch (IOException e) {
            System.out.println("### ERROR ### Error handling Dstore request from Clinet");
        }
        try {
            this.socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public boolean handleRequest(Token reqToken) throws IOException {
        if (reqToken instanceof StoreToken) {
            //Sends acknowledgement to client
            this.sendToClient(Protocol.ACK_TOKEN, reqToken);
            //Gets file content from client
            byte[] fileContent = getFileContentFromSocket(((StoreToken)reqToken).filesize);
            //Writes data to file
            if (fileContent != null) {
                this.dStore.storeToFile(((StoreToken)reqToken).filename, fileContent);
                //Once file is stored, send acknowledgement to controller
                this.dStore.sendAckToController(Protocol.STORE_ACK_TOKEN + " " + ((StoreToken)reqToken).filename);
            }

        } else if (reqToken instanceof LoadDataToken) {
            //Gets data from file on Dstore and if the data retrieved successfully, data sent to client
            //If not retrieved succesfully, returns true which closes the socket with client
            String filename = ((LoadDataToken)reqToken).filename;
            byte[] fileData = this.dStore.loadDataFromFile(filename);
            if (fileData == null) {
                return true;
            } else {
                this.outData.write(fileData);
                this.outData.flush();
            }
        } else if (reqToken instanceof RebalanceStoreToken) {
            this.handleRebalance(reqToken);
        }
        return false;
    }

    private void sendToClient(String message, Token reqReceivedToken) {
        if (reqReceivedToken instanceof StoreToken) {
        }
        this.outText.println(message);
        this.outText.flush();
        DstoreLogger.getInstance().messageSent(this.socket, message);
    }


    private byte[] getFileContentFromSocket(int filesize) {
        try {
            //We set a timeout for just this reading in, so that if client does not send data then we can still
            //continue
            this.socket.setSoTimeout(this.timeout);
            byte[] bytes = this.inData.readNBytes(filesize);
            this.socket.setSoTimeout(0);
            return bytes;
        } catch (SocketException e) {
            System.out.println("--- TIMEOUT ---   Dstore (port:" + this.socket.getLocalPort() + ") timed out waiting" +
                    " for file data from client (port:" + this.socket.getPort());
            return null;
        } catch (IOException e) {
            System.out.println("### ERROR ###   Connection to client lost when Dstore expecting file data");
            return null;
        }
    }

    private void handleRebalance(Token reqToken) throws IOException {
        try {
            RebalanceStoreToken t = ((RebalanceStoreToken) reqToken);
            this.outText.println(Protocol.ACK_TOKEN);
            this.outText.flush();
            this.socket.setSoTimeout(this.timeout);
            byte[] fileData = this.inData.readNBytes(t.filesize);
            this.socket.setSoTimeout(0);
            this.dStore.storeToFile(t.filename, fileData);
        } catch (SocketTimeoutException timeout) {
            System.out.println("--- TIMEOUT ---   Dstore timed out waiting for file_content from other Dstore during rebalance");
        }
    }

}
