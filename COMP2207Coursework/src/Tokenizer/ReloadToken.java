package Tokenizer;

public class ReloadToken extends Token{
    public String filename;

    public ReloadToken(String req, String filename) {
        this.req = req;
        this.filename = filename;
    }
}
