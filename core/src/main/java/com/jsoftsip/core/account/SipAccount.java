package com.jsoftsip.core.account;

public class SipAccount {

    private Long id;

    private String displayName;

    private String username;

    private String password;

    private String domain;

    private SipTransport transport;

    private volatile AccountStatus status;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public SipTransport getTransport() {
        return transport;
    }

    public void setTransport(SipTransport transport) {
        this.transport = transport;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }
}