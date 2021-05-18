package Tokenizer;

/**
 * Syntax: JOIN
 */
public class JoinToken extends Token {
    int port;
    JoinToken(String command, int port) {
        this.command = command;
        this.port = port;
    }
}
