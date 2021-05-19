package DStore;

import java.io.*;
import java.net.Socket;
import ConnectionParent.*;
import Loggers.Protocol;

public class DStoreToControllerConnection extends ConnectionParent{

    public DStoreToControllerConnection(Socket s, int dStorePort) throws IOException {
        super(s);
        this.outText.println(Protocol.JOIN_TOKEN + " " + dStorePort);
        this.outText.flush();
    }
}
