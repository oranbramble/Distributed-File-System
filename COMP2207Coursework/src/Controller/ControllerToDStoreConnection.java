package Controller;

import java.io.*;
import java.net.Socket;
import ConnectionParent.*;

/**
 * Class to handle connection from controller to DStore (acts as interface between controller and DStore)
 */
public class ControllerToDStoreConnection extends ConnectionParent{

    public int dStorePort;

    public ControllerToDStoreConnection(Socket s, int port) throws IOException {
        super(s);
        this.dStorePort = port;
    }
}
