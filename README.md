# Distributed-File-System


<br/>This project is a distributed storage system, which is used to **store files over multiple locations** for security and backup reasons. 
The concept is that any number of concurrent _**Clients**_ communicate with a _**Controller**_ and the Data Stores (_**Dstores**_) to either store a file, remove a file, load a file from storage or list all files.
For simplicity and testability, this was deisgned to work on just one machine, with multiple CMD's running the separate elements representing different computers.
However, the layout and communication concepts are the same for if it was spread over multiple servers.

<p align="center">
  <br/>
  <img src="https://github.com/oranbramble/Distributed-File-System/assets/56357864/18f6fba9-d74a-4164-8b69-6a81f6069f8d">

</p>

## <br/>Networking

<br/>The components communicate using TCP sockets. Each componnet is given the port to communicate to the Controller with (`cport`), and the Dstores are given a port number to listen on for incoming communication from the Controller. The Controller -> Dstore connections are persistant, and the Client -> Controller connections are not. <br/>


## <br/>Components





### <br/>Controller

There is only one Controller which orchestrates client requests, and maintains an index of files stored, on which Dstores the files are stored, and the size of each file. The Controller never handles the files themselves however. For this, the Clients and Dstores communicate directly, the Controller just informs each Client which Dstore to communicate with, depending on the request. 

One key feature of the Controller, and the system as a whole, is the _**Rebalance operation**_. This is an alogorithm that ensures all files are evenly spread over the Dstores whilst ensuring they are also stored `R` times each (`R` is an argument specified at the intialisation of the Controller object which specifies how many times we want to copy a file over the Dstores). It is triggered either after a certain time interval has passed (`rebalance_period`) or when a new Dstore joins the system. It is a complicated algorithm but it adds dynamic fault tolerance into the system, meaning the system will always remain fault tolerant, even after Dstores fail. 

To start a Controller, the following command line parameters are used:

```
java Controller cport R timeout rebalance_period
```
> A Controller is given a port to listen on (cport), a replication factor (R), a
timeout in milliseconds (timeout) and how long to wait (in milliseconds) to start the next
rebalance operation (rebalance_period)





### <br/>Client

The Client(s) are the programs which commands are entered into in order to communicate with the storage system. As many clients can be run and send commands concurrently as needed. 4 different commands
are used withing the Client(s) to communicate with the Controller. These are:

- `STORE`
- `REMOVE`
- `LOAD`
- `LIST`

To start a Client, the following command line parameters are used:

```
java Client cport timeout
```
> A client is started with the controller port to communicate with it (cport) and a timeout
in milliseconds (timeout)

This structure of Client only used to work with one command, meaning the Client would be started, then 1 commanc could be entered before the Client then closed the connection and ended. This makes the connections of Client -> Conntroller connection non-persistant. To enable running of multiple commands, I introduced a new class ClientMain, which takes the same arguments as above but will allow for multiple commands. 

To start a ClientMain, the following command line parameters are used:
```
java ClientMain cport timeout
```




### <br/>Dstore

These are the storage programs for storing files. They receive files to store directly from Clients when Clients send `STORE` command, and save them to the `file_folder` directory. They also send Clients file contents after receiving a `LOAD` operation from a Client, as well as removing a file when receiving a `REMOVE` command from the Controller. They also handle internal `REBALANCE` commands needed for the Rebalance operation.

To start a Dstore, the following command line parameters are used:

```
java Dstore port cport timeout file_folder
```
> A Dstore is started with the port to listen on (port) and the controller’s port to talk to
(cport), timeout in milliseconds (timeout) and where to store the data locally
(file_folder).

For ease of use of the program, I also developed a DstoreMain class, which initialises and runs multiple different Dstores, so the user does not have to start each one manually. This was mainly for testing, but is useful for quick use of the program.

To start DstoreMain, run the following in command line:
```
java DstoreMain cport timeout N
```
> The DstoreMain is started with the Controller's port (`cport`), the time to wait before exiting (`timeout`), and `N` which specifies how many Dstores to start. The `file_folder` for each is 'StoreN', where N is the number of the Dstore. I.e, for N=5, the directories will be Store1, Store2, Store3, Store4 and Store5. Also, the `port` for the Dstore is automatically set to 500 + n. E.g, for N=5, each Dstore will have the port 501, 502, 503, 504 and 505 respectively. 










## How to run

This program has been made wholly in Java, so requires the Java JDK package to be installed. To download this, visit https://www.oracle.com/uk/java/technologies/downloads/#jdk21-windows. The following explanation of running of the program lists commands to be used. For explanations of these, see the above 'Components' section.

### Starting up

</br>To run, clone this git repositroy using the git command in the Command Line Interface for your operating system.
```
git clone https://github.com/oranbramble/Distributed-File-System.git
```

</br> Once cloned, multiple different Command Line Interface windows must be openend, with each running a separate component of this system. This mimics the components running on separate machines.

</br> One window will be used to run the `Controller`. This must be run first before any other component, as they will all look to connect to the Controller on the `cport`. In a Command Line Interface window, use the following command:

```
java Controller cport R timeout rebalance_period
```


</br> Once a `Controller` is running, other components can be started. However, no `Client` requests will be served until `R` `Dstores` have joined the system. Therefore, next the `Dstore`s should be started. To do this, either N windows can be opened, and within each the following command can be run:

```
java Dstore port cport timeout file_folder
```

Or, one Command line Interface window may be used and the following command run:

```
java DstoreMain cport timeout N
```

</br> Next a `Client` can be run to operate commands. Usually, `Client` will only serve one command before closing the connection. To run multiple commands, the `ClientMain` can be used (this is recommended). For the `Client`, run the following command line command:
```
java Client cport timeout
```

And for the `ClientMain`, use the following:
```
java ClientMain cport timeout
```
</br>

### Client Commands

4 commmands are used on the Client or ClientMain in order to manipulate files on the system. The commands are as listed below.

**STORE**

```
STORE filename file_size
```
>This communicates to the Controller that we want to store file `filename`. This file must be in the same directory as the command's line current working directory. the `file_size` is the size of the file in bytes.

</br>* REMOVE *

```
REMOVE filename
```
>This removes the file specified from the file storage system.

### LOAD

```
LOAD filename
```
>This loads the file from the storage system into the current working directory of the command line.


### LIST

```
LIST
```
>This lists all files currently stored in the system. 







