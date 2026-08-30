package com.jsoftsip.core.account;

public class CreateAccountRequest {

    private String displayName;

    private String username;

    private String password;

    private String domain;

    private SipTransport transport;

    public SipTransport getTransport() {
        return transport;
    }

    public void setTransport(SipTransport transport) {
        this.transport = transport;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
}
