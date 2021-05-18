package Controller;

import java.io.*;
import java.net.Socket;
import ConnectionParent.*;

/**
 * Class to handle connection from controller to DStore (acts as interface between controller and DStore)
 */
public class controllerToDStoreConnection extends ConnectionParent{

    private Socket socket;
    private PrintWriter outText;
    private BufferedReader inText;
    private OutputStream outData;
    private InputStream inData;

    public controllerToDStoreConnection(Socket s) throws IOException {
        super(s);
    }
}
