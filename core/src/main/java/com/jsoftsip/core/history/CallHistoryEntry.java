package com.jsoftsip.core.history;

import com.jsoftsip.core.call.CallDirection;
import com.jsoftsip.core.call.CallResult;

import java.time.LocalDateTime;

public class CallHistoryEntry {

    private Long id;

    private Long accountId;

    private String accountUsername;

    private String destination;

    private CallResult result;

    private CallDirection direction;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    private long durationSeconds;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getAccountUsername() {
        return accountUsername;
    }

    public void setAccountUsername(String accountUsername) {
        this.accountUsername = accountUsername;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public CallResult getResult() {
        return result;
    }

    public void setResult(CallResult result) {
        this.result = result;
    }

    public CallDirection getDirection() {
        return direction;
    }

    public void setDirection(CallDirection direction) {
        this.direction = direction;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    @Override
    public String toString() {

        return String.format("%s -> %s (%ds)", accountUsername, destination, durationSeconds);
    }
}