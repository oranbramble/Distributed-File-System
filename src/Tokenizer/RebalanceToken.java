package Tokenizer;

import java.io.File;
import java.util.ArrayList;

public class RebalanceToken extends Token{

    public int numberOfFilesToSend;
    public int numberOfFilesToRemove;
    public ArrayList<FileToSend> filesToSend;
    public ArrayList<String> filesToRemove;

    public RebalanceToken(String req, int numberOfFilesToSend, int numberOfFilesToRemove, ArrayList<FileToSend> filesToSend, ArrayList<String> filesToRemove) {
        this.req = req;
        this.numberOfFilesToSend = numberOfFilesToSend;
        this.numberOfFilesToRemove = numberOfFilesToRemove;
        this.filesToSend = filesToSend;
        this.filesToRemove = filesToRemove;
    }
}
