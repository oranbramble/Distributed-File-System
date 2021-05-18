import java.io.*;
import java.net.Socket;

public class ConnectionParent {

    private Socket socket;
    private PrintWriter outText;
    private BufferedReader inText;
    private OutputStream outData;
    private InputStream inData;

    public ConnectionParent(Socket s) throws IOException {
        this.socket = s;
        this.outText = new PrintWriter(s.getOutputStream());
        this.inText = new BufferedReader(new InputStreamReader(s.getInputStream()));
        this.outData = new BufferedOutputStream(s.getOutputStream());
        this.inData = new BufferedInputStream(s.getInputStream());
    }
}
