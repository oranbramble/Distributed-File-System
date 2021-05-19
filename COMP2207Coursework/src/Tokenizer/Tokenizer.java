package Tokenizer;

import Loggers.Protocol;

import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

/**
 * A scanner and parser for requests.
 */

public class Tokenizer {

    public Tokenizer() { ; }


    /**
     * Parses requests.
     */
    public Token getToken(String command) {
        StringTokenizer sTokenizer = new StringTokenizer(command);
        if (!(sTokenizer.hasMoreTokens()))
            return null;
        String firstToken = sTokenizer.nextToken();

        //Matches 'LIST' and 'LIST filelist' commands, generating ListToken or FileListToken
        if (firstToken.equals(Protocol.LIST_TOKEN)) {
            if (sTokenizer.hasMoreTokens()) {
                return new FileListToken(command, sTokenizer);
            } else {
                return new ListToken(command);
            }
        }

        //Matches 'STORE filename filesize' command, generating StoreToken
        if (firstToken.equals(Protocol.STORE_TOKEN)) {
            //Number of elements after command STORE must be 2
            if (sTokenizer.countTokens() == 2) {
                //If we cannot convert the second element (i.e. filesize) to an integer, must be invalid input
                //so we return a null if this fails
                try {
                    return new StoreToken(command, sTokenizer.nextToken(), Integer.parseInt(sTokenizer.nextToken()));
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
        }

        //Matches 'LOAD filename' command, generating LoadToken
        if (firstToken.equals(Protocol.LOAD_TOKEN)) {
            if (sTokenizer.countTokens() == 1) {
                return new LoadToken(command, sTokenizer.nextToken());
            } else {
                return null;
            }
        }

        //Matches 'LOAD_DATA filename' command, generating LoadDataToken
        if (firstToken.equals(Protocol.LOAD_DATA_TOKEN)) {
            if (sTokenizer.countTokens() == 1) {
                return new LoadDataToken(command, sTokenizer.nextToken());
            } else {
                return null;
            }
        }

        //Matches 'RELOAD filename' command, generating ReloadToken
        if (firstToken.equals(Protocol.RELOAD_TOKEN)) {
            if (sTokenizer.countTokens() == 1) {
                return new ReloadToken(command, sTokenizer.nextToken());
            } else {
                return null;
            }
        }

        //Matches 'REMOVE filename' command, generating RemoveToken
        if (firstToken.equals(Protocol.REMOVE_TOKEN)) {
            if (sTokenizer.countTokens() == 1) {
                return new RemoveToken(command, sTokenizer.nextToken());
            } else {
                return null;
            }
        }

        //Matches 'STORE_TO port1 port2 ... portR' command, generating StoreToToken
        if (firstToken.equals(Protocol.STORE_TO_TOKEN)) {
            if (sTokenizer.hasMoreTokens()) {
                return new StoreToToken(command, sTokenizer);
            } else {
                return null;
            }
        }

        //Matches 'STORE_COMPLETE' command, generating StoreCompleteToken
        if (firstToken.equals(Protocol.STORE_COMPLETE_TOKEN)) {
            if (!(sTokenizer.hasMoreTokens())) {
                return new StoreCompleteToken(command);
            } else {
                return null;
            }
        }

        //Matches 'LOAD_FROM port filesize' command, generating LoadFromToken
        if (firstToken.equals(Protocol.LOAD_FROM_TOKEN)) {
            if (sTokenizer.countTokens() == 2) {
                try {
                    return new LoadFromToken(command, Integer.parseInt(sTokenizer.nextToken()),
                                             Integer.parseInt(sTokenizer.nextToken()));
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
        }

        //Matches 'REMOVE_COMPLETE' command, generating RemoveCompleteToken
        if (firstToken.equals(Protocol.REMOVE_COMPLETE_TOKEN)) {
            if (!(sTokenizer.hasMoreTokens())) {
                return new RemoveCompleteToken(command);
            } else {
                return null;
            }
        }

        //Matches the "REBALANCE ...' command (see method for conversion to token)
        if (firstToken.equals(Protocol.REBALANCE_TOKEN)) {
            return generateRebalanceToken(sTokenizer, command);
        }

        //Matches 'ERROR_FILE_DOES_NOT_EXIST' and 'ERROR_FILE_DOES_NOT_EXIST filename' errors
        if (firstToken.equals(Protocol.ERROR_FILE_DOES_NOT_EXIST_TOKEN)) {
            if (sTokenizer.countTokens() == 1) {
                return new FileNotExistFilenameToken(command, sTokenizer.nextToken());
            } else  if (sTokenizer.countTokens() == 0){
                return new FileNotExistToken(command);
            } else {
                return null;
            }
        }

        //Matches 'ERROR_FILE_ALREADY_EXISTS' error, generating FileAlreadyExistsToken
        if (firstToken.equals(Protocol.ERROR_FILE_ALREADY_EXISTS_TOKEN)) {
            if (!(sTokenizer.hasMoreTokens())) {
                return new FileAlreadyExistsToken(command);
            } else {
                return null;
            }
        }

        //Matches 'ERROR_NOT_ENOUGH_DSTORES' error, generating NotEnoughDStoresToken
        if (firstToken.equals(Protocol.ERROR_NOT_ENOUGH_DSTORES_TOKEN)) {
            if (!(sTokenizer.hasMoreTokens())) {
                return new NotEnoughDStoresToken(command);
            } else {
                return null;
            }
        }

        //Matches 'ERROR_LOAD' error, generating ErrorLoadToken
        if (firstToken.equals(Protocol.ERROR_LOAD_TOKEN)) {
            if (!(sTokenizer.hasMoreTokens())) {
                return new ErrorLoadToken(command);
            } else {
                return null;
            }
        }

        //Matches 'ACK' acknowledgement, generating ErrorLoadToken
        if (firstToken.equals(Protocol.ACK_TOKEN)) {
            if (!(sTokenizer.hasMoreTokens())) {
                return new AckToken(command);
            } else {
                return null;
            }
        }

        //Matches 'STORE_ACK filename' acknowledgement, generating StoreAckToken
        if (firstToken.equals(Protocol.STORE_ACK_TOKEN)) {
            if (sTokenizer.countTokens() == 1) {
                return new StoreAckToken(command, sTokenizer.nextToken());
            } else {
                return null;
            }
        }

        //Matches 'REMOVE_ACK filename' acknowledgement, generating RemoveAckToken
        if (firstToken.equals(Protocol.REMOVE_ACK_TOKEN)) {
            if (sTokenizer.countTokens() == 1) {
                return new RemoveAckToken(command, sTokenizer.nextToken());
            } else {
                return null;
            }
        }

        //Matches 'JOIN port' command, generating JoinToken
        if (firstToken.equals(Protocol.JOIN_TOKEN)) {
            if (sTokenizer.hasMoreTokens()) {
                return new JoinToken(command, Integer.parseInt(sTokenizer.nextToken()));
            } else {
                return null;
            }
        }

        //Matches 'REBALANCE_STORE filename filesize' command, generating RebalanceStoreToken
        if (firstToken.equals(Protocol.REBALANCE_STORE_TOKEN)) {
            if (sTokenizer.countTokens() == 2) {
                try {
                    return new RebalanceStoreToken(command, sTokenizer.nextToken(), Integer.parseInt(sTokenizer.nextToken()));
                } catch (NumberFormatException e) {
                    return null;
                }
            } else {
                return null;
            }
        }

        //Matches 'REBALANCE_COMPLETE' acknowledgement, generating RebalanceCompleteToken
        if (firstToken.equals(Protocol.REBALANCE_COMPLETE_TOKEN)) {
            if (!(sTokenizer.hasMoreTokens())) {
                return new RebalanceCompleteToken(command);
            } else {
                return null;
            }
        }

        return null;
    }


    /**
     * Method which generates a RebalanceToken from the command in the StringTokeniser
     * Separated this into a different method since it was a couple nested for loops with error checking which
     * would have been difficult to read in above getToken method
     *
     * @param s: StringTokenizer containing the REBALANCE command values
     * @param command:
     * @return
     */
    private RebalanceToken generateRebalanceToken(StringTokenizer s, String command) {
        try {
            ArrayList<FileToSend> filesToSend = new ArrayList<>();
            ArrayList<String> filesToRemove = new ArrayList<>();

            //Gets the number of files to send
            int numberOfFilesToSend = Integer.parseInt(s.nextToken());
            //Loops through next x (= numberOfFilesToSend) values which we know must be files to send
            for (int x = 0; x < numberOfFilesToSend; x++) {
                    //Gets filename of a file to send
                    String filenameToSend = s.nextToken();
                        //Gets the number of DStores we are sending the file to
                        int numberOfDStores = Integer.parseInt(s.nextToken());
                        ArrayList<Integer> dStores = new ArrayList<>();
                        //Loops through next y (= numberOfDStores) values which we know to be DStore ports
                        for (int y = 0; y < numberOfDStores; y++) {
                            //Gets the DStore ports
                            dStores.add(Integer.parseInt(s.nextToken()));
                        }
                        //After each loop of outer for loop, we add a fileToSend to the arrayList
                        filesToSend.add(new FileToSend(filenameToSend, numberOfDStores, dStores));
                }

            //Gets all the files to remove
            int numberOfFilesToRemove = Integer.parseInt(s.nextToken());
            for (int x = 0; x < numberOfFilesToRemove; x++) {
                filesToRemove.add(s.nextToken());
            }

            //Returns the RebalancedToken
            return new RebalanceToken(command, numberOfFilesToSend, numberOfFilesToRemove, filesToSend, filesToRemove);

        //This catch means if there's any errors in the formatting of the command, a null value will be returned
        } catch (NumberFormatException | NoSuchElementException e) {
            return null;
        }
    }
}








