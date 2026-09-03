package com.assignment.loadbalancer;

import java.util.List;

public interface PooledLoadBalancer {
    Lease acquire();

    List<Server> servers();
}
