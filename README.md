### Purpose

Actorish4j is a minimalistic library that provides asynchronous (non-blocking), actorlike entities and 
tries to implement these entities in the most Java-friendly way.
 
Due to its simplicity and **tight integration with Java 8 CompletableFuture/CompletionStage and Queue API**, 
Actorish4j may be the better choice than some bloated Actor frameworks. If you are not interested in supervision hierarchies, 
actor-based network communication and only need the concurrency part then this library may be the tool for you.

Use JitPack to add dependency in your project:
```
    // gradle build sample
    repositories {
        ...
        maven { url 'https://jitpack.io' }
    }
    dependencies {
        compile 'com.github.dlepex:actorish4j:v0.7.4'
    
        // compile 'org.jctools:jctools-core:2.1.1' // optional dependency, 
        // Actorish4j may use JCTools MpmcArrayQueue, if it detects its presence
    }
```
### Overview


#### Enqueuer&lt;T&gt; 
https://dlepex.github.io/actorish4j/io/github/actorish4j/Enqueuer.html

Enqueuer is the most basic and the most useful "actor-like" entity.

Enqueuer implements **the multiple-producer single-consumer idiom**: anyone can send (offer) a message to the Enqueuer, but only
the single consumer can read (poll) the queue. **The polling is done asynchronously.**

All other actor-like entities in this library are implemented on top of the Enqueuer.

#### TaskEnqueuer 
https://dlepex.github.io/actorish4j/io/github/actorish4j/TaskEnqueuer.html

TaskEnqueuer is the Enqueuer which polls and executes async tasks one by one.

TaskEnqueuer can be used as the direct **replacement for the single-threaded ExecutorService**, in case your tasks are asynchronous computations.
Note that this class doesn't follow ExecutorService API deliberately because it can be misused for blocking tasks.


TaskEnqueuer guarantees that:
 - Async tasks will be executed in the order of their arrival
 - Async tasks will NEVER run concurrently i.e. next AsyncRunnable will wait for the completion of the CompletionStage of the previous AsyncRunnable



#### Agent&lt;S&gt; 
https://dlepex.github.io/actorish4j/io/github/actorish4j/Agent.html

Agent implements the specific form of "state sharing pattern" tailored to asynchronous computations 
(Basically this library itself only useful for non-blocking code)

Agent is a very simple (thin) wrapper around the TaskEnqueuer class.

Agent interface (method naming) is inspired by Elixir Agent module. 
See https://hexdocs.pm/elixir/Agent.html

#### StateMachine&lt;E&gt; 

https://dlepex.github.io/actorish4j/io/github/actorish4j/StateMachine.html

Event-Driven State Machine implementation inspired by Erlang gen_statem (gen_fsm) behaviour in a "state functions mode". 
In this mode each state has (or better say "is") a corresponding function (StateFunc) that handles events in that state.

This test contains two example of state machines:
https://github.com/dlepex/actorish4j/blob/master/src/test/java/io/github/actorish4j/StateMachineTest.java

### Javadoc

https://dlepex.github.io/actorish4j/overview-summary.html

### Showcases

The classes below are the examples of applying Actorish4j to the "real world problems". 

###### NettyChannelWritesEnqueuer

https://github.com/dlepex/actorish4j/blob/master/src/main/java/io/github/actorish4j/applications/NettyChannelWritesEnqueuer.java

NettyChannelWritesEnqueuer orders writes to Netty channel and flushes them when it's appropriate to do so.

###### ExactDateScheduler

https://github.com/dlepex/actorish4j/blob/master/src/main/java/io/github/actorish4j/applications/ExactDateScheduler.java

ExactDateScheduler schedules task execution at specified LocalDateTime.
Tasks are executed sequentially one after another, their order is preserved in case of equal begin dates


