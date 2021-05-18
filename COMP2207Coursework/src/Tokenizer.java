import java.util.StringTokenizer;

/**
 * A scanner and parser for requests.
 */

class Tokenizer {

    public Tokenizer() { ; }

    /**
     * Parses requests.
     */
    Token getToken(String command) {
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

/**
 * The Token Prototype.
 */
abstract class Token {
    String command;
}

/**
 * Syntax: JOIN &lt;name&gt;
 */
class JoinToken extends Token {
    int port;
    JoinToken(String command, int port) {
        this.command = command;
        this.port = port;
    }
}




