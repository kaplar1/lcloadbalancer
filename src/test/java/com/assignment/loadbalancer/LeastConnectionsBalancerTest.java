package com.assignment.loadbalancer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class LeastConnectionsBalancerTest {

    private List<Lease> acquire(PooledLoadBalancer balancer, int count) {
        List<Lease> leases = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            leases.add(balancer.acquire());
        }
        return leases;
    }

    private List<String> namesOf(List<Lease> leases) {
        return leases.stream().map(lease -> lease.server().name()).toList();
    }

    @Test
    @DisplayName("with every server idle, selection rotates through the pool")
    void rotatesWhenAllServersAreEquallyLoaded() {
        PooledLoadBalancer balancer = new LeastConnectionsBalancer(ServerPool.defaultPool());

        // Equal counts mean every server ties, so the rotating cursor decides.
        // Least connections acts like round-robin at equal load.
        assertEquals(List.of("a", "b", "c", "d", "e", "f", "g"),
                namesOf(acquire(balancer, 7)));
    }

    @Test
    @DisplayName("the least-connected server wins, not the next in rotation")
    void prefersTheLeastConnectedServer() {
        PooledLoadBalancer balancer = new LeastConnectionsBalancer(ServerPool.defaultPool());
        List<Lease> held = acquire(balancer, 7); // one connection on each

        held.get(2).close(); // c drops to zero, everything else holds one

        assertEquals("c", balancer.acquire().server().name());
    }

    @Test
    @DisplayName("among several idle servers the rotation still breaks the tie")
    void breaksTiesByRotationRatherThanAlwaysPickingTheFirst() {
        PooledLoadBalancer balancer = new LeastConnectionsBalancer(ServerPool.defaultPool());
        List<Lease> held = acquire(balancer, 7);

        held.get(1).close(); // b
        held.get(4).close(); // e

        // Both b and e are at zero. Whichever is chosen, the other must be next,
        // rather than the same server being picked twice.
        String first = balancer.acquire().server().name();
        String second = balancer.acquire().server().name();

        assertTrue(List.of("b", "e").contains(first), "unexpected first choice: " + first);
        assertTrue(List.of("b", "e").contains(second), "unexpected second choice: " + second);
        assertNotEquals(first, second, "the same tied server was chosen twice");
    }

    @Test
    @DisplayName("a server at capacity is skipped")
    void skipsSaturatedServers() {
        PooledLoadBalancer balancer = new LeastConnectionsBalancer(
                ServerPool.defaultPoolWithCapacities(Map.of("b", 1)));

        List<String> chosen = namesOf(acquire(balancer, 20));

        assertEquals(1, chosen.stream().filter("b"::equals).count(),
                "b has capacity 1, so it should be chosen exactly once");
        assertEquals(1, balancer.servers().get(1).connectionCount());
    }

    @Test
    @DisplayName("a fully saturated pool refuses rather than overfilling")
    void throwsWhenNoServerCanAccept() {
        PooledLoadBalancer balancer = new LeastConnectionsBalancer(ServerPool.defaultPool(1));
        acquire(balancer, 7); // every server now holds its single connection

        NoServerAvailableException thrown = assertThrows(NoServerAvailableException.class,
                balancer::acquire);

        assertTrue(thrown.getMessage().contains("retry"),
                "the message should state the retry contract: " + thrown.getMessage());
    }

    @Test
    @DisplayName("releasing a connection makes the pool serviceable again")
    void recoversAfterARelease() {
        PooledLoadBalancer balancer = new LeastConnectionsBalancer(ServerPool.defaultPool(1));
        List<Lease> held = acquire(balancer, 7);

        assertThrows(NoServerAvailableException.class, balancer::acquire);

        held.get(3).close(); // free d

        assertEquals("d", balancer.acquire().server().name());
    }

    @Test
    @DisplayName("capacity is never exceeded")
    void neverExceedsCapacity() {
        PooledLoadBalancer balancer = new LeastConnectionsBalancer(ServerPool.defaultPool(2));

        acquire(balancer, 14); // exactly fills the pool

        balancer.servers()
                .forEach(server -> assertEquals(2, server.connectionCount(), server.name() + " should be full"));
        assertThrows(NoServerAvailableException.class, balancer::acquire);
    }

    @Test
    @DisplayName("load stays evenly spread as connections accumulate")
    void spreadsLoadEvenly() {
        PooledLoadBalancer balancer = new LeastConnectionsBalancer(ServerPool.defaultPool());

        acquire(balancer, 70); // ten per server, if selection is doing its job

        balancer.servers()
                .forEach(server -> assertEquals(10, server.connectionCount(), "uneven load on " + server.name()));
    }

    @Test
    @DisplayName("an empty pool is rejected at construction")
    void rejectsEmptyPool() {
        assertThrows(IllegalArgumentException.class,
                () -> new LeastConnectionsBalancer(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new LeastConnectionsBalancer(null));
    }

    @Test
    @DisplayName("the exposed pool cannot be modified")
    void exposesAnImmutablePool() {
        PooledLoadBalancer balancer = new LeastConnectionsBalancer(ServerPool.defaultPool());

        assertThrows(UnsupportedOperationException.class,
                () -> balancer.servers().add(new Server("h")));
    }
}
