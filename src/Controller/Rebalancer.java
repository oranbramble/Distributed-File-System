package Controller;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import Loggers.Protocol;
import IndexManager.*;


public class Rebalancer {

    private final int timeout;
    //Maps Dstore port to all the files stored on that port
    private final ConcurrentHashMap<Integer, ArrayList<String>> filesOnDstore;
    //Maps Dstore port to its instruction it is going to be sent
    private final ConcurrentHashMap<Integer, RebalanceInstruction> dstoreInstruction;
    //Maps files (that are stored on more than R Dstores) to number of times they should be removed
    private final ConcurrentHashMap<DstoreFile, Integer> filesToRemove;
    //Maps files (that are stored on less than R Dstores) to number of times they should be stored
    private final ConcurrentHashMap<DstoreFile, Integer> filesToStore;
    // List of Dstore ports we expect a return LIST message from
    private final ArrayList<Integer> listsExpected;
    // Object which manages the files on the system
    private final IndexManager filesIndex;
    //Maps from Dstore port to the list of files we want to store on that Dstore during rebalance
    private final HashMap<Integer, ArrayList<DstoreFile>> filesNeededByDstore;
    // Maps from a filename (which exists on the file index) to if that file is actually stored on the Dstores
    private final HashMap<String, Boolean> ifFileStoredMap;

    public Rebalancer(int timeout, IndexManager fileIndex) {
        this.timeout = timeout;
        this.filesOnDstore = new ConcurrentHashMap<>();
        this.dstoreInstruction = new ConcurrentHashMap<>();
        this.filesToRemove = new ConcurrentHashMap<>();
        this.filesToStore = new ConcurrentHashMap<>();
        this.listsExpected = new ArrayList<>();
        this.filesIndex = fileIndex;
        this.filesNeededByDstore = new HashMap<>();
        this.ifFileStoredMap = new HashMap<>();
    }

    /**
     * Method to perform rebalance logic and return a list of instructions to send to Dstores to carry out this
     * rebalance logic.
     * @param dstoreConnectionMap : map of Dstore port numbers to Controller to Dstore connection objects
     * @param R : Replication factor for system
     * @return Map of Dstore port number to a RebalanceInstruction object which contains the information to send
     *          to the Dstore in order to carry out the rebalancing.
     */
    public ConcurrentHashMap<Integer, RebalanceInstruction> rebalance(Map<Integer, ControllerToDStoreConnection> dstoreConnectionMap, int R) {
        // Creates copy of list of all files on fileIndex, and assumes there are not actually stored on Dstores
        for (String filename : this.filesIndex.getStoredFilenames()) {
            this.ifFileStoredMap.put(filename, false);
        }
        // Checks if files stored on Dstores match those on the fileIndex
        boolean success = this.getFilesOnDstores(dstoreConnectionMap);
        // If getting file lists for files on Dstores timed out, return null to represent failure of rebalance
        if (!success) {
            return null;
        }
        int numberOfFiles = this.filesIndex.getFileObjects().size();
        // Generates maximum and minimum number of files allowed on each Dstore
        // Minimum = floor = round down ((R * number of files on system) / number of Dstores)
        // Maximum = ceil = round up ((R * number of files on system) / number of Dstores)
        double ceil = this.getCeil(numberOfFiles, R, dstoreConnectionMap.size());
        double floor = this.getFloor(numberOfFiles, R, dstoreConnectionMap.size());
        //Finds which files need to be stored more (where there's less than R of them) and which
        //files need to be removed somewhere (where there's more than R of them)
        for (DstoreFile f : this.filesIndex.getFileObjects()) {
            if (f.getDstoresStoredOn().size() > R) {
                this.filesToRemove.put(f, f.getDstoresStoredOn().size() - R);
            } else if (f.getDstoresStoredOn().size() < R) {
                this.filesToStore.put(f, R - f.getDstoresStoredOn().size());
            }
        }

        this.getAllFilesNeededForDstores(ceil);
        this.createStoreFileInstructions();
        this.createRemoveFileInstructions(floor);
        this.rebalanceFiles(ceil,floor);


        return this.dstoreInstruction;

    }

    /*
    GENERATING STORE INSTRUCTIONS FOR FILES STORED LESS THAN R TIMES
     */

    private void createStoreFileInstructions() {
        //DEEP COPYING filesOnDStore
        HashMap<Integer, ArrayList<String>> filesOnDstoreDummy = new HashMap<>();
        for (Map.Entry<Integer, ArrayList<String>> entry : this.filesOnDstore.entrySet()) {
            filesOnDstoreDummy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }

        for (Integer dstoreToStoreOn : this.filesNeededByDstore.keySet()) {
            for (DstoreFile fileNeeded : this.filesNeededByDstore.get(dstoreToStoreOn)) {
                for (Integer dstoreToSendFile : this.filesOnDstore.keySet()) {
                    ArrayList<String> filesOnDstoreSending = this.filesOnDstore.get(dstoreToSendFile);

                    if (filesOnDstoreSending.contains(fileNeeded.getFilename())) {
                        RebalanceInstruction ins = this.dstoreInstruction.get(dstoreToSendFile);
                        ins.addFileToSend(fileNeeded.getFilename(),dstoreToStoreOn);

                        filesOnDstoreDummy.get(dstoreToStoreOn).add(fileNeeded.getFilename());
                        this.filesIndex.addDstoreForFile(fileNeeded.getFilename(), dstoreToStoreOn);
                        break;
                    }
                }
            }
        }
        //COPYING BACK
        this.filesOnDstore.clear();
        for (Map.Entry<Integer, ArrayList<String>> entry : filesOnDstoreDummy.entrySet()) {
            this.filesOnDstore.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
    }

    private void getAllFilesNeededForDstores(double ceil) {
        LinkedHashMap<Integer, ArrayList<String>> sortedFilesOnDstore = sortDstores(false, this.filesOnDstore);
        int dstoreCounter = 0;
        boolean ifFileStored;

        while (this.filesToStore.size() > 0 && sortedFilesOnDstore.size() > 0) {
            //Gets the least full dstore from the sorted list of dstores
            Integer dstore = (new ArrayList<>(sortedFilesOnDstore.keySet())).get(dstoreCounter);
            ArrayList<String> allFilesOnDstore = sortedFilesOnDstore.get(dstore);
            DstoreFile file = (new ArrayList<>(this.filesToStore.keySet())).get(0);

            boolean ifFileStoredEnough = false;
            //If this file still has to be stored to Dstores
            if (this.filesToStore.get(file) > 0) {
                //Checks if file should be stored to Dstore
                ifFileStored = this.storeFile(dstore, file, ceil, allFilesOnDstore, sortedFilesOnDstore);
            } else {
                //If file no longer needs to be stored, remove it from the list of files that need to be stored
                //Set ifFileStored to true because we do not want to increase the counter
                this.filesToStore.remove(file);
                ifFileStored = true;
                ifFileStoredEnough = true;
            }
            //If file has been stored, we re-sort the Dstore list to see which one now has the least files and reset the counter
            //If not, we increase the counter by one so that we are looking at the next Dstore in the list
            if (ifFileStored && !ifFileStoredEnough) {
                sortedFilesOnDstore = sortDstores(false, sortedFilesOnDstore);
                dstoreCounter = 0;
                this.filesNeededByDstore.get(dstore).add(file);
            } else if (ifFileStoredEnough) {
                sortedFilesOnDstore = sortDstores(false, sortedFilesOnDstore);
                dstoreCounter = 0;
            } else {
                dstoreCounter += 1;
            }

        }
    }

    private boolean storeFile(Integer dstore, DstoreFile file, double ceil, ArrayList<String> allFilesOnDstore, LinkedHashMap<Integer, ArrayList<String>> sortedFilesOnDstore ) {
        //Little error check to reduce exceptions thrown
        if (allFilesOnDstore != null) {
            //If Dstore still has less than 'ceil' files
            if (allFilesOnDstore.size() < ceil) {
                //If Dstore does not already contain the file
                if (!allFilesOnDstore.contains(file.getFilename())) {
                    //Add to the Dstore instruction to remove the file
                    if (this.dstoreInstruction.get(dstore) != null) {
                        //Subtract one from the number of times the file has to be stored
                        this.filesToStore.put(file, this.filesToStore.get(file) - 1);
                        sortedFilesOnDstore.get(dstore).add(file.getFilename());
                        return true;
                    }
                }
            }
        }
        return false;
    }



    /*
    GENERATING REMOVE INSTRUCTIONS FOR FILES STORED MORE THAN R TIMES
     */


    private void createRemoveFileInstructions(double floor) {
        //Sorts Dstore based on how many files they contain, so that we check the Dstores with most files first
        LinkedHashMap<Integer, ArrayList<String>> sortedFilesOnDstore = sortDstores(true, this.filesOnDstore);
        boolean ifFileRemoved;
        int dstoreCounter = 0;
        while (this.filesToRemove.size() > 0 && sortedFilesOnDstore.size() > 0) {
            //Gets the most full dstore from the sorted list of dstores
            Integer dstore = (new ArrayList<>(sortedFilesOnDstore.keySet())).get(dstoreCounter);
            ArrayList<String> allFilesOnDstore = sortedFilesOnDstore.get(dstore);
            DstoreFile file = (new ArrayList<>(this.filesToRemove.keySet())).get(0);

            //If this file still has to be removed from Dstores
            if (this.filesToRemove.get(file) > 0) {
                //Checks if file should be removed from Dstore, and removes it if it should be.
                ifFileRemoved = this.removeFile(dstore, file, floor, allFilesOnDstore);
            } else {
                //If file no longer needs to be removed, remove it from the list of files that need to be removed
                //Set ifFileRemoved to true because we do not want to increase the counter
                this.filesToRemove.remove(file);
                ifFileRemoved = true;
            }
            //If file has been removed, we re-sort the Dstore list to see which one now has the most files and reset the counter
            //If not, we increase the counter by one so that we are looking at the next Dstore in the list
            if (ifFileRemoved) {
                sortedFilesOnDstore = sortDstores(true, this.filesOnDstore);
                dstoreCounter = 0;
            } else {
                dstoreCounter += 1;
            }
        }
    }

    /**
     * Method which checks if a file should be removed from a specific Dstore during rebalance, based on if it is
     * allowed by the rabalance rules (does the Dstore stay above the floor level of files?)
     * It also updates the indexes assuming the file will actually be removed later in the operation
     * @param dstore: Port number of the Dstore we are checking
     * @param file: File object that we may be removing
     * @param floor: minimum number of files that should be on a Dstore
     * @param allFilesOnDstore: List of all files currently stored on the Dstore
     * @return boolean: true if file removed, false if not
     */
    private boolean removeFile(Integer dstore, DstoreFile file, double floor, ArrayList<String> allFilesOnDstore) {
        //Little error check to reduce exceptions thrown
        if (allFilesOnDstore != null) {
            //If Dstore still has more than 'floor' files

            if (allFilesOnDstore.size() > floor) {
                //If Dstore contains the file
                if (allFilesOnDstore.contains(file.getFilename())) {
                    //Add to the Dstore instruction to remove the file
                    if (this.dstoreInstruction.get(dstore) != null) {
                        //Adds instruction to remove the file from the specific Dstore
                        this.dstoreInstruction.get(dstore).addFileToRemove(file.getFilename());
                        //Assumes file will be removed on that Dstore, so removes it from both indexes
                        this.filesOnDstore.get(dstore).remove(file.getFilename());
                        this.filesIndex.removeDstoreForFile(file.getFilename(), dstore);
                        //Subtract one from the number of times the file has to be removed
                        this.filesToRemove.put(file, this.filesToRemove.get(file) - 1);
                        //If we remove a file, set this to true and reset the counter
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /*
    BALANCING DSTORES WHEN ALL FILES ARE STORED R TIMES
     */

    private void rebalanceFiles(double ceil, double floor) {
        //GENERATES LISTS OF DSTORES THAT NEED FILES AND NEED TO LOSE FILES
        //Each inner list is a 2 length list where index 0 is dstore port and index 1 is number of files need to remove/add
        ArrayList<ArrayList<Integer>> dstoresNeedingFiles = new ArrayList<>();
        ArrayList<ArrayList<Integer>>  dstoresLosingFiles = new  ArrayList<>();
        for (Integer dstore : this.filesOnDstore.keySet()) {
            if (this.filesOnDstore.get(dstore).size() > floor) {
                ArrayList<Integer> dstoreToNumberOfFilesToRemove = new ArrayList<>(Arrays.asList(dstore, (int) (this.filesOnDstore.get(dstore).size() - floor)));
                dstoresLosingFiles.add(dstoreToNumberOfFilesToRemove);
            } else if (this.filesOnDstore.get(dstore).size() < ceil) {
                ArrayList<Integer>  dstoreToNumberOfFilesNeeded = new ArrayList<>(Arrays.asList(dstore, (int) (ceil - this.filesOnDstore.get(dstore).size())));
                dstoresNeedingFiles.add(dstoreToNumberOfFilesNeeded);
            }
        }

        while (dstoresNeedingFiles.size() != 0 && dstoresLosingFiles.size() != 0 && !(this.checkIfBalanced(ceil, floor))) {
            dstoresNeedingFiles = this.sortDstoreLists(dstoresNeedingFiles);
            ArrayList<Integer> dstoreThatNeedsFiles = dstoresNeedingFiles.get(0);
            if (dstoreThatNeedsFiles.get(1) != 0) {
                int counter = 0;
                while (counter <= dstoresLosingFiles.size()- 1) {
                    dstoresLosingFiles = this.sortDstoreLists(dstoresLosingFiles);
                    ArrayList<Integer> dstoreRemovingFilesFrom = dstoresLosingFiles.get(0);
                    if (dstoreRemovingFilesFrom.get(1) != 0) {
                        ArrayList<String> filesToBeSent = new ArrayList<>(this.filesOnDstore.get(dstoreRemovingFilesFrom.get(0)));
                        for (String filename : filesToBeSent) {
                            if (!this.filesOnDstore.get(dstoreThatNeedsFiles.get(0)).contains(filename)) {
                                if (dstoreRemovingFilesFrom.get(1) == 0 || dstoreThatNeedsFiles.get(1) == 0) {
                                    break;
                                }
                                this.addMoveFileInstruction(dstoreThatNeedsFiles.get(0), dstoreRemovingFilesFrom.get(0), filename);

                                Integer numberOfFilesToAdd = dstoreThatNeedsFiles.get(1) - 1;
                                dstoreThatNeedsFiles.remove(1);
                                dstoreThatNeedsFiles.add(numberOfFilesToAdd);

                                Integer numberOfFilesToRemove = dstoreRemovingFilesFrom.get(1) - 1;
                                dstoreRemovingFilesFrom.remove(1);
                                dstoreRemovingFilesFrom.add(numberOfFilesToRemove);
                                break;
                            }
                        }
                    }
                    if (dstoreRemovingFilesFrom.get(1) == 0) {
                        dstoresLosingFiles.remove(0);
                        break;
                    } else {
                        counter += 1;
                    }
                    if (dstoreThatNeedsFiles.get(1) == 0) {
                        dstoresNeedingFiles.remove(0);
                        break;
                    }
                }
            } else {
                dstoresNeedingFiles.remove(0);
            }
        }

    }

    private ArrayList<ArrayList<Integer>> sortDstoreLists(ArrayList<ArrayList<Integer>> listToSort) {
        ArrayList<ArrayList<Integer>> sorted = new ArrayList<>();
        for (ArrayList<Integer> list1 : listToSort) {
            ArrayList<Integer> smallest = list1;
            for (ArrayList<Integer> list2 : listToSort) {
                if (sorted.contains(smallest)) {
                    smallest = list2;
                } else if (smallest.get(1) < list2.get(1)) {
                    smallest = list2;
                }
            }
            sorted.add(smallest);
        }
        return sorted;
    }

    private boolean checkIfBalanced(double ceil, double floor) {
        boolean ifBalanced = true;
        for (Integer dstore : this.filesOnDstore.keySet()) {
            int numberOfFilesOnDstore = this.filesOnDstore.get(dstore).size();
            if (!(numberOfFilesOnDstore <= ceil && numberOfFilesOnDstore >= floor)) {
                ifBalanced = false;
            }
        }
        return ifBalanced;
    }

    private void addMoveFileInstruction(Integer dstoreNeedingFile, Integer dstoreLosingFile, String filename) {
        this.dstoreInstruction.get(dstoreLosingFile).addFileToSend(filename, dstoreNeedingFile);
        this.dstoreInstruction.get(dstoreLosingFile).addFileToRemove(filename);
        this.filesOnDstore.get(dstoreLosingFile).remove(filename);
        this.filesOnDstore.get(dstoreNeedingFile).add(filename);
        this.filesIndex.addDstoreForFile(filename, dstoreNeedingFile);
        this.filesIndex.removeDstoreForFile(filename, dstoreLosingFile);
    }

    /*
    SORTING MAP OF DSTORES TO FILES
     */

    /**
     * Method which sorts the Dstores based on how many files they store
     * They are sorted based on the boolean parameter
     * @param ifRemoving: If true, sorts from most number of files to least. If last, returns opposite
     * @return sorted map of Dstore ports to the files it stores
     */
    public static LinkedHashMap<Integer, ArrayList<String>> sortDstores(boolean ifRemoving, Map<Integer, ArrayList<String>> toSort) {
        LinkedHashMap<Integer, ArrayList<String>> sorted = new LinkedHashMap<>();
        HashMap<Integer, ArrayList<String>> filesOnEachDstore = new HashMap<>();
        //DEEP COPYING
        for (Map.Entry<Integer, ArrayList<String>> entry : toSort.entrySet()) {
            filesOnEachDstore.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        //SORTING
        List<Map.Entry<Integer, ArrayList<String>>> list = new ArrayList<>(filesOnEachDstore.entrySet());
        list.sort(Map.Entry.comparingByValue(Comparator.comparing(ArrayList::size)));
        //ASC OR DESC
        if (ifRemoving) {
            Collections.reverse(list);
        }
        //PUT SORTED ENTRIES BACK INTO MAP
        for (Map.Entry<Integer, ArrayList<String>> entry : list) {
            sorted.put(entry.getKey(), entry.getValue());
        }
        return sorted;
    }

    /*
    CEIL AND FLOOR
     */

    private double getCeil(int F, int R, int D) {
        return Math.ceil(((double) R * (double) F) / (double) D);
    }

    private double getFloor(int F, int R, int D) {
        return Math.floor(((double) R * (double) F) / (double) D);
    }


    /*
    LISTING
     */

    public boolean getFilesOnDstores(Map<Integer, ControllerToDStoreConnection> dstoreConnectionMap) {
        //Sends lists to all Dstores
        new Thread(() -> {
            for (ControllerToDStoreConnection dstoreConnection : dstoreConnectionMap.values()) {
                // Keeps list of expected responses from Dstores
                this.listsExpected.add(dstoreConnection.getDstorePort());
                // Sends LIST request to all Dstores
                dstoreConnection.sendMessageToDstore(Protocol.LIST_TOKEN);
            }
        }).start();
        // Pauses the program to allow for the listsExpected list to be filled with list of Dstores we expect
        // a return message from
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // Starts method which waits until all responses are received before moving on with the rebalancing
        return this.listenForLists();
    }

    public void listReceived(ArrayList<String> fileList, Integer portReceivedFrom) {
        if (this.filesOnDstore.containsKey(portReceivedFrom)) {
            System.out.println("### ERROR ###   Rebalance listing: Multiple lists received from same port");
        } else {
            //Adds list of files to map and removes the port from list of ports we expect a list back from
            this.filesOnDstore.put(portReceivedFrom, fileList);
            this.dstoreInstruction.put(portReceivedFrom, new RebalanceInstruction());
            this.filesNeededByDstore.put(portReceivedFrom, new ArrayList<>());
            boolean finished = this.updateFileIndex(fileList, portReceivedFrom);
            if (finished) {
                this.listsExpected.remove(portReceivedFrom);
            }
        }
    }

    /**
     * Updates the file index so that files that are in the Dstores but not in the index are added
     * Updates each file's list of Dstores of where it is stored aswell
     * E.g. If File F has Dstore D in it's list but the D's LIST command does not contain F, we remove
     *      D from the list of Dstores associated with F. (and vice versa if D not in F's list but is actually stored
     *      in D)
     * @param fileList
     * @param dstorePort
     */
    public synchronized boolean updateFileIndex(ArrayList<String> fileList, Integer dstorePort) {
        //Checks if the file is already in the file index
        for (String filename : fileList) {
            System.out.println("FLAG " + filename);
            this.ifFileStoredMap.put(filename, true);
            boolean fileNotYetStored = true;
            System.out.println(this.filesIndex.getFileObjects());
            for (DstoreFile fileStored : this.filesIndex.getFileObjects()) {
                System.out.println(fileStored.getFilename());
                System.out.println(filename);
                //If file returned from dstore list is already in our index, we know we don't have to
                //add it to the index as it is already stored
                if (fileStored.getFilename().equals(filename)) {
                    fileNotYetStored = false;
                }
                //If file stored on index has this dstore port as a location where its stored, but the
                //list from the dstore does not contain the file, then remove this dstore from the list
                //of dstores where the file is stored
                if (fileStored.getDstoresStoredOn().contains(dstorePort) && !(fileList.contains(fileStored.getFilename()))) {
                    this.filesIndex.removeDstoreForFile(fileStored.getFilename(), dstorePort);
                }
            }

            //If file is not in file index yet, we add it
            if (fileNotYetStored) {
                //NOTE : SERIOUS ERROR WITH NO SOLUTION
                //We have to add file with 0 filesize, as we have no way of knowing its size
                //Therefore, if we try to load this file in the future, it will fail
                this.filesIndex.addFile(filename, 0, DstoreFile.State.AVAILABLE, new ArrayList<>());
                this.filesIndex.addDstoreForFile(filename, dstorePort);
            } else {
                //If file exists on index and does not have Dstore port in its list of where it is stored,
                //add it to that list
                if (!this.filesIndex.getFile(filename).getDstoresStoredOn().contains(dstorePort)) {
                    this.filesIndex.addDstoreForFile(filename, dstorePort);
                }
            }
        }
        return true;
    }

    public boolean listenForLists() {
        boolean ifNotTimedOut = true;
        //Waits for all lists back from Dstores within a certain timeout period
        double timeoutStamp = System.currentTimeMillis() + this.timeout;
        while (this.listsExpected.size() > 0) {
            // If timeout, print error, raise flag and break
            if (System.currentTimeMillis() > timeoutStamp) {
                System.out.println("--- TIMEOUT ---   Rebalance listing: Process timed out waiting for Dstore lists");
                ifNotTimedOut = false;
                break;
            }
        }

        // Checks if file exists on file index but no longer exists on Dstores.
        // If this is the case, file is removed from the file index
        for (String filename : this.ifFileStoredMap.keySet()) {
            if (this.ifFileStoredMap.get(filename) == false) {
                this.filesIndex.removeFileFromIndex(filename);
            }
        }

        // Returns if timed out when waiting for LIST responses from Dstores
        return ifNotTimedOut;
    }
}
