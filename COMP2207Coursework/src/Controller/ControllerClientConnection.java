package Controller;

import ConnectionParent.ConnectionParent;
import Tokenizer.Token;
import java.io.*;
import java.net.Socket;

public class ControllerClientConnection extends ConnectionParent {

    public ControllerClientConnection(Socket s, Token t) throws IOException {
        super(s);
    }
}
