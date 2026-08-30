package com.jsoftsip.core.account;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import javax.crypto.SecretKey;

public interface AccountRepository {

    SipAccount save(SipAccount account);

    SipAccount update(SipAccount account);

    void delete(long id);

    void updateStatus(long id, AccountStatus status);

    Optional<SipAccount> findById(long id);

    List<SipAccount> findAll();

    /**
     * Re-encrypts every stored SIP account password from the old master key
     * to the new one inside a single transaction.
     */
    void rekeyCredentials(SecretKey oldKey, SecretKey newKey) throws SQLException;
}