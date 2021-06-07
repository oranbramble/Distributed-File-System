package Controller;

import Loggers.Protocol;
import java.util.ArrayList;

/**
 * Class to hold the instruction sent to a single Dstore in the rebalance operation
 * I.e. contains REBALANCE files_to_send files_to_remove
 */
public class RebalanceInstruction {

    private ArrayList<FileToSend> filesToSend;
    private ArrayList<String> filesToRemove;

    private class FileToSend {
        private String filename;
        private ArrayList<String> DstoresToSend;

        public FileToSend(String filename, ArrayList<String> Dstores) {
            this.filename = filename;
            this.DstoresToSend = Dstores;
        }

        public String getInstruction() {
            StringBuilder instruction = new StringBuilder();
            instruction.append(this.filename).append(" ").append(this.DstoresToSend.size()).append(" ");
            for (String Dstore : this.DstoresToSend) {
                instruction.append(Dstore).append(" ");
            }
            return instruction.toString();
        }
    }

    public RebalanceInstruction() {
        this.filesToSend = new ArrayList<>();
        this.filesToRemove = new ArrayList<>();
    }

    public void addFileToSend(String filename, ArrayList<String> DstoresToSendTo) {
        this.filesToSend.add(new FileToSend(filename, DstoresToSendTo));
    }

    public void addFileToRemove(String filename) {
        this.filesToRemove.add(filename);
    }

    public String getInstruction() {
        StringBuilder instruction = new StringBuilder();
        instruction.append(Protocol.REBALANCE_TOKEN).append(" ");
        //FILES_TO_SEND
        instruction.append(this.filesToSend.size()).append(" ");
        for (FileToSend f : this.filesToSend) {
            instruction.append(f.getInstruction());
        }
        //FILES_TO_REMOVE
        instruction.append(this.filesToRemove.size());
        for (String f : this.filesToRemove) {
            instruction.append(" ").append(f);
        }
        return instruction.toString();
    }


}
