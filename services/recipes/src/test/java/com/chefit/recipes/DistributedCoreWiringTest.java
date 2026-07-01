package com.chefit.recipes;

import com.chefit.distributed.NodeInfo;
import com.chefit.distributed.PubSubChannels;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Guards the composite-build wiring: distributed-core's classes must be on this service's
// classpath. If the includeBuild substitution breaks, this fails to compile/run.
class DistributedCoreWiringTest {

    @Test
    void pubSubChannels_isWiredIn() {
        assertEquals("chefit:recipes:discovery", PubSubChannels.discovery("recipes"));
    }

    @Test
    void nodeInfo_isWiredIn() {
        NodeInfo node = NodeInfo.create("recipes");
        assertNotNull(node.id());
        assertEquals("recipes", node.service());
    }
}
