
import java.io.*;
import java.net.Socket;

public class dStoreToControllerConnection extends ConnectionParent{

    private Socket socket;
    private PrintWriter outText;
    private BufferedReader inText;
    private OutputStream outData;
    private InputStream inData;

    public dStoreToControllerConnection(Socket s) throws IOException {
        super(s);
    }
}
