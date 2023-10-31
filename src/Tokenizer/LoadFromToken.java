package Tokenizer;

public class LoadFromToken extends Token{
    public int port;
    public int filesize;

    public LoadFromToken(String req, int port, int filesize) {
        this.req = req;
        this.port = port;
        this.filesize = filesize;
    }
}
