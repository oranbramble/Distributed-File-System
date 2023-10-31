package ConnectionParent;

import Tokenizer.Tokenizer;

import java.io.*;
import java.net.Socket;

public class ConnectionParent extends Thread{

    protected Socket socket;
    protected PrintWriter outText;
    protected BufferedReader inText;
    protected OutputStream outData;
    protected InputStream inData;

    public ConnectionParent(Socket s) throws IOException {
        this.socket = s;
        this.outText = new PrintWriter(new BufferedOutputStream(s.getOutputStream()));
        this.inText = new BufferedReader(new InputStreamReader(s.getInputStream()));
        this.outData = new BufferedOutputStream(s.getOutputStream());
        this.inData = new BufferedInputStream(s.getInputStream());
    }
}
