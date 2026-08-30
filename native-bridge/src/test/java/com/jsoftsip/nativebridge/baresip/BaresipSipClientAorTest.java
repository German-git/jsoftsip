package com.jsoftsip.nativebridge.baresip;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies the AOR to accountId reverse lookup used by the
 * video frame pipe to route frames to the right account.
 */
class BaresipSipClientAorTest {

    private static final String ALICE_AOR = "sip:alice@example.com";

    private static final String BOB_AOR = "sip:bob@example.com";

    private FakeCtrlConnection connection;

    private BaresipSipClient client;

    @BeforeEach
    void setUp() {

        connection = new FakeCtrlConnection();

        client = new BaresipSipClient(connection);
    }

    @AfterEach
    void tearDown() {

        client.shutdown();
    }

    @Test
    void resolvesTheAccountIdOfARegisteredAor() {

        client.setAccountAor(7, ALICE_AOR);

        assertEquals(7L, client.accountIdForAor(ALICE_AOR));
    }

    @Test
    void returnsNullForAnUnknownAor() {

        assertNull(client.accountIdForAor("sip:ghost@example.com"));
    }

    @Test
    void returnsNullOnceTheUnregisteredEventArrives() {

        client.setAccountAor(7, ALICE_AOR);

        client.unregisterAccount(7);

        // The mapping is dropped when baresip confirms the
        // unregistration via the ua event, mirroring the
        // asynchronous lifecycle of the real backend.
        String payload = "{\"event\":true,\"class\":\"ua\"," + "\"type\":\"UNREGISTERED\"," + "\"accountaor\":\""
            + ALICE_AOR + "\"}";

        connection.injectNetstring(payload.length() + ":" + payload + ",");

        assertNull(client.accountIdForAor(ALICE_AOR));
    }

    @Test
    void keepsTheAorMappingWhileTheUnregisterIsOnlyStarting() {

        client.setAccountAor(7, ALICE_AOR);

        // UNREGISTERING marks the attempt
        // starting. If it never completes (network down) or the UA
        // refreshes right after, later events for this AOR must
        // still resolve their account instead of dying as unknown.
        String payload = "{\"event\":true,\"class\":\"ua\"," + "\"type\":\"UNREGISTERING\"," + "\"accountaor\":\""
            + ALICE_AOR + "\"}";

        connection.injectNetstring(payload.length() + ":" + payload + ",");

        assertEquals(7L, client.accountIdForAor(ALICE_AOR), "UNREGISTERING alone must not drop the AOR mapping");
    }

    @Test
    void routesEachAccountToItsOwnId() {

        client.setAccountAor(7, ALICE_AOR);

        client.setAccountAor(9, BOB_AOR);

        assertEquals(7L, client.accountIdForAor(ALICE_AOR));

        assertEquals(9L, client.accountIdForAor(BOB_AOR));
    }

    @Test
    void normalizeAorStripsSipDefaultPort() {

        assertEquals(ALICE_AOR, client.normalizeAor("sip:alice@example.com:5060"));
    }

    @Test
    void normalizeAorStripsSipsDefaultPort() {

        assertEquals("sips:alice@example.com", client.normalizeAor("sips:alice@example.com:5061"));
    }

    @Test
    void normalizeAorPreservesNonDefaultPort() {

        assertEquals("sip:alice@example.com:5080", client.normalizeAor("sip:alice@example.com:5080"));
    }

    @Test
    void normalizeAorStripsUserinfoPassword() {

        assertEquals("sip:alice@example.com", client.normalizeAor("sip:alice:secret@example.com"));
    }

    @Test
    void normalizeAorStripsParameters() {

        assertEquals(ALICE_AOR, client.normalizeAor("sip:alice@example.com;transport=tcp"));
    }

    @Test
    void normalizeAorDoesNotThrowWhenABracketComesWithoutAnAt() {

        // '[' without '@' used to drive
        // substring(-1) and crash the dispatcher thread
        assertDoesNotThrow(() -> client.normalizeAor("sip:secret:[2001:db8::1]"));

        assertEquals("sip:secret:[2001:db8::1]", client.normalizeAor("sip:secret:[2001:db8::1]"),
                     "without an '@' there is no userinfo to strip");
    }

    @Test
    void normalizeAorKeepsIpv6HostsIntact() {

        assertEquals("sip:alice@[2001:db8::1]", client.normalizeAor("sip:alice@[2001:db8::1]:5060"));

        assertEquals("sip:alice@sip.example.org", client.normalizeAor("sip:alice:pass@sip.example.org"));
    }
}