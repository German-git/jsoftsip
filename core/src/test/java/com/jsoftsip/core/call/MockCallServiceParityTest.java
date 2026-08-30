package com.jsoftsip.core.call;

import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.sip.SipPeer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Registration-parity tests between {@link MockCallService} and
 * {@link DefaultCallService}: an outgoing mock
 * call must attach its leg to the correlation session and set the
 * peer-local flag exactly like the real backend service does, so
 * the MOCK backend exercises the same aggregation and partner
 * logic instead of bypassing it.
 */
class MockCallServiceParityTest {

    /**
     * Dialing a destination whose username matches a provisioned
     * account is the intra-app case: the peer-local flag must be
     * true, mirroring DefaultCallService.startCall.
     */
    @Test
    void startCallAttachesLegToSessionAndSetsPeerLocalFlag() {

        CallServiceTestFixtures.InMemoryAccounts accounts = new CallServiceTestFixtures.InMemoryAccounts();

        SipAccount alice = CallServiceTestFixtures.account(7L, "alice");

        accounts.createAccount(alice);

        MockCallService service = new MockCallService(new MockSipClient(), accounts,
            new CallServiceTestFixtures.NoOpHistoryService());

        String destination = "alice@sip.local";

        CallLeg leg = service.startCall(alice, destination);

        String key = CallSession.sessionKey(alice, destination);

        assertNotNull(leg.getSession(), "the outgoing mock leg must be attached to its session");

        assertTrue(leg.getSession().getLegs().contains(leg), "the session must contain the outgoing leg");

        assertEquals(key, leg.getSession().getKey(), "the session key must follow the shared correlation rule");

        assertSame(service.sessionsByKey.get(key), leg.getSession(),
                   "the session must be registered under the correlation key");

        assertEquals(SipPeer.isLocalAccount(accounts.getAccounts(), destination), leg.isPeerLocalAccount(),
                     "the peer-local flag must mirror DefaultCallService behavior");
    }

    /**
     * Two outgoing calls to the same peer correlate into one
     * session, so the partner linkage of a logical call is
     * exercised under the MOCK backend too.
     */
    @Test
    void twoCallsToSameDestinationShareSession() {

        CallServiceTestFixtures.InMemoryAccounts accounts = new CallServiceTestFixtures.InMemoryAccounts();

        SipAccount alice = CallServiceTestFixtures.account(7L, "alice");

        accounts.createAccount(alice);

        MockCallService service = new MockCallService(new MockSipClient(), accounts,
            new CallServiceTestFixtures.NoOpHistoryService());

        String destination = "alice@sip.local";

        CallLeg first = service.startCall(alice, destination);

        CallLeg second = service.startCall(alice, destination);

        assertNotNull(first.getSession());

        assertSame(first.getSession(), second.getSession(), "legs to the same peer must share one session");

        assertEquals(2, first.getSession().getLegs().size(), "the session must hold both outgoing legs");
    }
}
