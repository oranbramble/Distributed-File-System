package IndexManager;

import Tokenizer.StoreAckToken;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;


public class IndexManager {

    private volatile ConcurrentHashMap<String, ArrayList<Integer>> filenameAckMap;
    private volatile ConcurrentHashMap<String, File.State> fileStates;
    private volatile ConcurrentHashMap<String, ArrayList<Integer>> fileToDstoreMap;


    public IndexManager() {
        this.fileStates = new ConcurrentHashMap<>();
        this.filenameAckMap = new ConcurrentHashMap<>();
        this.fileToDstoreMap = new ConcurrentHashMap<>();
    }

    public File.State getFileState(String filename) {
        return this.fileStates.get(filename);
    }

    public ArrayList<String> getAvailableFiles() {
        ArrayList<String> availableFiles = new ArrayList<>();
        for (String file : this.fileStates.keySet()) {
            if (this.fileStates.get(file) == File.State.AVAILABLE) {
                availableFiles.add(file);
            }
        }
        return availableFiles;
    }

    public void changeState(String filename, File.State s) {
        this.fileStates.put(filename, s);

    }

    public synchronized boolean addFileToIndex(String filename) {
        if (this.fileStates.containsKey(filename)) {
           return false;
        } else {
            this.fileStates.put(filename, File.State.STORE_IN_PROGRESS);
            return true;
        }
    }

    public synchronized void removeFileFromIndex(String filename) {
        if (this.fileStates.containsKey(filename)) {
            this.fileStates.remove(filename);
        }
    }

    public void removeDstore(Integer port) {
        for (ArrayList<Integer> ports : this.fileToDstoreMap.values()) {
            ports.remove(port);
        }
    }

    public void addExpectedAcksForFile(String filename, ArrayList<Integer> ports) {
        this.filenameAckMap.put(filename,ports);
    }

    /**
     * Method which loops and waits to see if we receive all acks within a timeout period.
     * @param filename
     * @param timeout
     * @param ports
     * @return
     */
    public boolean listenForAcks(String filename, int timeout, ArrayList<Integer> ports) {
        double timeoutStamp = System.currentTimeMillis() + timeout;
        while (this.filenameAckMap.get(filename).size() != 0) {
            if (System.currentTimeMillis() >= timeoutStamp) {
                this.filenameAckMap.remove(filename);
                return false;
            }
        }
        this.filenameAckMap.remove(filename);
        //We add to a different map here, which maps a filename to the ports/dstores it is stored on
        this.fileToDstoreMap.put(filename, ports);
        return true;
    }

    /**
     * Method which handles the receiving of an acknowledgment. It updates the map containing filename mapped to port
     * numbers we expect an ack on. I.e. If we receive an ack on a port we expected to receive one on for the specific
     * filename, we remove that port from the map value for that filename. Elsewhere, we check if the number of of ports
     * left to receive acks on is 0, and if it is, we have received all acks
     * @param ackToken
     * @param portOfDstore
     * @return
     */
    public synchronized boolean storeAckReceived(StoreAckToken ackToken, Integer portOfDstore) {
        if (this.filenameAckMap.containsKey(ackToken.filename)) {
            ArrayList<Integer> portsNeedingAck = this.filenameAckMap.get(ackToken.filename);
            if (portsNeedingAck.contains(portOfDstore)) {
                portsNeedingAck.remove(portOfDstore);
                this.filenameAckMap.put(ackToken.filename, portsNeedingAck);
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }
}
