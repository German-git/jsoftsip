package com.jsoftsip.core.sip;

public class SipAccountData {

    private final long id;

    private final String username;

    private final String password;

    private final String domain;

    private final String transport;

    public SipAccountData(long id, String username, String password, String domain, String transport) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.domain = domain;
        this.transport = transport;
    }

    public long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getDomain() {
        return domain;
    }

    public String getTransport() {
        return transport;
    }
}