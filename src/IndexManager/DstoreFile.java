package IndexManager;

import java.util.ArrayList;

public class DstoreFile {

    private final String filename;
    private final int filesize;
    private State state;
    private ArrayList<Integer> DstoresStoredOn;

    public DstoreFile(String filename, int filesize, State state) {
        this.filename = filename;
        this.filesize = filesize;
        this.state = state;
        this.DstoresStoredOn = new ArrayList<>();
    }

    public String getFilename() {
        return this.filename;
    }

    public int getFilesize() {
        return this.filesize;
    }

    public State getState() {
        return this.state;
    }

    public void setState(State state) {
        this.state = state;
    }

    public ArrayList<Integer> getDstoresStoredOn() {
        return this.DstoresStoredOn;
    }

    public void setDstoresStoredOn(ArrayList<Integer> dstoresStoredOn) {
        this.DstoresStoredOn = dstoresStoredOn;
    }

    public enum State {
        AVAILABLE,
        STORE_IN_PROGRESS,
        REMOVE_IN_PROGRESS
    }

}
