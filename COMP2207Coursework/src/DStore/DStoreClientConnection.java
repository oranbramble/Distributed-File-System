package DStore;

import java.io.*;
import java.net.Socket;
import ConnectionParent.*;

public class DStoreClientConnection extends ConnectionParent{

    public DStoreClientConnection(Socket s) throws IOException {
        super(s);
    }
}
