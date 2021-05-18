package Tokenizer;

import Loggers.Protocol;

import java.io.File;
import java.util.StringTokenizer;

/**
 * A scanner and parser for requests.
 */

public class Tokenizer {

    public Tokenizer() { ; }

    /**
     * Parses requests.
     */
    public Token getToken(String command) {
        StringTokenizer sTokenizer = new StringTokenizer(command);
        if (!(sTokenizer.hasMoreTokens()))
            return null;
        String firstToken = sTokenizer.nextToken();

        //Matches ListTokens and FileListTokens
        if (firstToken.equals(Protocol.LIST_TOKEN)) {
            if (sTokenizer.hasMoreTokens()) {
                return new FileListToken(command, sTokenizer);
            } else {
                return new ListToken(command);
            }
        }

        //Matches StoreTokens
        if (firstToken.equals(Protocol.STORE_TOKEN)) {
            //Number of elements after command STORE must be 2
            if (sTokenizer.countTokens() == 2) {
                //If we cannot convert the second element (i.e. filesize) to an integer, must be invalid input
                //so we return a null if this fails
                try {
                    return new StoreToken(command, sTokenizer.nextToken(), Integer.parseInt(sTokenizer.nextToken()));
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
        }

        

        //Finds JoinTokens
        if (firstToken.equals(Protocol.JOIN_TOKEN)) {
            if (sTokenizer.hasMoreTokens()) {
                return new JoinToken(command, Integer.parseInt(sTokenizer.nextToken()));
            } else {
                return null;
            }
        }

        return null;
    }

    public static void main(String[] args) {
        Tokenizer t = new Tokenizer();
       // Token j = t.getToken("JOIN 4000");
       // Token l = t.getToken("LIST x y z f");
       // System.out.println(((JoinToken)j).port + "    " + l.req + ": " + ((FileListToken)l).fileList);
       // StoreToken s = (StoreToken)t.getToken("STORE a 300");
       // System.out.println(s.filename + " " + s.filesize );

    }
}








