package Tokenizer;

public class StoreAckToken extends Token{
    public String filename;

    public StoreAckToken(String req, String filename) {
        this.req = req;
        this.filename = filename;
    }
}

