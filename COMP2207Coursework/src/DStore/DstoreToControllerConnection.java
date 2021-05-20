package DStore;

import java.io.*;
import java.net.Socket;
import ConnectionParent.*;
import Loggers.DstoreLogger;
import Loggers.Protocol;

public class DstoreToControllerConnection extends ConnectionParent{

    public DstoreToControllerConnection(Socket s, int dStorePort) throws IOException {
        super(s);
        String joinMsg = Protocol.JOIN_TOKEN + " " + dStorePort;
        this.outText.println(joinMsg);
        this.outText.flush();
        DstoreLogger.getInstance().messageSent(socket, joinMsg);

        this.startListening();
    }

    private void startListening() {
        new Thread(() -> {
            while (true) {
                try {
                    String controllerCommand = this.inText.readLine();

                } catch (IOException ignored) {
                    System.out.println("### DSTORE ERROR ###   Cannot read on connection to controller");
                }


            }
        }).start();
    }
}
