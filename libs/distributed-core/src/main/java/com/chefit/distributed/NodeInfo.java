package com.chefit.distributed;

import java.net.InetAddress;
import java.net.UnknownHostException;

// This instance's identity, embedded into the discovery/election/heartbeat messages.
// Lifted from the legacy IdServer (myServerId = PID, ip = localhost) minus the hardcoded
// "eno1" interface that died with multicast. id is a String so it slots straight into the
// peers map (id -> ip) the DiscoveryManager keeps.
public record NodeInfo(String id, String ip, String service) {

    // Builds the identity for the current process: id from this JVM's PID, ip from the
    // local host address. If the host can't be resolved we fall back to loopback rather
    // than fail startup — a node with a usable id still participates in coordination.
    public static NodeInfo create(String service) {
        String id = String.valueOf(ProcessHandle.current().pid());
        String ip;
        try {
            ip = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            ip = "127.0.0.1";
        }
        return new NodeInfo(id, ip, service);
    }
}
