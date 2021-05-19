package Tokenizer;

public class RebalanceStoreToken extends Token{
    public String filename;
    public int filesize;

    public RebalanceStoreToken(String req, String filename, int filesize) {
        this.req = req;
        this.filename = filename;
        this.filesize = filesize;
    }
}

