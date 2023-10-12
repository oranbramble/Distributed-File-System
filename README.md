# Distributed-File-System


<br/>This project is a distributed storage system, which is used to **store files over multiple locations** for security and backup reasons. 
The concept is that any number of concurrent _**Clients**_ communicate with a _**Controller**_ and the Data Stores (_**DStores**_) to either store a file, remove a file, load a file from storage or list all files.
For simplicity and testability, this was deisgned to work on just one machine, with multiple CMD's running the separate elements representing different computers.
However, the layout and communication concepts are the same for if it was spread over multiple servers.

<p align="center">
  <br/>
<img src="https://github.com/oranbramble/Distributed-File-System/assets/56357864/18f6fba9-d74a-4164-8b69-6a81f6069f8d">
</p>

## Components

### Controller

There is only one Controller which orchestrates client requests, and maintains an index of files stored, on which DStores the files are stored, and the size of each file. The Controller never handles the files themselves however. For this, the Clients and DStores communicate directly, the Controller just informs each Client which DStore to communicate with, depending on the request. 

One key feature of the Controller, and the system as a whole, is the _**Rebalance operation**_. This is an alogorithm that ensures all files are evenly spread over the DStores whilst ensuring they are also stored `R` times each (`R` is an argument specified at the intialisation of the Controller object which specifies how many times we want to copy a file over the DStores). It is triggered either after a certain time interval has passed (`rebalance_period`) or when a new DStore joins the system. It is a complicated algorithm but it adds dynamic fault tolerance into the system, meaning the system will always remain fault tolerant, even after DStores fail. 

To start a Controller, the following command line parameters are used:

```
java Controller cport R timeout rebalance_period
```


### Client

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

### DStore

These are the storage programs for storing files.
