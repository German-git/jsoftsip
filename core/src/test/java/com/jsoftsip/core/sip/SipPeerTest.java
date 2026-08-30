package com.jsoftsip.core.sip;

import com.jsoftsip.core.account.SipAccount;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the peer parsing and the peer-local comparison used
 * to decide whether a call peer is another account of this
 * same application. Local AOR usernames may carry a baresip
 * instance prefix ("2_1003") while the peer URI carries the
 * bare number ("sip:1003@host"), so both sides are normalized
 * before comparing, and the host must match the account
 * domain (a bare dial target accepts any host).
 */
class SipPeerTest {

    private static SipAccount account(String username, String domain) {
        SipAccount account = new SipAccount();
        account.setUsername(username);
        account.setDomain(domain);
        return account;
    }

    @Test
    void parseFullUriSplitsUserAndHost() {

        Optional<SipPeer> peer = SipPeer.parse("sip:1003@192.168.0.97");

        assertTrue(peer.isPresent(), "a well formed peer URI must parse");

        assertEquals("1003", peer.get().getUsername());

        assertEquals("192.168.0.97", peer.get().getHost());
    }

    @Test
    void parseStripsInstancePrefixFromUser() {

        Optional<SipPeer> peer = SipPeer.parse("sip:2_1003@192.168.0.97");

        assertTrue(peer.isPresent(), "a prefixed peer URI must parse");

        assertEquals("1003", peer.get().getUsername());
    }

    @Test
    void parseBareTargetHasNoHost() {

        Optional<SipPeer> peer = SipPeer.parse("1002");

        assertTrue(peer.isPresent(), "a bare dial target must parse");

        assertEquals("1002", peer.get().getUsername());

        assertNull(peer.get().getHost(), "a bare dial target carries no host");
    }

    @Test
    void parseTargetWithHostKeepsHost() {

        Optional<SipPeer> peer = SipPeer.parse("1002@pbx.example.com");

        assertTrue(peer.isPresent(), "a dial target with host must parse");

        assertEquals("pbx.example.com", peer.get().getHost());
    }

    @Test
    void parseStripsPortAndParamsFromHost() {

        Optional<SipPeer> peer = SipPeer.parse("sip:1002@192.168.0.97:5060;transport=udp");

        assertTrue(peer.isPresent(), "a peer URI with port and params must parse");

        assertEquals("192.168.0.97", peer.get().getHost(), "the port and SIP params must be stripped");
    }

    @Test
    void parseRejectsNullOrBlank() {

        assertTrue(SipPeer.parse(null).isEmpty());

        assertTrue(SipPeer.parse("").isEmpty());

        assertTrue(SipPeer.parse("   ").isEmpty());
    }

    @Test
    void parseRejectsEmptyUser() {

        assertTrue(SipPeer.parse("sip:@192.168.0.97").isEmpty(), "a peer with an empty user cannot be matched");
    }

    @Test
    void normalizeUsernameStripsNumericInstancePrefix() {

        assertEquals("1003", SipPeer.normalizeUsername("2_1003"));

        assertEquals("1002", SipPeer.normalizeUsername("3_1002"));
    }

    @Test
    void normalizeUsernameStripsDashRandomToken() {

        assertEquals("1003", SipPeer.normalizeUsername("-a1b2c3d4_1003"));

        assertEquals("1003", SipPeer.normalizeUsername("1003-a1b2c3d4"));
    }

    @Test
    void normalizeUsernameKeepsPlainUser() {

        assertEquals("user1", SipPeer.normalizeUsername("user1"));

        assertEquals("", SipPeer.normalizeUsername(null));

        assertEquals("", SipPeer.normalizeUsername("2_"));
    }

    @Test
    void matchesAccountOnSameHostAndNumber() {

        SipPeer peer = SipPeer.parse("sip:1003@192.168.0.97").get();

        assertTrue(peer.matches(account("2_1003", "192.168.0.97")),
                   "the prefixed local account must match the" + " unprefixed peer URI");
    }

    @Test
    void matchesAccountWithBareTarget() {

        SipPeer peer = SipPeer.parse("1003").get();

        assertTrue(peer.matches(account("2_1003", "192.168.0.97")), "a bare dial target must match any host");
    }

    @Test
    void doesNotMatchDifferentHost() {

        SipPeer peer = SipPeer.parse("sip:1003@10.0.0.5").get();

        assertFalse(peer.matches(account("2_1003", "192.168.0.97")), "a peer on a different host must not match");
    }

    @Test
    void doesNotMatchDifferentNumber() {

        SipPeer peer = SipPeer.parse("sip:1004@192.168.0.97").get();

        assertFalse(peer.matches(account("2_1003", "192.168.0.97")), "a peer with a different number must not match");
    }

    @Test
    void doesNotMatchAccountWithoutDomain() {

        SipPeer peer = SipPeer.parse("1003").get();

        assertFalse(peer.matches(account("2_1003", null)), "an account without a domain must not match");
    }

    @Test
    void isLocalAccountFindsAnyMatchingAccount() {

        List<SipAccount> accounts = List.of(account("2_1003", "192.168.0.97"), account("2_1002", "192.168.0.97"));

        assertTrue(SipPeer.isLocalAccount(accounts, "sip:1002@192.168.0.97"),
                   "a peer matching any local account is local");
    }

    @Test
    void isLocalAccountRejectsExternalPeer() {

        List<SipAccount> accounts = List.of(account("2_1002", "192.168.0.97"));

        assertFalse(SipPeer.isLocalAccount(accounts, "sip:1002@remote.example.com"),
                    "the same number on a foreign host is not local");

        assertFalse(SipPeer.isLocalAccount(accounts, "sip:5555@192.168.0.97"), "an unknown number is not local");
    }

    @Test
    void isLocalAccountRejectsUnparseablePeer() {

        List<SipAccount> accounts = List.of(account("2_1003", "192.168.0.97"));

        assertFalse(SipPeer.isLocalAccount(accounts, null));

        assertFalse(SipPeer.isLocalAccount(accounts, "   "));
    }
}