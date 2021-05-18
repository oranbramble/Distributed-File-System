package DStore;

import java.io.*;
import java.net.Socket;
import ConnectionParent.*;

public class dStoreClientConnection extends ConnectionParent{

    private Socket socket;
    private PrintWriter outText;
    private BufferedReader inText;
    private OutputStream outData;
    private InputStream inData;

    public dStoreClientConnection(Socket s) throws IOException {
        super(s);
    }
}
