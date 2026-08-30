package com.jsoftsip.core.history;

import java.util.List;

public interface CallHistoryRepository {

    void save(CallHistoryEntry entry);

    List<CallHistoryEntry> findAll();

    void deleteAll();
}