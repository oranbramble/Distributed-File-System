package Tokenizer;

public class LoadDataToken extends Token{
    public String filename;

    public LoadDataToken(String req, String filename) {
        this.req = req;
        this.filename = filename;
    }
}
