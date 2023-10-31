package Tokenizer;

public class StoreToken extends Token{
    public String filename;
    public int filesize;

    public StoreToken(String req, String filename, int filesize) {
        this.req = req;
        this.filename = filename;
        this.filesize = filesize;
    }
}
