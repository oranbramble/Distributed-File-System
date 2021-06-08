package Client;

import java.io.IOException;
import java.util.Scanner;

public class ClientMain {

    public static void main(String[] args) throws InterruptedException, IOException {
        String[] commands = {"STORE test1.txt 28", "STORE test2.txt 41", "LOAD test1.txt"};
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
        Thread.sleep(2000);
        new Client().run("LIST", args[0]);

        Scanner s = new Scanner(System.in);
        String[] commands2 = {"REMOVE test1.txt", "LOAD test1.txt"};
        for (String command : commands2) {
            new Thread(() -> {
                try {
                    new Client().run(command, args[0]);
                } catch (IOException e) {
                    System.out.println("ERROR");
                    e.printStackTrace();
                }
            }).start();
        }
        while (true) {
            new Client().run(s.nextLine(), args[0]);
        }



    }
}
