package DStore;

import java.io.*;
import java.net.Socket;
import ConnectionParent.*;

public class dStoreToControllerConnection extends ConnectionParent{

    private Socket socket;
    private PrintWriter outText;
    private BufferedReader inText;
    private OutputStream outData;
    private InputStream inData;

    public dStoreToControllerConnection(Socket s) throws IOException {
        super(s);
        this.outText.println();
    }
}
