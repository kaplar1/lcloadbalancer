package com.assignment.loadbalancer;

import java.util.concurrent.atomic.AtomicBoolean;

public final class Lease implements AutoCloseable {

    private final Server server;
    private final Runnable onRelease;
    private final AtomicBoolean released = new AtomicBoolean(false);

    Lease(Server server, Runnable onRelease) {
        this.server = server;
        this.onRelease = onRelease;
    }

    public Server server() {
        return server;
    }

    public boolean isReleased() {
        return released.get();
    }

    @Override
    public void close() {
        if (released.compareAndSet(false, true)) {
            onRelease.run();
        }
    }

    @Override
    public String toString() {
        return "Lease[" + server.name() + (released.get() ? ", released]" : ", active]");
    }

}
