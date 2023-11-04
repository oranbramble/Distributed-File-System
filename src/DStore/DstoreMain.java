package DStore;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

public class DstoreMain {

    // DstoreMain cport timeout N
    public static void main(String[] args) throws IOException {
        int cport = Integer.parseInt(args[0]);
        int timeout = Integer.parseInt(args[1]);
        int N = Integer.parseInt(args[2]);

        for (int x = 1; x <= N; x++) {
            AtomicInteger a = new AtomicInteger(x);
            new Thread( () -> {
                try {
                    new Dstore(500 + a.get(), cport, timeout, "Store" + a.get());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
        }
    }
}
