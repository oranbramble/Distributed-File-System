package Tokenizer;

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
            //Originally nulljava
            return null;
        String firstToken = sTokenizer.nextToken();
        if (firstToken.equals("JOIN")) {
            if (sTokenizer.hasMoreTokens())
                return new JoinToken(command, Integer.parseInt(sTokenizer.nextToken()));
            else
                return null;
        }
        return null;
    }
}








