package IndexManager;

import java.util.ArrayList;

public class DstoreFile {

    private String filename;
    private int filesize;
    private State state;
    private ArrayList<Integer> DstoresStoredOn;

    public DstoreFile(String filename, int filesize, State state) {
        this.filename = filename;
        this.filesize = filesize;
        this.state = state;
        this.DstoresStoredOn = new ArrayList<>();
    }

    public String getFilename() {
        return filename;
    }

    public int getFilesize() {
        return filesize;
    }

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public ArrayList<Integer> getDstoresStoredOn() {
        return DstoresStoredOn;
    }

    public void setDstoresStoredOn(ArrayList<Integer> dstoresStoredOn) {
        DstoresStoredOn = dstoresStoredOn;
    }

    public enum State {
        AVAILABLE,
        STORE_IN_PROGRESS,
        REMOVE_IN_PROGRESS
    }

}
