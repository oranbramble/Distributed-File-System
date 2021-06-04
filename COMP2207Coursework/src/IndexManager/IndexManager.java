package IndexManager;

import Tokenizer.RemoveAckToken;
import Tokenizer.StoreAckToken;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;


public class IndexManager {

    private volatile ConcurrentHashMap<String, ArrayList<Integer>> expectedStoreAcksMap;
    private volatile ConcurrentHashMap<String, ArrayList<Integer>> expectedRemoveAcksMap;
    private volatile ConcurrentHashMap<String, File.State> fileStates;
    private volatile ConcurrentHashMap<String, ArrayList<Integer>> fileToDstoreMap;


    public IndexManager() {
        this.fileStates = new ConcurrentHashMap<>();
        this.expectedStoreAcksMap = new ConcurrentHashMap<>();
        this.expectedRemoveAcksMap = new ConcurrentHashMap<>();
        this.fileToDstoreMap = new ConcurrentHashMap<>();
    }

    public File.State getFileState(String filename) {
        return this.fileStates.get(filename);
    }

    /**
     * Method which gets all files that are fully stored on the system
     * They can be in the process of being removed or loaded, but cannot be in the process of being stored
     * @return List of files that are fully stored on system
     */
    public ArrayList<String> getStoredFiles() {
        ArrayList<String> storedFiles = new ArrayList<>();
        for (String file : this.fileStates.keySet()) {
            if (this.fileStates.get(file) != File.State.STORE_IN_PROGRESS) {
                storedFiles.add(file);
            }
        }
        return storedFiles;
    }

    /**
     * Method which gets all Dstore ports which are storing the filename
     * @param filename: filename which we want to get Dstores for
     * @return List of all Dstore ports
     */
    public ArrayList<Integer> getDstoresStoringFile(String filename) {
        return this.fileToDstoreMap.get(filename);
    }

    public synchronized void changeState(String filename, File.State s) {
        this.fileStates.put(filename, s);

    }

    public synchronized boolean startRemoving(String filename) {
        if (this.fileStates.containsKey(filename)) {
            if (this.fileStates.get(filename) == File.State.AVAILABLE) {
                this.fileStates.put(filename, File.State.REMOVE_IN_PROGRESS);
                return true;
            }
        }
        return false;
    }

    /**
     * Method to start the storing process of a file by updating its index value to STORE_IN_PROGRESS
     * @param filename: Name of file we want to store
     * @return true if file does not already exist, false if it does
     */
    public synchronized boolean startStoring(String filename) {
        if (this.fileStates.containsKey(filename)) {
           return false;
        } else {
            this.fileStates.put(filename, File.State.STORE_IN_PROGRESS);
            return true;
        }
    }

    /**
     * Method that removes a filename and its state from the index
     * @param filename: Filename to remove from the index
     */
    public synchronized void removeFileFromIndex(String filename) {
        if (this.fileStates.containsKey(filename)) {
            this.fileStates.remove(filename);
        }
    }

    /**
     * Method to remove a Dstore from the all the indexes
     * @param port
     */
    public void removeDstore(Integer port) {
        for (ArrayList<Integer> ports : this.fileToDstoreMap.values()) {
            ports.remove(port);
        }
    }

    public void addStoreAcksForFile(String filename, ArrayList<Integer> ports) {
        //We do this roundabout method of adding the list of ports to the map so that the array list passed in (ports)
        //is not linked by reference to the list in the map, because we remove stuff from the list in the map
        //but we want the list passed in to remain the same
        ArrayList<Integer> portsExpected = new ArrayList<>(ports);
        this.expectedStoreAcksMap.put(filename,portsExpected);
    }

    public void addRemoveAcksForFile(String filename, ArrayList<Integer> ports) {
        this.expectedRemoveAcksMap.put(filename,ports);
    }

    /**
     * Method which loops and waits to see if we receive all store acks within a timeout period.
     * @param filename
     * @param timeout
     * @param ports
     * @return
     */
    public boolean listenForStoreAcks(String filename, int timeout, ArrayList<Integer> ports) {
        double timeoutStamp = System.currentTimeMillis() + timeout;
        while (this.expectedStoreAcksMap.get(filename).size() != 0) {
            if (System.currentTimeMillis() >= timeoutStamp) {
                this.expectedStoreAcksMap.remove(filename);
                return false;
            }
        }
        this.expectedStoreAcksMap.remove(filename);
        this.changeState(filename, File.State.AVAILABLE);
        //We add to a different map here, which maps a filename to the ports/dstores it is stored on
        this.fileToDstoreMap.put(filename, ports);
        return true;
    }

    /**
     *
     * @param filename
     * @param timeout
     * @param ports
     * @return
     */
    public boolean listenForRemoveAcks(String filename, int timeout, ArrayList<Integer> ports) {
        double timeoutStamp = System.currentTimeMillis() + timeout;
        while (this.expectedRemoveAcksMap.get(filename).size() != 0) {
            if (System.currentTimeMillis() >= timeoutStamp) {
                this.expectedRemoveAcksMap.remove(filename);
                return false;
            }
        }
        this.expectedRemoveAcksMap.remove(filename);
        this.fileStates.remove(filename);
        this.fileToDstoreMap.remove(filename);
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
        if (this.expectedStoreAcksMap.containsKey(ackToken.filename)) {
            if (this.expectedStoreAcksMap.get(ackToken.filename).contains(portOfDstore)) {
                this.expectedStoreAcksMap.get(ackToken.filename).remove(portOfDstore);
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    public synchronized boolean removeAckReceived(RemoveAckToken ackToken, Integer portOfDstore) {
     //  System.out.println("PORTS EXP : " + this.expectedRemoveAcksMap);
        if (this.expectedRemoveAcksMap.containsKey(ackToken.filename)) {
            if (this.expectedRemoveAcksMap.get(ackToken.filename).contains(portOfDstore)) {
                this.expectedRemoveAcksMap.get(ackToken.filename).remove(portOfDstore);
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }
}
