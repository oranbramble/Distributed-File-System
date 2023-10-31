package Tokenizer;

public class RemoveAckToken extends Token{
    public String filename;

    public RemoveAckToken(String req, String filename) {
        this.req = req;
        this.filename = filename;
    }
}