package Client;

import java.io.IOException;

public class ClientMain {

    public static void main(String[] args) throws InterruptedException, IOException {
        new Client().run("STORE test1.txt 28", args[0]);
        String[] commands = {"REMOVE test1.txt", "REMOVE test1.txt", "REMOVE test1.txt"};
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
        Thread.sleep(3000);
        new Client().run("LIST", args[0]);
        /*
        Thread.sleep(3000);
        new Client().run("REMOVE test1.txt", args[0]);
        Thread.sleep(3000);
        new Client().run("LIST", args[0]);
         */
    }
}
