package Controller;

import DStore.Dstore;
import IndexManager.DstoreFile;
import Loggers.Protocol;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Rebalancer {

    private int timeout;
    //Maps Dstore port to all the files stored on that port
    private HashMap<Integer, ArrayList<String>> filesOnDstore;
    //Maps Dstore port to its instruction it is going to be sent
    private HashMap<Integer, RebalanceInstruction> dstoreInstruction;
    //Maps files (that are stored on more than R Dstores) to number of times they should be removed
    private HashMap<DstoreFile, Integer> filesToRemove;
    //Maps files (that are stored on less than R Dstores) to number of times they should be stored
    private HashMap<DstoreFile, Integer> filesToStore;
    //
    private ArrayList<Integer> listsExpected;
    public Rebalancer(int timeout) {
        this.timeout = timeout;
        this.filesOnDstore = new HashMap<>();
        this.dstoreInstruction = new HashMap<>();
        this.filesToRemove = new HashMap<>();
        this.filesToStore = new HashMap<>();
        this.listsExpected = new ArrayList<>();
    }

    public void rebalance(ArrayList<DstoreFile> files, Map<Integer, ControllerToDStoreConnection> dstoreConnectionMap, int R) {
        this.getFilesOnDstores(dstoreConnectionMap);

        int numberOfFiles = this.getNumberOfFiles();
        double ceil = this.getCeil(numberOfFiles, R, dstoreConnectionMap.size());
        double floor = this.getFloor(numberOfFiles, R, dstoreConnectionMap.size());

        //Finds which files need to be stored more (where there's less than R of them) and which
        //files need to be removed somewhere (where there's more than R of them)
        for (DstoreFile f : files) {
            if (f.getDstoresStoredOn().size() > R) {
                this.filesToRemove.put(f, f.getDstoresStoredOn().size() - R);
            } else if (f.getDstoresStoredOn().size() < R) {
                this.filesToStore.put(f, R - f.getDstoresStoredOn().size());
            }
        }
        System.out.println(filesToRemove.toString());
        this.createRemoveFileInstructions(ceil, floor);
    }

    private void createRemoveFileInstructions(double ceil, double floor) {
        //Sorts Dstore based on how many files they contain, so that we check the Dstores with most files first
        LinkedHashMap<Integer, ArrayList<String>> sortedFilesOnDstore = sortDstores(true);
        for (Integer dstore : sortedFilesOnDstore.keySet()) {
            //If we have no more files we need to remove, we stop
            if (!this.checkMoreToRemove()) {
                break;
            }
            for (DstoreFile file : this.filesToRemove.keySet()) {
                //If Dstore still has more than 'floor' files
                if (!(sortedFilesOnDstore.get(dstore).size() <= floor)) {
                    //If Dstore contains the file
                    if (sortedFilesOnDstore.get(dstore).contains(file.getFilename())) {
                        System.out.print("MADE IT");
                        //Add to the Dstore instruction to remove the file
                        this.dstoreInstruction.get(dstore).addFileToRemove(file.getFilename());
                        //Subtract one from the number of times the file has to be removed
                        this.filesToRemove.put(file, this.filesToRemove.get(file) - 1);
                    }
                }
            }
        }
        System.out.println("INSTRUCTIONS : ");
        for (RebalanceInstruction x : this.dstoreInstruction.values()) {
            if (!x.getInstruction().equals("")) {
                System.out.print(x.getInstruction());
            }
        }
    }

    /**
     * Method which finds if any file needs to be removed more (still on more than R dstores)
     * @return
     */
    private boolean checkMoreToRemove() {
        for (DstoreFile f : this.filesToRemove.keySet()) {
            if (this.filesToRemove.get(f) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Method which sorts the Dstores based on how many files they store
     * They are sorted based on the boolean parameter
     * @param ifRemoving: If true, sorts from highest number of files to least. If last, returns opposite
     * @return sorted map of Dstore ports to the files it stores
     */
    private LinkedHashMap<Integer, ArrayList<String>> sortDstores(boolean ifRemoving) {
        LinkedHashMap<Integer, ArrayList<String>> sorted = new LinkedHashMap<>();
        ArrayList<ArrayList<String>> filesOnEachDstore = new ArrayList<>(this.filesOnDstore.values());

        //Sorts the list based on smallest inner list first, then may reverses it depending on what operation
        //we are doing
        filesOnEachDstore.sort(Comparator.comparingInt(ArrayList::size));
        if (ifRemoving) {
            Collections.reverse(filesOnEachDstore);
        }
        //Matches each list back up with its key value
        for (ArrayList<String> files : filesOnEachDstore) {
            for (Integer port : this.filesOnDstore.keySet()) {
                //MAY FAIL IF ORDER OF LISTS IS DIFFERENT
                if (this.filesOnDstore.get(port).equals(files)) {
                    sorted.put(port, files);
                }
            }
        }
        return sorted;
    }

    /*
    CEIL AND FLOOR
     */

    private int getNumberOfFiles() {
        if (this.filesOnDstore.size() != 0) {
            ArrayList<ArrayList<String>> allFilesOnAllDstores = new ArrayList<>(this.filesOnDstore.values());
            allFilesOnAllDstores.sort(Comparator.comparingInt(ArrayList::size));
            Collections.reverse(allFilesOnAllDstores);
            return allFilesOnAllDstores.get(0).size();
        }
        return 0;
    }

    private double getCeil(int F, int R, int D) {
        double fD = F;
        double rD = R;
        double dD = D;
        return Math.ceil((R * fD) / dD);
    }

    private double getFloor(int F, int R, int D) {
        double fD = F;
        double rD = R;
        double dD = D;
        return Math.floor((R * fD) / dD);
    }

    /*
    LISTING
     */

    public void getFilesOnDstores(Map<Integer,ControllerToDStoreConnection> dstoreConnectionMap) {
        //Sends lists to all Dstores
        new Thread(() -> {
            for (ControllerToDStoreConnection dstoreConnection : dstoreConnectionMap.values()) {
                dstoreConnection.sendMessageToDstore(Protocol.LIST_TOKEN);
                this.listsExpected.add(dstoreConnection.getDstorePort());
            }
        }).start();
        this.listenForLists();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void listReceived(ArrayList<String> fileList, Integer portReceivedFrom) {
        if (this.filesOnDstore.containsKey(portReceivedFrom)) {
            System.out.println("### ERROR ###   Rebalance listing: Multiple lists received from same port");
        } else {
            //Adds list of files to map and removes the port from list of ports we expect a list back from
            this.filesOnDstore.put(portReceivedFrom, fileList);
            this.dstoreInstruction.put(portReceivedFrom, new RebalanceInstruction());
            this.listsExpected.remove(portReceivedFrom);
        }
    }

    public void listenForLists() {
        //Waits for all lists back from Dstores within a certain timeout period
        double timeoutStamp = System.currentTimeMillis() + this.timeout;
        while (this.listsExpected.size() > 0) {
            if (System.currentTimeMillis() > timeoutStamp) {
                System.out.println("--- TIMEOUT ---   Rebalance listing: Process timed out waiting for Dstore lists");
                break;
            }
        }
    }
}
