package com.jsoftsip.core.infrastructure;

import com.jsoftsip.core.account.AccountRepository;
import com.jsoftsip.core.history.CallHistoryRepository;
import com.jsoftsip.core.infrastructure.sqlite.SQLiteAccountRepository;
import com.jsoftsip.core.infrastructure.sqlite.SQLiteCallHistoryRepository;
import com.jsoftsip.core.infrastructure.sqlite.SQLiteSettingRepository;
import com.jsoftsip.core.settings.SettingRepository;

/**
 * Factory that hides the concrete SQLite repository package from other
 * modules. The composition root should depend on the repository interfaces
 * defined in {@code com.jsoftsip.core.account}, {@code ...history} and
 * {@code ...settings}, not on the internal SQLite implementations.
 */
public final class RepositoryFactory {

    private RepositoryFactory() {
    }

    public static AccountRepository accountRepository() {

        return new SQLiteAccountRepository();
    }

    public static CallHistoryRepository callHistoryRepository() {

        return new SQLiteCallHistoryRepository();
    }

    public static SettingRepository settingRepository() {

        return new SQLiteSettingRepository();
    }
}
