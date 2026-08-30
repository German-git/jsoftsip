package com.jsoftsip.nativebridge.baresip;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the bidirectional account id to AOR mapping used by
 * call and registration events to resolve accounts, plus the
 * provisioning-time identity validation and auth_pass
 * escaping applied when an account uri is built. The maps are
 * concurrent because they are written from the application
 * threads during provisioning and read from the dispatcher
 * thread while events are processed.
 */
class AccountAorRegistry {

    /**
     * Characters that must never reach the uanew uri through
     * the username or domain: whitespace and control chars
     * corrupt the uri framing, while uri and addr-param
     * delimiters would let a crafted value inject extra
     * address parameters (or a second userinfo) at
     * provisioning time.
     */
    private static final String FORBIDDEN_IDENTITY_CHARS = "\"';/?#:%&=@,<>[]\\";

    private static final char[] HEX_DIGITS = "0123456789ABCDEF".toCharArray();

    private final ConcurrentHashMap<String, Long> aorToAccountId = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<Long, String> accountIdToAor = new ConcurrentHashMap<>();

    void setAccountAor(long accountId, String aor) {

        aorToAccountId.put(aor, accountId);
        accountIdToAor.put(accountId, aor);
    }

    String getAorForAccount(long accountId) {

        return accountIdToAor.get(accountId);
    }

    Long accountIdForAor(String aor) {

        return aorToAccountId.get(aor);
    }

    /**
     * Resolves the AOR of an account or fails fast when the
     * account has no active UA, so a dial against an unknown
     * account never reaches ctrl_tcp.
     */
    String requireAorForAccount(long accountId) {

        String aor = getAorForAccount(accountId);

        if (aor == null) {

            throw new IllegalStateException("No AOR registered for account ID: " + accountId);
        }

        return aor;
    }

    /**
     * Drops both directions of the mapping after baresip
     * confirmed the unregistration via a ua event.
     */
    void removeAor(String aor, long accountId) {

        aorToAccountId.remove(aor);
        accountIdToAor.remove(accountId);
    }

    java.util.Set<String> knownAors() {

        return aorToAccountId.keySet();
    }

    /**
     * Fail-fast provisioning guard: rejects usernames or domains
     * whose content would corrupt the uanew uri (whitespace,
     * control chars, uri and addr-param delimiters). The username
     * may not contain '@' either because the aor is constructed
     * by joining parts. The domain must also be non-blank.
     */
    static void validateProvisioningIdentity(String username, String domain) {

        validateIdentityPart(username, "Username");

        if (domain == null || domain.isBlank()) {
            throw new IllegalArgumentException("Domain must not be blank");
        }

        validateIdentityPart(domain, "Domain");
    }

    private static void validateIdentityPart(String value, String label) {

        if (value == null) {
            throw new IllegalArgumentException(label + " must not be null");
        }

        for (int i = 0; i < value.length(); i++) {

            char c = value.charAt(i);

            if (Character.isWhitespace(c) || Character.isISOControl(c)) {

                throw new IllegalArgumentException(label + " must not contain whitespace or control characters");
            }

            if (FORBIDDEN_IDENTITY_CHARS.indexOf(c) != -1) {

                throw new IllegalArgumentException(label + " must not contain '" + c + "'");
            }
        }
    }

    /**
     * Percent-encodes the password bytes (UTF-8) for safe
     * inclusion as the ,auth_pass addr-param value, leaving RFC
     * 3986 unreserved characters untouched. Baresip unescapes
     * %XX sequences in auth_pass (upstream issue #273) and
     * keeps auth_pass out of the reported accountaor, so the
     * encoded form preserves both the literal password and the
     * aor correlation key. Passwords made only of unreserved
     * characters are returned unchanged.
     */
    static String encodeAuthPass(String password) {

        StringBuilder encoded = new StringBuilder();

        byte[] bytes = password.getBytes(StandardCharsets.UTF_8);

        for (byte b : bytes) {

            int value = b & 0xFF;

            char c = (char) value;

            if (isUnreserved(c)) {

                encoded.append(c);

            } else {

                encoded.append('%').append(HEX_DIGITS[value >>> 4]).append(HEX_DIGITS[value & 0xF]);
            }
        }

        return encoded.toString();
    }

    private static boolean isUnreserved(char c) {

        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '.'
            || c == '_' || c == '~';
    }

    static String normalizeAor(String aor) {

        if (aor == null || aor.isBlank()) {
            return "";
        }

        int semicolonIdx = aor.indexOf(';');

        if (semicolonIdx != -1) {
            aor = aor.substring(0, semicolonIdx);
        }

        boolean isSips = aor.startsWith("sips:");

        boolean isSip = aor.startsWith("sip:");

        int schemeLength = isSips ? 5 : (isSip ? 4 : 0);

        // Defensive: strip a leftover userinfo password
        // (sip:user:pass@host). Only an explicit '@' creates
        // userinfo: the colons of an IPv6 literal sit after
        // the '@', and a '[' without any '@' has no userinfo
        // to strip at all, so no bracket tracking is needed
        // and substring(-1) can never happen
        int atIdx = aor.indexOf('@');

        if (atIdx != -1) {

            int colonIdx = aor.indexOf(':', schemeLength);

            if (colonIdx != -1 && colonIdx < atIdx) {
                aor = aor.substring(0, colonIdx) + aor.substring(atIdx);
            }
        }

        // Strip the default port for the detected scheme so
        // sip:user@host:5060 and sip:user@host are treated as
        // the same AOR. Non-default ports are preserved.
        String defaultPort = isSips ? ":5061" : ":5060";

        if (aor.endsWith(defaultPort)) {
            aor = aor.substring(0, aor.length() - defaultPort.length());
        }

        return aor;
    }
}
