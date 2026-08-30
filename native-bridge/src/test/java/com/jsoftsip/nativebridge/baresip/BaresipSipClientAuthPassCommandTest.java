package com.jsoftsip.nativebridge.baresip;

import com.jsoftsip.core.sip.SipAccountData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization baseline for Item 11 (credentials in clear text over ctrl_tcp).
 *
 * <p>This test locks the CURRENT behaviour of {@code BaresipSipClient.registerAccount()}:
 * the SIP password is sent inline inside the {@code /uanew} command as the
 * {@code ,auth_pass=} address parameter. It runs fully in memory against
 * {@link FakeCtrlConnection}, so it needs no live Baresip process and no real
 * SIP account.</p>
 *
 * <p>Why this shape and not the "uanew without auth_pass" variant: Baresip's
 * {@code ui_password_prompt()} is a UI-module callback, not a ctrl_tcp command, so
 * JSoftSIP has no programmatic way to feed the password after omitting it from
 * {@code /uanew}. A test asserting that flow would encode a mechanism Baresip does
 * not expose over ctrl_tcp and would stay red indefinitely. This baseline must be
 * flipped only once a real backend mechanism exists (a supported password file,
 * a dedicated ctrl_tcp command, or an accepted documented limitation).</p>
 *
 * <p>The assertion checks the command shape only, it never prints or asserts the
 * concrete password value.</p>
 */
class BaresipSipClientAuthPassCommandTest {

    @Test
    void registerAccountSendsUanewWithAuthPassInClearText() {

        // Synthetic account data used only to verify the command shape.
        // No SIP server is contacted, the command is captured in memory.
        SipAccountData account = new SipAccountData(1L, "1002", "s3cret", "192.168.0.97", "UDP");

        FakeCtrlConnection connection = new FakeCtrlConnection();

        BaresipSipClient client = new BaresipSipClient(connection);

        client.registerAccount(account);

        List<String> commands = connection.sentCommands();

        assertEquals(1, commands.size(), "exactly one ctrl_tcp command must be sent");

        String command = commands.get(0);

        assertTrue(command.contains("uanew"), "the command sent to Baresip must be /uanew");
        assertTrue(command.contains("auth_pass="),
                   "CURRENT behaviour (Item 11 gap): the password travels inside the /uanew command over ctrl_tcp");
    }
}
