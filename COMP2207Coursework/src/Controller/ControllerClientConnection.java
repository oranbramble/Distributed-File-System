package Controller;

import ConnectionParent.ConnectionParent;
import Tokenizer.Token;
import java.io.*;
import java.net.Socket;

public class ControllerClientConnection extends ConnectionParent {

    private Socket socket;
    private PrintWriter outText;
    private BufferedReader inText;
    private OutputStream outData;
    private InputStream inData;

    public ControllerClientConnection(Socket s, Token t) throws IOException {
        super(s);
    }
}
