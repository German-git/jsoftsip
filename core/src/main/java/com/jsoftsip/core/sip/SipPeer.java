package com.jsoftsip.core.sip;

import com.jsoftsip.core.account.SipAccount;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Parsed peer reference of a call, either a full SIP URI like
 * "sip:1003@pbx.example" or a bare dial target like "1002",
 * plus the comparison used to decide whether the peer is
 * another account registered in this same application.
 *
 * Local accounts register with AORs whose username may carry a
 * baresip instance prefix (e.g. "2_1003" in the lab setup),
 * while the peer URI reported by the backend for the same
 * account carries the bare number (e.g. "sip:1003@..."). The
 * comparison therefore strips instance prefixes from both
 * sides and requires the peer host to match the account
 * domain, except for bare dial targets, which carry no host.
 */
public final class SipPeer {

    private static final Pattern NUMERIC_INSTANCE_PREFIX = Pattern.compile("^\\d+_");

    private static final Pattern DASH_INSTANCE_PREFIX = Pattern.compile("^-[0-9a-fA-F]{6,}_");

    private static final Pattern DASH_INSTANCE_SUFFIX = Pattern.compile("-[0-9a-fA-F]{6,}$");

    private final String username;

    private final String host;

    private SipPeer(String username, String host) {
        this.username = username;
        this.host = host;
    }

    /**
     * Parses a peer reference. A reference without a host is a
     * bare dial target, which matches any host. Returns empty
     * for null, blank or otherwise unparseable references.
     */
    public static Optional<SipPeer> parse(String peerRef) {
        String trimmed = peerRef == null ? null : peerRef.trim();

        if (trimmed == null || trimmed.isEmpty()) {
            return Optional.empty();
        }

        String withoutScheme = trimmed.replaceFirst("^(?i:sips?):", "");

        String withoutParams = withoutScheme.split(";", 2)[0];

        int atIndex = withoutParams.indexOf('@');

        String rawUsername;
        String rawHost;

        if (atIndex >= 0) {

            rawUsername = withoutParams.substring(0, atIndex);

            rawHost = withoutParams.substring(atIndex + 1);

        } else {

            rawUsername = withoutParams;

            rawHost = null;
        }

        String username = normalizeUsername(rawUsername);

        if (username.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new SipPeer(username, normalizeHost(rawHost)));
    }

    /**
     * True when this peer designates the given account: the
     * host matches the account domain (a bare dial target
     * accepts any host) and the username matches after
     * stripping instance prefixes.
     */
    public boolean matches(SipAccount account) {
        if (account == null || account.getDomain() == null) {
            return false;
        }

        if (host != null && !host.equalsIgnoreCase(account.getDomain())) {
            return false;
        }

        String accountUsername = normalizeUsername(account.getUsername());

        return !accountUsername.isEmpty() && accountUsername.equals(username);
    }

    /**
     * True when the given peer reference designates any of
     * the given accounts.
     */
    public static boolean isLocalAccount(List<SipAccount> accounts, String peerRef) {
        Optional<SipPeer> parsed = parse(peerRef);

        if (parsed.isEmpty()) {
            return false;
        }

        SipPeer peer = parsed.get();

        return accounts.stream().anyMatch(peer::matches);
    }

    /**
     * Strips baresip instance prefixes from a SIP username so
     * that a locally provisioned AOR ("2_1003") and the peer
     * URI of the same account ("sip:1003@host") compare equal.
     * Handles numeric prefixes ("2_", "3_"), dash-prefixed
     * random hex tokens followed by an underscore
     * ("-a1b2c3d4_1003") and dash-suffixed random hex tokens
     * ("1003-a1b2c3d4"). A glued "-<random><number>" form is
     * not handled because the token/number boundary is
     * ambiguous.
     */
    public static String normalizeUsername(String username) {
        if (username == null) {
            return "";
        }

        String normalized = username.trim();

        normalized = NUMERIC_INSTANCE_PREFIX.matcher(normalized).replaceFirst("");

        normalized = DASH_INSTANCE_PREFIX.matcher(normalized).replaceFirst("");

        normalized = DASH_INSTANCE_SUFFIX.matcher(normalized).replaceFirst("");

        return normalized;
    }

    private static String normalizeHost(String host) {
        if (host == null || host.isEmpty()) {
            return null;
        }

        if (host.startsWith("[")) {
            return host;
        }

        return host.replaceFirst(":\\d+$", "");
    }

    public String getUsername() {
        return username;
    }

    public String getHost() {
        return host;
    }
}
