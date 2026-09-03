package com.assignment.loadbalancer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;

public class Simulation {
    public static final Duration MIN_HOLD = Duration.ofSeconds(1);
    public static final Duration MAX_HOLD = Duration.ofSeconds(10);

    private final PooledLoadBalancer balancer;
    private final LongSupplier holdMillis;

    public Simulation(PooledLoadBalancer balancer) {
        this(balancer, defaultHold(new Random()));
    }

    public Simulation(PooledLoadBalancer balancer, LongSupplier holdMillis) {
        if (balancer == null || holdMillis == null) {
            throw new IllegalArgumentException("balancer and holdMillis are required");
        }
        this.balancer = balancer;
        this.holdMillis = holdMillis;
    }

    public static LongSupplier defaultHold(Random random) {
        long min = MIN_HOLD.toMillis();
        long span = MAX_HOLD.toMillis() - min;
        return () -> min + (long) random.nextInt((int) span + 1);
    }

    public Result run(int clients, int requestsPerClient, Duration timeout)
            throws InterruptedException {

        if (clients <= 0 || requestsPerClient <= 0) {
            throw new IllegalArgumentException("clients and requestsPerClient must be positive");
        }

        LongAdder served = new LongAdder();
        LongAdder rejected = new LongAdder();
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peakInFlight = new AtomicInteger();
        AtomicInteger capacityBreaches = new AtomicInteger();
        Map<String, LongAdder> perServer = new ConcurrentHashMap<>();

        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(clients);
        List<Thread> threads = new ArrayList<>(clients);

        for (int c = 0; c < clients; c++) {
            Thread thread = new Thread(() -> {
                try {
                    start.await(); // release every client at once, to maximise overlap
                    for (int r = 0; r < requestsPerClient; r++) {
                        try (Lease lease = balancer.acquire()) {
                            peakInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);

                            Server server = lease.server();
                            perServer.computeIfAbsent(server.name(), name -> new LongAdder())
                                    .increment();
                            if (server.connectionCount() > server.capacity()) {
                                capacityBreaches.incrementAndGet();
                            }
                            served.increment();

                            Thread.sleep(holdMillis.getAsLong());

                            inFlight.decrementAndGet();
                        } catch (NoServerAvailableException e) {
                            rejected.increment();
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    finished.countDown();
                }
            }, "client-" + c);
            threads.add(thread);
            thread.start();
        }

        start.countDown();
        boolean completed = finished.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!completed) {
            threads.forEach(Thread::interrupt);
        }

        Map<String, Long> counts = new LinkedHashMap<>();
        for (Server server : balancer.servers()) {
            LongAdder adder = perServer.get(server.name());
            counts.put(server.name(), adder == null ? 0L : adder.sum());
        }

        return new Result(completed, served.sum(), rejected.sum(),
                peakInFlight.get(), capacityBreaches.get(), counts);
    }

    public record Result(boolean completed,
            long served,
            long rejected,
            int peakConcurrent,
            int capacityBreaches,
            Map<String, Long> requestsPerServer) {

        /** Difference between the busiest and least busy server. */
        public long spread() {
            long max = requestsPerServer.values().stream().mapToLong(Long::longValue).max().orElse(0);
            long min = requestsPerServer.values().stream().mapToLong(Long::longValue).min().orElse(0);
            return max - min;
        }
    }
}
