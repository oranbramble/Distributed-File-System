# Distributed-File-System


<br/>This project is a distributed storage system, which is used to **store files over multiple locations** for security and backup reasons. 
The concept is that _**Clients**_ communicate with a _**Controller**_ and the Data Stores (_**DStores**_) to either store a file, remove a file, load a file from storage or list all files.
For simplicity and testability, this was deisgned to work on just one machine, with multiple CMD's running the separate elements representing different computers.
However, the layout and communication concepts are the same for if it was spread over multiple servers.

<p align="center">
  <br/>
<img src="https://github.com/oranbramble/Distributed-File-System/assets/56357864/18f6fba9-d74a-4164-8b69-6a81f6069f8d">
</p>

## Basic Information

Components:
- One controller
- N DStores
- Clients
- Files, with each file stored R times over the N DStores.

Commands:

- `STORE`
- `REMOVE`
- `LOAD`
- `LIST`


