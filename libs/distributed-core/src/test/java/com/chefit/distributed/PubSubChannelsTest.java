package com.chefit.distributed;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class PubSubChannelsTest {

    // ---- exact format per purpose ----

    @Test
    void discovery_format() {
        assertEquals("chefit:recipes:discovery", PubSubChannels.discovery("recipes"));
    }

    @Test
    void election_format() {
        assertEquals("chefit:recipes:election", PubSubChannels.election("recipes"));
    }

    @Test
    void heartbeat_format() {
        assertEquals("chefit:recipes:heartbeat", PubSubChannels.heartbeat("recipes"));
    }

    @Test
    void replication_format() {
        assertEquals("chefit:recipes:replication", PubSubChannels.replication("recipes"));
    }

    // ---- holds for every real service name ----

    @ParameterizedTest(name = "service={0}")
    @ValueSource(strings = {"recipes", "auth", "users"})
    void format_holdsForEachService(String svc) {
        assertEquals("chefit:" + svc + ":discovery", PubSubChannels.discovery(svc));
        assertEquals("chefit:" + svc + ":election", PubSubChannels.election(svc));
        assertEquals("chefit:" + svc + ":heartbeat", PubSubChannels.heartbeat(svc));
        assertEquals("chefit:" + svc + ":replication", PubSubChannels.replication(svc));
    }

    // ---- structural invariants ----

    @Test
    void allFourChannelsForOneServiceAreDistinct() {
        String s = "recipes";
        Set<String> channels = Set.of(
                PubSubChannels.discovery(s),
                PubSubChannels.election(s),
                PubSubChannels.heartbeat(s),
                PubSubChannels.replication(s));
        assertEquals(4, channels.size(), "no two purposes may share a channel");
    }

    @Test
    void differentServicesYieldDifferentChannels() {
        assertNotEquals(PubSubChannels.election("recipes"), PubSubChannels.election("auth"));
        assertNotEquals(PubSubChannels.discovery("auth"), PubSubChannels.discovery("users"));
    }

    @Test
    void everyChannelStartsWithChefitPrefix() {
        assertTrue(PubSubChannels.discovery("auth").startsWith("chefit:"));
        assertTrue(PubSubChannels.election("auth").startsWith("chefit:"));
        assertTrue(PubSubChannels.heartbeat("auth").startsWith("chefit:"));
        assertTrue(PubSubChannels.replication("auth").startsWith("chefit:"));
    }

    // ---- guard against bad input ----

    @ParameterizedTest(name = "blank=[{0}]")
    @ValueSource(strings = {"", "   "})
    void blankService_throws(String bad) {
        assertThrows(IllegalArgumentException.class, () -> PubSubChannels.discovery(bad));
    }

    @Test
    void nullService_throws() {
        assertThrows(IllegalArgumentException.class, () -> PubSubChannels.discovery(null));
    }
}
