package com.chefit.distributed;

// Central naming for the Redis pub/sub channels each service cluster coordinates over.
// A publisher and a subscriber in different processes must compute the exact same string,
// so this is the single source of truth: chefit:{service}:{purpose}.
public final class PubSubChannels {

    private static final String PREFIX = "chefit:";

    private PubSubChannels() {}

    public static String discovery(String service) {
        return channel(service, "discovery");
    }

    public static String election(String service) {
        return channel(service, "election");
    }

    public static String heartbeat(String service) {
        return channel(service, "heartbeat");
    }

    public static String replication(String service) {
        return channel(service, "replication");
    }

    private static String channel(String service, String purpose) {
        if (service == null || service.isBlank()) {
            throw new IllegalArgumentException("service must not be null or blank");
        }
        return PREFIX + service + ":" + purpose;
    }
}
