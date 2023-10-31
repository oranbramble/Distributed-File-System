package Client;

import java.io.IOException;
import java.util.Scanner;

public class ClientMain {

    public static void main(String[] args) {
        System.out.println("Welcome to my Distributed File System");
        System.out.println("-------------------------------------");
        System.out.println("Commands List:");
        System.out.println("STORE filename filesize");
        System.out.println("REMOVE filename");
        System.out.println("LOAD filename");
        System.out.println("LIST");
        System.out.println("-------------------------------------");
        System.out.println("--- Enter commands below (to exit type QUIT) ---");

        Scanner s = new Scanner(System.in);
        boolean end = false;
        while (!end) {
            try {
                // Gets next command
                String line = s.nextLine();
                end = line.equals("QUIT");
                if (!end) {
                    // If not quitting, run command using Client object
                    // args[0] are the arguments 'cport' and 'timeout' for Client
                    new Client().run(s.nextLine(), args[0]);
                }
            } catch (IOException e) {
                System.out.println("ERROR");
                e.printStackTrace();
            }
        }



    }
}
