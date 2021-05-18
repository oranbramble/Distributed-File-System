import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.stream.Stream;

public class DStore {

    private int port;
    private int timeout;
    private String fileFolder;
    private dStoreToControllerConnection controllerConnection;

    public DStore(int port, int cPort, int timeout, String fileFolder) {
        this.port = port;
        this.timeout = timeout;
        this.fileFolder = fileFolder;
        Socket controllerSocket;
        //Sets up connection to controller, and then starts listening for client connections
        try {
            controllerSocket = new Socket("LAPTOP-VM9CS5EI", cPort);
            this.controllerConnection = new dStoreToControllerConnection(controllerSocket);
            this.startListening();
        } catch (IOException e) {
            System.out.println("### DSTORE ERROR ###    TCP Connection to Controller failed");
        }
    }

    /**
     * Method to listen for connections from clients. When connection established, new
     * dStoreClientConnection object established to handle connection
     * @throws IOException
     */
    private void startListening() throws IOException {
        ServerSocket listener = new ServerSocket(this.port);
        //First R connections are DStores
        while (true) {
            //How to differentiate between client and DStore?
            Socket connection = listener.accept();

        }
    }

    /** Args layout:
     *  args[0] = port            -> port for DStore to listen on
     *  args[1] = cPort           -> port for controller
     *  args[2] = timeout         -> How long connection held with client/DStore
     *  args[3] = fileFolder      -> Path to store files
     */
    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Incorrect args structure");
            return;
        }
        try {
            int port = Integer.parseInt(args[0]);
            int cPort = Integer.parseInt(args[1]);
            int timeout = Integer.parseInt(args[2]);
            new DStore(port, cPort, timeout, args[3]);
        } catch (Exception e) {
            System.out.println("### ERROR ###  " + e);
        }
    }
}
