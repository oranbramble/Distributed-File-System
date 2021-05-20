package DStore;

import java.io.*;
import java.net.Socket;
import ConnectionParent.*;

public class DstoreClientConnection extends ConnectionParent{

    public DstoreClientConnection(Socket s) throws IOException {
        super(s);
    }
}
