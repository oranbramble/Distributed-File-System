package Controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.stream.Stream;
import Tokenizer.*;


public class Controller {

    private int cPort;
    private int R;
    private int timeout;
    private int rebalancePeriod;
    //Stores names of files for index/concurrency
    private Map<String,Enumeration> files;
    private ArrayList<ControllerToDStoreConnection> dStores;
    private ArrayList<ControllerClientConnection> clients;

    public Controller(int cPort, int R, int timeout, int rebalancePeriod) {
        this.cPort = cPort;
        this.R = R;
        this.timeout = timeout;
        this.rebalancePeriod = rebalancePeriod;
        this.files = Collections.synchronizedMap(new HashMap<>());
        this.dStores = new ArrayList<>();
    }

    public void startListening() throws IOException {
        ServerSocket listener = new ServerSocket(this.cPort);
        //First R connections are DStores
        while (true) {
            //How to differentiate between client and DStore?
            Socket connection = listener.accept();
            this.startConnection(connection);
        }
    }

    private void startConnection(Socket s){
        new Thread(() -> {
            BufferedReader in = null;
            Tokenizer t = new Tokenizer();
            String line = null;

            try {
                in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                line = in.readLine();
                Token token = t.getToken(line);
                //We tokenize the first command received. IF it is a JOIN command, we know the connection is to
                //a DStore, if not then it must be a client
                if (token instanceof JoinToken) {
                    this.dStores.add(new ControllerToDStoreConnection(s, ((JoinToken)token).port));
                } else if (token != null) {
                    this.clients.add(new ControllerClientConnection(s, token));
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }).start();
    }

    /** Args layout:
     *  args[0] = cport           -> port for controller to listen on
     *  args[1] = R               -> Replication factor for files
     *  args[2] = timeout         -> How long connection held with client/DStore
     *  args[3] = rebalancePeriod -> How long between rebalance operations
     */
    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Incorrect args structure");
            return;
        }
        Integer[] i = Stream.of(args).map(Integer::valueOf).toArray(Integer[]::new);
        try {
            new Controller(i[0], i[1], i[2], i[3]).startListening();
        } catch (IOException e) {
            System.out.println("### ERROR ###  " + e);
        }
    }
}
