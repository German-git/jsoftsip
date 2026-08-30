package com.jsoftsip.ui.controller;

import com.jsoftsip.core.account.SipAccount;
import com.jsoftsip.core.registration.RegistrationListener;
import com.jsoftsip.core.registration.RegistrationService;
import com.jsoftsip.core.service.AccountService;
import com.jsoftsip.core.service.AccountStatusListener;
import com.jsoftsip.ui.AppContext;
import com.jsoftsip.ui.DirectExecutorService;
import com.jsoftsip.ui.FxTestToolkit;
import com.jsoftsip.ui.I18n;
import com.jsoftsip.ui.MockAppContext;
import com.jsoftsip.ui.SelectedAccountContext;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test: the accounts context menu is
 * long-lived on the main window, so its items must re-translate
 * when the locale changes instead of keeping the language that was
 * active when the pane was built.
 */
@Execution(ExecutionMode.SAME_THREAD)
class AccountsPaneContextMenuI18nTest {

    @BeforeAll
    static void startFxToolkit() {

        FxTestToolkit.acquire();
    }

    @AfterAll
    static void stopFxToolkit() {

        FxTestToolkit.release();
    }

    private Locale originalLocale;

    private ContextMenu contextMenu;

    @BeforeEach
    void setUp() throws IOException {

        originalLocale = I18n.getLocale();

        AppContext context = new MockAppContext(null, new DirectExecutorService()) {

            @Override
            public AccountService getAccountService() {

                return new EmptyAccountService();
            }

            @Override
            public RegistrationService getRegistrationService() {

                return new NoopRegistrationService();
            }

            @Override
            public SelectedAccountContext getSelectedAccountContext() {

                return new SelectedAccountContext();
            }
        };

        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/AccountsPane.fxml"));

        loader.setResources(I18n.bundle());

        loader.setControllerFactory(type -> new AccountsPaneController(context));

        loader.load();

        ListView<?> listView = (ListView<?>) loader.getNamespace().get("accountsListView");

        contextMenu = listView.getContextMenu();

        assertNotNull(contextMenu, "the pane must install its context menu during initialize");
    }

    @AfterEach
    void tearDown() {

        I18n.setLocale(originalLocale);
    }

    @Test
    void contextMenuItemsRetranslateWhenTheLocaleChanges() throws InterruptedException {

        I18n.setLocale(Locale.forLanguageTag("es"));

        flushFx();

        String spanishRegister = contextMenu.getItems().get(0).getText();

        assertEquals(I18n.get("accounts.register"), spanishRegister, "Spanish text must be applied");

        I18n.setLocale(Locale.ENGLISH);

        flushFx();

        String englishRegister = contextMenu.getItems().get(0).getText();

        assertEquals(I18n.get("accounts.register"), englishRegister, "English text must follow the locale switch");

        assertTrue(!englishRegister.equals(spanishRegister), "the item text must actually change between locales");
    }

    /**
     * Drains pending Platform.runLater tasks: runLater is FIFO,
     * so once this sentinel executes, every task queued before it
     * has already run.
     */
    private static void flushFx() throws InterruptedException {

        CountDownLatch drained = new CountDownLatch(1);

        Platform.runLater(drained::countDown);

        assertTrue(drained.await(5, TimeUnit.SECONDS), "the FX queue must drain within the timeout");
    }

    /**
     * Account service fake with no rows: enough for the pane to
     * build and refresh without touching a database.
     */
    private static final class EmptyAccountService implements AccountService {

        private final AtomicLong sequence = new AtomicLong(1);

        @Override
        public SipAccount createAccount(SipAccount account) {

            account.setId(sequence.getAndIncrement());

            return account;
        }

        @Override
        public SipAccount updateAccount(SipAccount account) {

            return account;
        }

        @Override
        public void deleteAccount(long id) {
        }

        @Override
        public List<SipAccount> getAccounts() {

            return List.of();
        }

        @Override
        public void updateStatus(long accountId, com.jsoftsip.core.account.AccountStatus status) {
        }

        @Override
        public void addListener(AccountStatusListener listener) {
        }

        @Override
        public void removeListener(AccountStatusListener listener) {
        }

        @Override
        public void rotateMasterKey() {
        }

        @Override
        public Optional<SipAccount> findById(long id) {

            return Optional.empty();
        }
    }

    /**
     * Registration service fake: the pane only registers listeners,
     * never drives registrations in this test.
     */
    private static final class NoopRegistrationService implements RegistrationService {

        @Override
        public void registerAccount(SipAccount account) {
        }

        @Override
        public void unregisterAccount(long accountId) {
        }

        @Override
        public void reprovisionAccount(SipAccount account) {
        }

        @Override
        public List<SipAccount> getRegisteredAccounts() {

            return List.of();
        }

        @Override
        public void addRegistrationListener(RegistrationListener listener) {
        }

        @Override
        public void removeRegistrationListener(RegistrationListener listener) {
        }
    }
}
