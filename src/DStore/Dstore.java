package DStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

import Loggers.DstoreLogger;
import Loggers.Logger;

public class Dstore {

    private final int port;
    private final int timeout;
    private final File fileFolder;
    private DstoreToControllerConnection controllerConnection;

    public Dstore(int port, int cPort, int timeout, String fileFolder) throws IOException {
        this.port = port;
        this.timeout = timeout;
        this.fileFolder = new File(fileFolder);
        this.fileFolder.mkdir();

        DstoreLogger.init(Logger.LoggingType.ON_FILE_AND_TERMINAL, this.port);

        //Tries to join controller and set up connection with it, and then starts listening for other connections
        try {
            Socket controllerSocket = new Socket(InetAddress.getLocalHost(), cPort);
            this.joinController(controllerSocket);
            this.startListening();
        } catch (IOException e) {
            System.out.println("### DSTORE ERROR ###    TCP Connection to Controller failed");
        }
    }

    private void joinController(Socket s) throws IOException {
        this.controllerConnection = new DstoreToControllerConnection(s, this.port, this);
    }

    private void startListening() throws IOException {
        ServerSocket listener = new ServerSocket(this.port);
        while (true) {
            Socket connection = listener.accept();
            new DstoreClientConnection(connection, this, this.timeout).start();
        }
    }

    public void storeToFile(String filename, byte[] data)  {
        try {
            File f = new File(this.fileFolder.getPath() + "/" + filename);
            FileOutputStream writer = new FileOutputStream(f);
            writer.write(data);
            writer.flush();
            writer.close();
        } catch (IOException e) {
            System.out.println("### ERROR ###   Cannot write data to filename " + filename);
        }
    }

    public boolean removeFile(String filename) {
        File f = new File(this.fileFolder.getPath() + "/" + filename);
        try {
            Files.delete(Paths.get(f.getAbsolutePath()));
            return true;
        } catch (IOException e) {
            System.out.println("### ERROR ###   File " + filename + "does not exist on Dstore (port : " + this.port + ")");
            return false;
        }
    }

    public void sendAckToController(String ack) {
        this.controllerConnection.sendMessageToController(ack);
    }

    public byte[] loadDataFromFile(String filename) {
        String path = this.fileFolder.getPath() + "/" + filename;
        File f = new File(path);
        try {
            FileInputStream r = new FileInputStream(f);
            byte[] data = new byte[(int) f.length()];
            r.read(data);
            r.close();
            return data;
        } catch (IOException e) {
            return null;
        }
    }

    public ArrayList<String> getListOfFiles() {
        String[] files = this.fileFolder.list();
        if (files != null) {
            return new ArrayList<>(Arrays.asList(files));
        } else {
            return new ArrayList<>();
        }

    }

    public int getFilesize(String filename) {
        File f = new File(this.fileFolder.getPath() + "/" + filename);
        if (f.exists()) {
            return ((int)f.length());
        } else {
            return -1;
        }
    }

    public void end() {
        System.exit(0);
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
