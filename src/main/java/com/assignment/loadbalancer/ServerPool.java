package com.assignment.loadbalancer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ServerPool {

    private ServerPool() {
        // utility class, not instantiable
    }

    /* Pool of servers named "a" - "g" */

    public static List<Server> defaultPool() {
        return named("a", "b", "c", "d", "e", "f", "g");
    }

    public static List<Server> defaultPool(int capacity) {
        return named(capacity, "a", "b", "c", "d", "e", "f", "g");
    }

    public static List<Server> named(String... names) {
        return Arrays.stream(names).map(Server::new).toList();
    }

    public static List<Server> named(int capacity, String... names) {
        return Arrays.stream(names)
                .map(name -> new Server(name, capacity))
                .toList();
    }

    public static List<Server> defaultPoolWithCapacities(Map<String, Integer> capacities) {
        return withCapacities(List.of("a", "b", "c", "d", "e", "f", "g"), capacities);
    }

    public static List<Server> withCapacities(List<String> names,
            Map<String, Integer> capacities) {
        Map<String, Integer> remaining = new LinkedHashMap<>(capacities);

        List<Server> servers = new ArrayList<>(names.size());
        for (String name : names) {
            Integer capacity = remaining.remove(name);
            servers.add(capacity == null ? new Server(name) : new Server(name, capacity));
        }

        if (!remaining.isEmpty()) {
            throw new IllegalArgumentException(
                    "capacity given for unknown server(s): " + remaining.keySet());
        }
        return List.copyOf(servers);
    }
}
