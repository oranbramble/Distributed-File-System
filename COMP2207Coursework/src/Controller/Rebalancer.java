package Controller;

import DStore.Dstore;
import IndexManager.DstoreFile;
import Loggers.Protocol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Rebalancer {

    private int timeout;
    //Maps Dstore port to all the files stored on that port
    private HashMap<Integer, ArrayList<String>> filesOnDstore;
    //Maps Dstore port to its instruction it is going to be sent
    private HashMap<Integer, RebalanceInstruction> DstoreInstruction;
    //Maps files (that are stored on more than R Dstores) to number of times they should be removed
    private HashMap<DstoreFile, Integer> filesToRemove;
    //Maps files (that are stored on less than R Dstores) to number of times they should be stored
    private HashMap<DstoreFile, Integer> filesToStore;
    //
    private ArrayList<Integer> listsExpected;

    public Rebalancer(int timeout) {
        this.timeout = timeout;
        this.filesOnDstore = new HashMap<>();
        this.DstoreInstruction = new HashMap<>();
        this.filesToRemove = new HashMap<>();
        this.filesToStore = new HashMap<>();
        this.listsExpected = new ArrayList<>();
    }

    public void rebalance(ArrayList<DstoreFile> files, Map<Integer, ControllerToDStoreConnection> dstoreConnectionMap) {
        this.getFilesOnDstores(dstoreConnectionMap);
    }

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
        System.out.println(this.filesOnDstore.toString());
    }



    public void listReceived(ArrayList<String> fileList, Integer portReceivedFrom) {
        if (this.filesOnDstore.containsKey(portReceivedFrom)) {
            System.out.println("### ERROR ###   Rebalance listing: Multiple lists received from same port");
        } else {
            //Adds list of files to map and removes the port from list of ports we expect a list back from
            this.filesOnDstore.put(portReceivedFrom, fileList);
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
