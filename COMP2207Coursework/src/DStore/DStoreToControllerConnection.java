package DStore;

import java.io.*;
import java.net.Socket;
import ConnectionParent.*;
import Loggers.Protocol;

public class DStoreToControllerConnection extends ConnectionParent{

    private Socket socket;
    private PrintWriter outText;
    private BufferedReader inText;
    private OutputStream outData;
    private InputStream inData;

    public DStoreToControllerConnection(Socket s, int dStorePort) throws IOException {
        super(s);
        this.outText.println(Protocol.JOIN_TOKEN + " " + dStorePort);
        this.outText.flush();
    }
}
