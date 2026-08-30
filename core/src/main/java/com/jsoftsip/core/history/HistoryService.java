package com.jsoftsip.core.history;

import com.jsoftsip.core.call.CallLeg;

import java.util.List;

public interface HistoryService {

    void registerFinishedCall(CallLeg call);

    List<CallHistoryEntry> getHistory();

    void clearAll();

    void addListener(Runnable listener);

    default void close() {
    }
}