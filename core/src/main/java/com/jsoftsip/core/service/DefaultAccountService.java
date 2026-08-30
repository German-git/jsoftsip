package com.jsoftsip.core.service;

import com.jsoftsip.core.account.AccountRepository;
import com.jsoftsip.core.account.AccountStatus;
import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.exception.RepositoryException;
import com.jsoftsip.core.infrastructure.crypto.MasterKeyManager;

import javax.crypto.SecretKey;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

public class DefaultAccountService implements AccountService {

    private final AccountRepository repository;

    private final List<AccountStatusListener> listeners = new CopyOnWriteArrayList<>();

    public DefaultAccountService(AccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public SipAccount createAccount(SipAccount account) {
        return repository.save(account);
    }

    @Override
    public SipAccount updateAccount(SipAccount account) {
        return repository.update(account);
    }

    @Override
    public void deleteAccount(long id) {
        repository.delete(id);
    }

    @Override
    public List<SipAccount> getAccounts() {
        return repository.findAll();
    }

    @Override
    public void updateStatus(long accountId, AccountStatus status) {

        repository.updateStatus(accountId, status);

        notifyListeners(accountId);
    }

    @Override
    public void addListener(AccountStatusListener listener) {

        listeners.add(listener);
    }

    @Override
    public void removeListener(AccountStatusListener listener) {

        listeners.remove(listener);
    }

    @Override
    public Optional<SipAccount> findById(long id) {
        return repository.findById(id);
    }

    @Override
    public void rotateMasterKey() {

        SecretKey oldKey = MasterKeyManager.loadKey();

        SecretKey newKey = MasterKeyManager.prepareRotation();

        try {

            repository.rekeyCredentials(oldKey, newKey);

        } catch (SQLException exception) {

            MasterKeyManager.abortRotation();

            throw new RepositoryException("Credential re-encryption failed", exception);
        }

        MasterKeyManager.commitRotation();
    }

    private void notifyListeners(long accountId) {

        listeners.forEach(listener -> listener.onAccountStatusChanged(accountId));
    }

}