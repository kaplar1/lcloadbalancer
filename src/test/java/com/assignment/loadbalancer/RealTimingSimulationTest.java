package com.assignment.loadbalancer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.Random;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("slow")
public class RealTimingSimulationTest {
    private static final int CLIENTS = 10;
    private static final int REQUESTS_PER_CLIENT = 3;

    @Test
    @Timeout(value = 3, unit = MINUTES)
    @DisplayName("servers are held for a real 1 to 10 seconds and always released")
    void holdsServersForRealSecondsAndReleasesThemAll() throws InterruptedException {
        PooledLoadBalancer balancer = new LeastConnectionsBalancer(ServerPool.defaultPool());

        // The genuine hold from the brief. Seeded only so a failure is reproducible.
        Simulation simulation = new Simulation(balancer, Simulation.defaultHold(new Random(7)));

        long startedAt = System.nanoTime();
        Simulation.Result result = simulation.run(CLIENTS, REQUESTS_PER_CLIENT, Duration.ofMinutes(2));
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertTrue(result.completed(), "clients did not finish within the timeout");
        assertEquals(CLIENTS * REQUESTS_PER_CLIENT, result.served());
        assertEquals(0, result.rejected(), "an unlimited pool should never reject");

        // Each client makes its requests one after another, so it cannot finish
        // faster than the shortest possible holds added together. This is what
        // distinguishes a genuine second-scale hold from a scaled-down one: swap in
        // millisecond holds and this assertion fails immediately.
        Duration floor = Simulation.MIN_HOLD.multipliedBy(REQUESTS_PER_CLIENT);
        assertTrue(elapsed.compareTo(floor) >= 0,
                "finished in " + elapsed.toMillis() + " ms, which is faster than "
                        + REQUESTS_PER_CLIENT + " holds of at least "
                        + Simulation.MIN_HOLD.toSeconds() + "s each");

        // Nor can it take longer than the slowest possible holds, plus slack for
        // scheduling. Guards against a hold that silently exceeds the brief's range.
        Duration ceiling = Simulation.MAX_HOLD.multipliedBy(REQUESTS_PER_CLIENT).plusSeconds(30);
        assertTrue(elapsed.compareTo(ceiling) <= 0,
                "took " + elapsed.toSeconds() + "s, longer than the brief's holds allow");

        assertTrue(result.peakConcurrent() > 1,
                "expected connections to overlap, peak was " + result.peakConcurrent());
        assertEquals(0, result.capacityBreaches());

        balancer.servers().forEach(server -> assertEquals(0, server.connectionCount(),
                "connection left held on " + server.name()));
    }

    @Test
    @Timeout(value = 3, unit = MINUTES)
    @DisplayName("with real holds, a capacity-limited pool turns requests away and recovers")
    void saturatesAndRecoversAtRealTiming() throws InterruptedException {
        // One connection per server, more clients than servers, so some must wait
        // out a real multi-second hold before capacity frees up.
        PooledLoadBalancer balancer = new LeastConnectionsBalancer(ServerPool.defaultPool(1));
        Simulation simulation = new Simulation(balancer, Simulation.defaultHold(new Random(11)));

        Simulation.Result result = simulation.run(12, 2, Duration.ofMinutes(2));

        assertTrue(result.completed());
        assertTrue(result.rejected() > 0,
                "with 12 clients and 7 single-connection servers, some should be refused");
        assertEquals(24, result.served() + result.rejected(), "every request accounted for");
        assertEquals(0, result.capacityBreaches(), "capacity must never be exceeded");

        balancer.servers().forEach(server -> assertEquals(0, server.connectionCount()));
    }
}
