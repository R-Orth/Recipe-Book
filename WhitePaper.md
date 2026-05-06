# White Paper: Server Design

## 1. Executive Summary
- Design for a multi-server distributed identity system

Our program will consist of both one-shot clients that communicate with a set of identity servers.
- High-level goals

Design an arcetecture that has both consistency and redudancy among servers
- Key benefits and outcomes

## 2. Introduction
- Background and context
- Scope of the server design
- Audience and assumptions

## 3. Client Server Model
- Our servers and clients will be separate, meaning we will not be implementing
servents. Instead, we will have single shot clients that will send one request
to the coordinator then die after receiving a response. Before implementing P3
tasks, we do need to revivsit our P2 clients, as they would not die on their
own. To path this in our subjmission, we added a ```System.exit()``` call, but
this resulted in a marshalling error in the server. We also need to address the
time taken to send a request and receive a response in our P2, as it was suspiciously
long.
- 
- 

## 4. Coordinator Elections
- We will be using the Bully algorithm for our election process. We decide this
was our best option because it makes the method for clients to rediscover the 
new coordinator relatively easy, as clients can just join the multicast group
used for the election and listen for coordinator update messages. 
- Whenever a server joins the system, they will initiate an election. This includes
previous coordinators who rejoin the system. 
- To detect a missing or down coordinator, our replicas will send periodic heartbeats
(once a minute) to the coordinator, which will multicast a response, which will reset
all replicas heartbeat timers so we don't too many going off at once.
- For our newly elected coordinators, cleints will be stored in our data store
in a list of all known connected clients. This list will be added to when a new client
connects to the system, and pruned when a new coordinator is elected and doesn't receive
a new conncection from that client.

## 5. Consistency Model
- We will be using sequential consistency and will implement it using a blocking
primary backup protocol. If a client initially connects to a replica, the replica
will forward the location of the coordinator for the client to connect to.
This will be managed by our coordinator, and when our coordinator
alerts all replicas of an update, those multicasts will happen in the order in which
the coordinator decides that they happened.
- Because our chosen method of consistency will be using a blocking protocol, the
client may not receive confirmation that all servers have replicated their request
for a not-insignificant amount of time. We anticipate a Maximum window of inconsistency
of 0.5 seconds. This accounts for potential UDP packet loss in the multicasting of
replica updates and the subsiquent retransmission to replicas who missed the initial
update.
- 

## 6. Sever Synchronization
- As a part of our sequential consistency model, we will be using Lamport timestamps,
not vector clocks. This will create a definite and agreed-upon order of events
between our servers. 
- 
- 