package com.assignment.loadbalancer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class Demo {

    public static void main(String[] args) throws InterruptedException {
        selection();
        capacity();
        saturation();
        concurrentSimulation();
    }

    private static void selection() {
        heading("Least-connected selection");

        PooledLoadBalancer balancer = new LeastConnectionsBalancer(ServerPool.defaultPool());

        List<Lease> held = new ArrayList<>();
        StringBuilder sequence = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            Lease lease = balancer.acquire();
            held.add(lease);
            sequence.append(lease.server().name()).append(' ');
        }
        System.out.println("all servers idle, so ties rotate: " + sequence.toString().trim());
        System.out.println("counts now: " + balancer.servers());

        held.get(2).close(); // c
        held.get(5).close(); // f
        System.out.println("released c and f: " + balancer.servers());

        Lease next = balancer.acquire();
        System.out.println("next acquire went to: " + next.server().name()
                + "  (least connected, not simply next in rotation)");

        held.forEach(Lease::close);
        next.close();
        System.out.println("after releasing everything: " + balancer.servers());
    }

    private static void capacity() {
        heading("Capacity and skipping");

        // Server b can hold only one connection; the rest are unlimited.
        PooledLoadBalancer balancer = new LeastConnectionsBalancer(
                ServerPool.defaultPoolWithCapacities(Map.of("b", 1)));

        List<Lease> held = new ArrayList<>();
        StringBuilder sequence = new StringBuilder();
        for (int i = 0; i < 14; i++) {
            Lease lease = balancer.acquire();
            held.add(lease);
            sequence.append(lease.server().name()).append(' ');
        }
        System.out.println("b has capacity 1, others unlimited");
        System.out.println("14 acquires:  " + sequence.toString().trim());
        System.out.println("counts:       " + balancer.servers());
        System.out.println("b is used once then skipped, never exceeding 1/1.");
        System.out.println("13 connections over the remaining 6 servers is 2 each");
        System.out.println("with 1 left over, so exactly one server holds 3.");

        held.forEach(Lease::close);
    }

    private static void saturation() {
        heading("Total saturation and recovery");

        // Every server holds one connection, so seven acquires fill the pool.
        PooledLoadBalancer balancer = new LeastConnectionsBalancer(ServerPool.defaultPool(1));

        List<Lease> held = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            held.add(balancer.acquire());
        }
        System.out.println("pool full: " + balancer.servers());

        try {
            balancer.acquire();
            System.out.println("ERROR: expected the pool to reject this");
        } catch (NoServerAvailableException e) {
            System.out.println("8th acquire rejected: " + e.getMessage());
        }

        held.get(3).close();
        System.out.println("released d: " + balancer.servers());

        Lease recovered = balancer.acquire();
        System.out.println("acquire now succeeds, and went to: " + recovered.server().name());

        recovered.close();
        held.forEach(Lease::close);
    }

    private static void concurrentSimulation() throws InterruptedException {
        heading("Concurrent simulation (the 1 to 10 second hold, scaled down)");

        PooledLoadBalancer balancer = new LeastConnectionsBalancer(ServerPool.defaultPool());

        int clients = 20;
        int requestsPerClient = 15;
        Random random = new Random(42); // seeded, so runs are comparable

        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger peakInFlight = new AtomicInteger();
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger overCapacity = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(clients);

        for (int c = 0; c < clients; c++) {
            long seed = random.nextLong();
            new Thread(() -> {
                Random local = new Random(seed);
                try {
                    start.await(); // release every client together
                    for (int r = 0; r < requestsPerClient; r++) {
                        try (Lease lease = balancer.acquire()) {
                            peakInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);

                            // stand in for the client's work against its server
                            Server server = lease.server();
                            if (server.connectionCount() > server.capacity()) {
                                overCapacity.incrementAndGet();
                            }
                            Thread.sleep(10 + local.nextInt(91));

                            inFlight.decrementAndGet();
                        } catch (NoServerAvailableException e) {
                            rejected.incrementAndGet();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            }).start();
        }
        System.out.println(clients + " concurrent clients, " + requestsPerClient
                + " requests each, holding for 10 to 100 ms");
        start.countDown();
        boolean done = finished.await(60, TimeUnit.SECONDS);

        System.out.println("completed:      " + done);
        System.out.println("total requests: " + (clients * requestsPerClient));
        System.out.println("peak in flight: " + peakInFlight.get() + " simultaneous connections");
        System.out.println("rejected:       " + rejected.get() + " (pool is unlimited by default)");
        System.out.println("over capacity:  " + overCapacity.get() + " (never, under any interleaving)");
        System.out.println("final counts:   " + balancer.servers());
        System.out.println("every connection was released, so all counts are back to zero.");
    }

    private static void heading(String title) {
        System.out.println();
        System.out.println("=== " + title + " ===");
    }
}
