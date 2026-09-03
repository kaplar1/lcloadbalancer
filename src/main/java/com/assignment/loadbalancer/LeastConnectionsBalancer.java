package com.assignment.loadbalancer;

import java.util.List;

public final class LeastConnectionsBalancer implements PooledLoadBalancer {

    private final List<Server> servers;
    private final Object lock = new Object();
    private int cursor;

    public LeastConnectionsBalancer(List<Server> servers) {
        if (servers == null || servers.isEmpty()) {
            throw new IllegalArgumentException("server pool must not be empty");
        }
        this.servers = List.copyOf(servers);
    }

    @Override
    public Lease acquire() {
        synchronized (lock) {
            // The cursor advances on every acquisition, including one that ends in
            // rejection, so rotation stays uniform.
            Server selected = selectLocked(cursor++);
            if (selected == null) {
                throw new NoServerAvailableException(
                        "no server can accept a connection; all " + servers.size()
                                + " are at capacity. This is transient: retry after a"
                                + " short delay.");
            }
            selected.incrementConnections(); // claim, still under the lock
            return new Lease(selected, () -> release(selected));
        }
    }

    private Server selectLocked(int start) {
        int size = servers.size();

        Server best = null;
        for (int step = 0; step < size; step++) {
            Server candidate = servers.get(Math.floorMod(start + step, size));
            if (!candidate.canAccept()) {
                continue; // saturated
            }
            if (best == null || candidate.connectionCount() < best.connectionCount()) {
                best = candidate;
            }
        }
        return best;
    }

    private void release(Server server) {
        synchronized (lock) {
            server.decrementConnections();
        }
    }

    @Override
    public List<Server> servers() {
        return servers;
    }
}
