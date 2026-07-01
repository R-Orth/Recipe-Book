package com.chefit.distributed;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

class NodeInfoTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // ---- create(): identity derivation ----

    @Test
    void create_idIsCurrentProcessPid() {
        NodeInfo node = NodeInfo.create("recipes");
        assertEquals(String.valueOf(ProcessHandle.current().pid()), node.id());
    }

    @Test
    void create_idIsStableAcrossCalls() {
        // Same process => same id, regardless of service.
        assertEquals(NodeInfo.create("recipes").id(), NodeInfo.create("auth").id());
    }

    @Test
    void create_ipIsNonBlankAndMatchesLocalHost() throws Exception {
        NodeInfo node = NodeInfo.create("recipes");
        assertNotNull(node.ip());
        assertFalse(node.ip().isBlank());
        assertEquals(InetAddress.getLocalHost().getHostAddress(), node.ip());
    }

    @Test
    void create_carriesServiceNameUnchanged() {
        assertEquals("users", NodeInfo.create("users").service());
    }

    @Test
    void create_differentServices_shareIdAndIp_differInService() {
        NodeInfo a = NodeInfo.create("recipes");
        NodeInfo b = NodeInfo.create("auth");
        assertEquals(a.id(), b.id());
        assertEquals(a.ip(), b.ip());
        assertNotEquals(a.service(), b.service());
    }

    // ---- value semantics ----

    @Test
    void valueEquality() {
        assertEquals(
                new NodeInfo("1", "127.0.0.1", "recipes"),
                new NodeInfo("1", "127.0.0.1", "recipes"));
    }

    // ---- JSON payload shape (NodeInfo travels inside pub/sub messages) ----

    @Test
    void jsonRoundTrip_preservesAllFields() throws Exception {
        NodeInfo original = new NodeInfo("123", "10.0.0.5", "recipes");
        String json = mapper.writeValueAsString(original);
        NodeInfo back = mapper.readValue(json, NodeInfo.class);
        assertEquals(original, back);
    }

    @Test
    void json_exposesIdIpServiceFields() throws Exception {
        String json = mapper.writeValueAsString(new NodeInfo("123", "10.0.0.5", "recipes"));
        assertTrue(json.contains("\"id\":\"123\""), json);
        assertTrue(json.contains("\"ip\":\"10.0.0.5\""), json);
        assertTrue(json.contains("\"service\":\"recipes\""), json);
    }
}
