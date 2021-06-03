package DStore;

import Loggers.DstoreLogger;
import Loggers.Logger;
import Loggers.Protocol;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class Dstore {

    private int port;
    private int timeout;
    private File fileFolder;
    private DstoreToControllerConnection controllerConnection;

    public Dstore(int port, int cPort, int timeout, String fileFolder) throws IOException {
        this.port = port;
        this.timeout = timeout;
        this.fileFolder = new File(fileFolder);
        this.fileFolder.mkdir();

        DstoreLogger.init(Logger.LoggingType.ON_TERMINAL_ONLY, this.port);

        //Tries to join controller and set up connection with it, and then starts listening for other connections
        try {
            Socket controllerSocket = new Socket(InetAddress.getLocalHost(), cPort);
            this.joinController(controllerSocket);
            this.startListening(cPort);
        } catch (IOException e) {
            System.out.println("### DSTORE ERROR ###    TCP Connection to Controller failed");
        }
    }

    private void joinController(Socket s) throws IOException {
        this.controllerConnection = new DstoreToControllerConnection(s, this.port, this);
    }

    private void startListening(int cPort) throws IOException {
        ServerSocket listener = new ServerSocket(this.port);
        while (true) {
            Socket connection = listener.accept();
            new DstoreClientConnection(connection, this, cPort, this.timeout).start();
        }
    }

    public void storeToFile(String filename, String data)  {
        try {
            File f = new File(this.fileFolder.getPath() + "/" +filename);
            FileWriter writer = new FileWriter(f);
            writer.write(data);
            writer.flush();
        } catch (IOException e) {
            System.out.println("### ERROR ###   Cannot write data to filename " + filename);
        }
    }

    public void sendAckToController(String ack) {
        this.controllerConnection.sendAckToController(ack);
    }



    /** Args layout:
     *  args[0] = port            -> port for DStore to listen on
     *  args[1] = cPort           -> port for controller
     *  args[2] = timeout         -> How long connection held with client/DStore
     *  args[3] = fileFolder      -> Path to store files
     */
    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Incorrect args structure");
            return;
        }
        try {
            int port = Integer.parseInt(args[0]);
            int cPort = Integer.parseInt(args[1]);
            int timeout = Integer.parseInt(args[2]);
            new Dstore(port, cPort, timeout, args[3]);
        } catch (Exception e) {
            System.out.println("### ERROR ###  " + e);
        }
    }
}
