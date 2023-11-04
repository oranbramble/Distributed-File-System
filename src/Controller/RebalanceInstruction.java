package Controller;

import java.util.ArrayList;
import Loggers.Protocol;

/**
 * Class to hold the instruction sent to a single Dstore in the rebalance operation.
 * The instruction tells the Dstore to send files to other Dstores using their port numbers and/or
 * files to remove from its own storage.
 * <p>
 * The instruction format is:
 * REBALANCE files_to_send files_to_remove
 *          files_to_send = num_of_files_to_send file_to_send1 file_to_send2 ...
 *                  file_to_send = filename num_of_dstores_to_send_to dstore_port1 dstore_port2 ...
 *          files_to_remove = num_of_files_to_remove file_to_remove1 file_to_remove2 ...
 */
public class RebalanceInstruction {

    // List of files to send in form of FileToSend objects, which contain the filenames and Dstore ports to send to
    private final ArrayList<FileToSend> filesToSend;
    // List of files to remove, which is just a list of filenames to remove
    private final ArrayList<String> filesToRemove;

    /**
     * Class to hold information on file to send to other Dstores
     */
    private static class FileToSend {
        // Filename of file to send
        private final String filename;
        // List of port numbers of Dstores to send file to
        private final ArrayList<String> DstoresToSend;

        /**
         * Constructor
         * @param filename : name of file to send
         * @param Dstores : list of Dstore ports to send file to
         */
        public FileToSend(String filename, ArrayList<String> Dstores) {
            this.filename = filename;
            this.DstoresToSend = Dstores;
        }

        /**
         * Method to generate file_to_send String instruction, based on class attributes,
         * for use in whole Rebalance instruction
         * @return String : file_to_send instruction
         */
        public String getInstruction() {
            StringBuilder instruction = new StringBuilder();
            instruction.append(this.filename).append(" ").append(this.DstoresToSend.size()).append(" ");
            for (String Dstore : this.DstoresToSend) {
                instruction.append(Dstore).append(" ");
            }
            return instruction.toString();
        }
    }

    /**
     * Constructor
     */
    public RebalanceInstruction() {
        this.filesToSend = new ArrayList<>();
        this.filesToRemove = new ArrayList<>();
    }

    /**
     * Method to add a new file_to_send along with the port to send it to,
     * or to add a new port to send to on an existing file_to_send
     * @param filename : name of file to send from this Dsotre to others
     * @param dstoreToSendTo : port number of Dstore we want to send file to
     */
    public void addFileToSend(String filename, Integer dstoreToSendTo) {
        boolean fileToSendExists = false;
        for (FileToSend f : this.filesToSend) {
            if (f.filename.equals(filename)) {
                f.DstoresToSend.add(dstoreToSendTo.toString());
                fileToSendExists = true;
            }
        }
        if (!fileToSendExists) {
            ArrayList<String> dstoresToSendTo = new ArrayList<>();
            dstoresToSendTo.add(dstoreToSendTo.toString());
            this.filesToSend.add(new FileToSend(filename, dstoresToSendTo));
        }
    }

    /**
     * Add a new file_to_remove
     * @param filename : name of file to remove from Dstore
     */
    public void addFileToRemove(String filename) {
        this.filesToRemove.add(filename);
    }

    /**
     * Method to generate String instruction for the Rebalance operation for a single Dstore
     * It generates an instruction in the following form:
     * REBALANCE files_to_send files_to_remove
     *         files_to_send = num_of_files_to_send file_to_send1 file_to_send2 ...
     *                 file_to_send = filename num_of_dstores_to_send_to dstore_port1 dstore_port2 ...
     *         files_to_remove = num_of_files_to_remove file_to_remove1 file_to_remove2 ...
     * @return String : Rebalance instruction for specific Dstore
     */
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
