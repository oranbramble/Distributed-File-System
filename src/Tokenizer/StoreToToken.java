package Tokenizer;

import java.util.ArrayList;
import java.util.StringTokenizer;

public class StoreToToken extends Token{
    public ArrayList<Integer> ports;

    public StoreToToken(String req, StringTokenizer stringTokenizer) {
        this.req = req;
        this.ports = new ArrayList<>();
        while (stringTokenizer.hasMoreTokens()) {
            try {
                this.ports.add(Integer.parseInt(stringTokenizer.nextToken()));
            } catch(NumberFormatException ignored) {};
        }
    }
}
