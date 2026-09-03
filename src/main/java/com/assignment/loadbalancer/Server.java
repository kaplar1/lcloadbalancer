package com.assignment.loadbalancer;

/*
A Server class, it has a name, a cpactiy, and the number of connections it is holding.
increment decrement connections are package private to be called by balancer in this package only.
The ServerPool will use this template to create a Server object by providing a name.
*/

import java.util.concurrent.atomic.AtomicInteger;

public final class Server {

    public static final int UNLIMITED = Integer.MAX_VALUE;

    private final String name;
    private final int capacity;
    private final AtomicInteger connectionCount = new AtomicInteger();

    public Server(String name) {
        this(name, UNLIMITED);
    }

    public Server(String name, int capacity) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("server name must not be blank");
        }
        if (capacity <= 0) {
            throw new IllegalArgumentException(
                    "capacity must be positive, but was " + capacity);
        }
        this.name = name;
        this.capacity = capacity;
    }

    public String name() {
        return name;
    }

    public int capacity() {
        return capacity;
    }

    public int connectionCount() {
        return connectionCount.get();
    }

    public boolean canAccept() {
        return connectionCount.get() < capacity;
    }

    void incrementConnections() {
        connectionCount.incrementAndGet();
    }

    void decrementConnections() {
        connectionCount.decrementAndGet();
    }

    @Override
    public String toString() {
        return capacity == UNLIMITED
                ? name + "(" + connectionCount.get() + ")"
                : name + "(" + connectionCount.get() + "/" + capacity + ")";
    }
}
