package IndexManager;

import Tokenizer.*;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

public class IndexManager {

    private volatile ConcurrentHashMap<String, ArrayList<Integer>> expectedStoreAcksMap;
    private volatile ConcurrentHashMap<String, ArrayList<Integer>> expectedRemoveAcksMap;
    private volatile ConcurrentHashMap<String, DstoreFile> files;
    //  private volatile ConcurrentHashMap<String, ArrayList<Integer>> fileToDstoreMap;


    public IndexManager() {
        this.files = new ConcurrentHashMap<>();
        this.expectedStoreAcksMap = new ConcurrentHashMap<>();
        this.expectedRemoveAcksMap = new ConcurrentHashMap<>();
    }

    public DstoreFile getFile(String filename) {
        return this.files.get(filename);
    }

    public void addDstoreForFile(String filename, Integer dstore) {
        DstoreFile fileToUpdate = this.files.get(filename);
        if (fileToUpdate != null) {
            fileToUpdate.getDstoresStoredOn().add(dstore);
        }
    }

    public void removeDstoreForFile(String filename, Integer dstore) {
        DstoreFile fileToUpdate = this.files.get(filename);
        if (fileToUpdate != null) {
            fileToUpdate.getDstoresStoredOn().remove(Integer.valueOf(dstore));
        }
    }

    public void addFile(String filename, int filesize, DstoreFile.State state, ArrayList<Integer> dstoresStoredOn) {
        DstoreFile fileToAdd = new DstoreFile(filename, filesize, state);
        fileToAdd.setDstoresStoredOn(dstoresStoredOn);
        this.files.put(filename, fileToAdd);
    }

    public ArrayList<DstoreFile> getFileObjects() {
        return new ArrayList<>(this.files.values());
    }

    public boolean checkIfAllAvailable() {
        boolean ifAllAvailable = true;
        for (DstoreFile file : this.files.values()) {
            if (file.getState() != DstoreFile.State.AVAILABLE) {
                ifAllAvailable = false;
                break;
            }
        }
        return ifAllAvailable;
    }

    /**
     * Method which gets all files that are fully stored on the system
     * They can be in the process of being loaded, but cannot be in the process of being stored or removed
     * @return List of files that are fully stored on system
     */
    public ArrayList<String> getStoredFilenames() {
        ArrayList<String> storedFiles = new ArrayList<>();
        for (String file : this.files.keySet()) {
            if (this.files.get(file).getState() == DstoreFile.State.AVAILABLE) {
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
        try {
            return this.files.get(filename).getDstoresStoredOn();
        } catch (NullPointerException e) {
            return new ArrayList<>();
        }
    }

    public synchronized void changeState(String filename, DstoreFile.State s) {
        try {
            this.files.get(filename).setState(s);
        } catch (NullPointerException ignored) {
            System.out.println("### ERROR ###   Could not change state of file :" + filename);
        }
    }

    public synchronized boolean startRemoving(String filename) {
        if (this.files.containsKey(filename)) {
            if (this.files.get(filename).getState() == DstoreFile.State.AVAILABLE) {
                this.files.get(filename).setState(DstoreFile.State.REMOVE_IN_PROGRESS);
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
    public synchronized boolean startStoring(String filename, int filesize) {
        if (this.files.containsKey(filename)) {
            return false;
        } else {
            this.files.put(filename, new DstoreFile(filename, filesize, DstoreFile.State.STORE_IN_PROGRESS));
            return true;
        }
    }

    /**
     * Method that removes a filename and its state from the index
     * @param filename: Filename to remove from the index
     */
    public synchronized void removeFileFromIndex(String filename) {
        if (this.files.containsKey(filename)) {
            this.files.remove(filename);
        }
    }

    /**
     * Method to remove a Dstore from the all the indexes
     * @param port
     */
    public void removeDstore(Integer port) {
        for (DstoreFile file : this.files.values()) {
            file.getDstoresStoredOn().remove(port);
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
                System.out.println("--- TIMEOUT ---   Controller timed out waiting for STORE_ACKs");
                this.expectedStoreAcksMap.remove(filename);
                this.files.remove(filename);
                return false;
            }
        }
        this.expectedStoreAcksMap.remove(filename);
        this.changeState(filename, DstoreFile.State.AVAILABLE);
        //We add to a different map here, which maps a filename to the ports/dstores it is stored on
        this.files.get(filename).setDstoresStoredOn(ports);
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
                System.out.println("--- TIMEOUT ---   Controller timed out waiting for REMOVE_ACKs");
                break;
            }
        }
        this.expectedRemoveAcksMap.remove(filename);
        this.files.remove(filename);
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
