# Introduction

Gossip type algorithms can be used both for group communication and for aggregate computation. The goal of this project is to determine
the convergence of such algorithms through a simulator based on actors written in Scala. Since actors in Scala are fully asynchronous, the particular type of Gossip implemented is the so called Asynchronous Gossip.

## How to Run? ##

### Requirements ###
* Install Scala
* Requires SBT

### Run ###
```shell
> sbt
> compile
> run arg1 arg2
```

The code requires two arguments: 

* arg1 : numNodes - Number of nodes to be simulated in the selected topology.
* arg2 : topology - one of "full" OR "line" OR "3d" OR "imp3d" [Explained below]

The value of global variable algo can be toggled in the code between "gossip" or "push-sum" accordingly.[Explained below]

## Algorithms ##

Two styles of algorithms are implemented: Gossip (Generally used for rumor propagation) and Push-Sum (Generally used for aggregation).

### Gossip Algorithm for information propagation ###

The Gossip algorithm involves the following:
* **Starting**: A participant(actor) it told/sent a roumor(fact) by the main process
* **Step**: Each actor selects a random neighboor and tells it the roumor 
* **Termination**: Each actor keeps track of rumors and how many times it has heard the rumor. It stops transmitting once it has heard the roumor 10 times (10 is arbitrary, any value can be configured).

### Push-Sum algorithm for sum computation ###

* **State**: Each actor maintains two quantities: s and w. Initially, s = i (that is actor number i has value i) and w = 1
* **Starting**: Ask one of the actors to start from the main process.
* **Receive**: Messages sent and received are pairs of the form (s, w). Upon receive, an actor adds received pair to its own corresponding values. Upon receive, each actor selects a random neighboor and sends it a message.
* **Send**: When sending a message to another actor, half of s and w is kept by the sending actor and half is placed in the message.
* **Sum estimate**: At any given moment of time, the sum estimate is s/w where s and w are the current values of an actor.
* **Termination**: If an actors ratio s/w did not change more than 10^−10 in 3 consecutive rounds the actor terminates. 
_WARNING: the values s and w independently never converge, only the ratio does._

## Topologies ##

The actual network topology plays a critical role in the dissemination speed of Gossip protocols. This project has simulators for various topologies. The topology determines who is considered a neighboor in the above algorithms.

* **Full Network**:  Every actor is a neighboor of all other actors. That is, every actor can talk directly to any other actor.
* **3D Grid**: Actors form a 3D grid. The actors can only talk to the grid neigboors.
* **Line**: Actors are arranged in a line. Each actor has only 2 neighboors (one left and one right, unless you are the first or last actor).
* **Imperfect 3D Grid**: Grid arrangement but one random other neighboor is selected from the list of all actors (4+1 neighboors).
