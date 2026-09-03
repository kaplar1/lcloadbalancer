# Load Balancer Simulation



## Requirements
	Scheduling order: Least connected server is the output
	Objects: Servers "a" - "g"

## Setup
Language: Java 21 LTS
Build: Maven 3.2.5 or newer
IDE: vscode
Extension: Microsof Java Extension(s)
JUnit: 5.10

## Design

Server (Class to create Servers)
ServerPool  (Pool of class Servers)
PooledLoadBalancer (Interface to call aquire)
Lease (Server Lease managed 1 - 10 sec on user side (Autoclosable))
LeastConnectionsBalancer (Find least loaded server to acquire)
NoServerAvailableException  (throw exception in case of full saturation)
Demo (main function to call)




## Tests
Simulation.java  (Test Supporting things are added here)
RealTimeSimulationTest.java (Test with server acquiring server for random 1-10 sec)


## Assumptions and Decisions

Unlimited capacity is handled via Integer.MAX_VALUE
Servers are simulated in-memory objects; no sockets or real I/O.
The pool is fixed at construction: seven servers, a to g.
The caller owns the connection lifecycle.
No lease expiry, time could have managed at server side at least expiry.
Selection rule: among servers that can accept, choose the fewest current connections; ties broken by rotation.
If no server can accept, acquire() throws NoServerAvailableException, contract "retry after a short delay"
Thread safety is required, derived from "multiple simultaneous connections" combined with "always serve the least-connected server".
The balancer is passive: it creates no client threads. Concurrency comes from the simulation harness.
One lock covering scan, capacity check and claim.
Server holds state; all mutation under the balancer's lock
Lease.close() is idempotent



## Tests

RealTimingSimulationTest :	The genuine 1 to 10 second hold test, tagged slow

Further Tests can be added:
LeastConnectionsBalancerTest : 	Selection rotation at equal load, least-connected preference, tie rotation, skipping saturated servers, refusal and recovery.
LeaseTest: 	Accounting: increment and decrement, idempotent close, release on the exception path, lease independence
ConcurrencyInvariantsTest: 	The invariants a racy implementation breaks



## Steps to run
	mvn test               # Full suite
	mvn test -Dgroups=slow # The genuine 1 -10 sec test
	
	mvn -q compile exec:java -Dexec.mainClass=com.assignment.loadbalancer.Demo



## Good to add but not done in this exerscise

Health checking. Least-connections has a pathology worth naming: a dead server has zero connections, so it looks least loaded and attracts all traffic. A production balancer needs health checks, ejection and circuit breaking.

Multi-resource selection. If servers also tracked memory or CPU, "least-connected" would stop being a total order, since a server can be best on connections and worst on memory. 

Dynamic pool membership, graceful draining, and metrics (per-server counts, acquisition latency, refusal rate).