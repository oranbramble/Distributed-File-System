package Tokenizer;

/**
 * Syntax: JOIN
 */
public class JoinToken extends Token {
    public int port;

    JoinToken(String req, int port) {
        this.req = req;
        this.port = port;
    }
}
