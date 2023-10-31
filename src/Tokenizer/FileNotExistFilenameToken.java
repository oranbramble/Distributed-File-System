package Tokenizer;

public class FileNotExistFilenameToken extends Token{
    public String filename;

    public FileNotExistFilenameToken(String req, String filename) {
        this.req = req;
        this.filename = filename;
    }
}
