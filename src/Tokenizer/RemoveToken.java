package Tokenizer;

public class RemoveToken extends Token{
    public String filename;

    public RemoveToken(String req, String filename) {
        this.req = req;
        this.filename = filename;
    }
}
