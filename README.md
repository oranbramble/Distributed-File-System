# Distributed-File-System


This project is a distributed storage system, which is used to **store files over multiple locations** for security and backup reasons. 
The concept is that **Clients** communicate with a **Controller** and the Data Stores (**DStores**) to either store a file, remove a file, load a file from storage or list all files.
For simplicity and testability, this was deisgned to work on just one machine, with multiple CMD's running the separate elements representing different computers.
However, the layout and communication concepts are the same for if it was spread over multiple servers.


![DFS image](https://github.com/oranbramble/Distributed-File-System/assets/56357864/18f6fba9-d74a-4164-8b69-6a81f6069f8d)



