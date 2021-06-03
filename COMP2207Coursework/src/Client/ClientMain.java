package Client;

import java.io.IOException;
import java.util.ArrayList;

public class ClientMain {

    public static void main(String[] args) {
        String[] commands = {"STORE test.txt 16", "STORE test2.txt 33", "STORE test3.txt 226"};
        for (String command : commands) {
            new Thread(() -> {
                try {
                    new Client().run(command, args[0]);
                } catch (IOException e) {
                    System.out.println("ERROR");
                    e.printStackTrace();
                }
            }).start();
        }
    }
}
