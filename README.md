# Project 3: Identity Server (Phase 2)

* Author: Ryan Orth & Adam Taylor
* Class: CS455 [Distributed Systems] Section #001


# 3 Late Days Please


## Overview

An Identity Server that allows a client to use RMI to access a Jedis database that stores the information about the users including login information, hashed passwords, and other analytical data. A coordinator server processes requests from the client and replicates its actions to the back up servers.

## Building the code

We use a make file to compile our programs, you run it with the following:

~~~ 
    ./build.sh
~~~

Before you start the primary server you need to start the redis client on your local machine with:
~~~ 
    redis-server
~~~

To start the Identity server:


:(Linux & Mac)
~~~
    java -cp ".:./lib/*" server/IdServer [--numport <registry port>] [--verbose] 
~~~

:(Windows)
~~~
    java -cp ".;./lib/*" server/IdServer [--numport <registry port>] [--verbose] 
~~~

To start and use the client, use this command-line argument
~~~
    ./run-client.sh --server <serverhost> [--numport <port#>] <query>
~~~


## Submission Video
https://youtu.be/IqDyfhyxqGU



## Testing

We were able to use the csclusters for testing in this step of the project. This was helpful in our development since we were able to see the real functionality and any issues that might arise when testing between devices. We were unable to access cscluster01, but the rest of the clusters were open and functional. 

### AI Usage

AI is fairly decent at recognizing and diagnosing certain errors and stack traces. This was helpful for this project when we ran into an error for RMI or any of the other features that we were still learning. The aggregation of data that LLMs rely on is why they work so well on errors, they are often similarly formatted common mistakes.  

## Reflection

While this project also built off of the previous one, project 3 required us to go back fix some things. We needed to make some changes to the structure of our project before we could properly move on to the beginning of the project. It was interesting for me to actually implement in code functionality of some of these distributed systems concepts. The election algorithm using bullying and the heartbeat were some of the more notable sections for us. 



5121-5125