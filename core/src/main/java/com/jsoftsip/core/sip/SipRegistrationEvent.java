package com.jsoftsip.core.sip;

public class SipRegistrationEvent implements SipEvent {

    private final long accountId;

    private final SipRegistrationState state;

    private final String message;

    private final Integer code;

    private final String reason;

    public SipRegistrationEvent(long accountId, SipRegistrationState state, String message) {
        this(accountId, state, message, null, null);
    }

    public SipRegistrationEvent(long accountId, SipRegistrationState state, String message, Integer code,
                                String reason) {
        this.accountId = accountId;
        this.state = state;
        this.message = message;
        this.code = code;
        this.reason = reason;
    }

    public long getAccountId() {
        return accountId;
    }

    public SipRegistrationState getState() {
        return state;
    }

    public String getMessage() {
        return message;
    }

    /**
     * SIP response code of a failed registration (e.g. 401/403),
     * or null when the backend did not report one.
     */
    public Integer getCode() {
        return code;
    }

    /**
     * SIP reason phrase of a failed registration (e.g. "Forbidden"),
     * or null when the backend did not report one.
     */
    public String getReason() {
        return reason;
    }
}