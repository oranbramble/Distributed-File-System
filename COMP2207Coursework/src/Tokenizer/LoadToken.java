package Tokenizer;

public class LoadToken extends Token{
    public String filename;

    public LoadToken(String req, String filename) {
        this.req = req;
        this.filename = filename;
    }
}
