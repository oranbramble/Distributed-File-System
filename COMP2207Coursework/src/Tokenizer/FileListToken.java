package Tokenizer;

import java.util.ArrayList;
import java.util.StringTokenizer;

/**
 * Token representing command LIST file_list
 */
public class FileListToken extends Token{
    ArrayList<String> fileList;

    public FileListToken(String req, StringTokenizer stringTokenizer) {
        this.req = req;
        this.fileList = new ArrayList<>();
        while (stringTokenizer.hasMoreTokens()) {
            this.fileList.add(stringTokenizer.nextToken());
        }
    }
}
