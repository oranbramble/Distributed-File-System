package DStore;

import Loggers.DstoreLogger;
import Loggers.Logger;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class DstoreMain {

    public static void main(String[] args) throws IOException {
        DstoreLogger.init(Logger.LoggingType.ON_TERMINAL_ONLY, 5000);
        for (int x = 1; x <= 5; x++) {
            AtomicInteger a = new AtomicInteger(x);
            new Thread( () -> {
                try {
                    new Dstore(500 + a.get(), Integer.parseInt(args[0]), 5000, "Store" + a.get());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }
}
