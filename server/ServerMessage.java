package server;

import java.io.Serializable;
import java.util.UUID;

public class ServerMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Type {
        // Bully Algorithm
        ELECTION, COORDINATOR, SUPPRESS,
        // Primary-Backup Protocol
        REPLICATE,

        ACK
    }

    private Type type;
    private long senderServerId;
    private long timestamp;

    private String coordinatorIP;
    private String senderIP;

    // Payload for write commands (CREATE, MODIFY, DELETE)
    private Request request;

    public ServerMessage(Type type, long senderServerId) {
        this.type = type;
        this.senderServerId = senderServerId;
    }

    public ServerMessage(Type type, long senderServerId, String coordinatorIP) {
        this.type = type;
        this.senderServerId = senderServerId;
        this.coordinatorIP = coordinatorIP;
    }

    public ServerMessage(Request request, long timestamp) {
        this.type = Type.REPLICATE;
        this.timestamp = timestamp;
        this.request = request;
    }

    public ServerMessage(Type type, String ip) {
        this.type = type;
        this.senderIP = ip;
    }

    public Type getType() {
        return this.type;
    }

    public long getSenderId() {
        return this.senderServerId;
    }

    public long getTimestamp() {
        return this.timestamp;
    }

    public String getCoordinatorIP() {
        return this.coordinatorIP;
    }

    public Request getRequest() {
        return this.request;
    }

    public String getSenderIP() {
        return this.senderIP;
    }
}