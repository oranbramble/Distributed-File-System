package Tokenizer;

import java.util.ArrayList;

public class FileToSend {

    public String filename;
    public int numberOfDStores;
    public ArrayList<Integer> dStores;

    public FileToSend(String filename, int numberOfDStores, ArrayList<Integer> dStores) {
        this.filename = filename;
        this.numberOfDStores = numberOfDStores;
        this.dStores = dStores;
    }
}
