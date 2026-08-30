package com.jsoftsip.ui.baresip;

import com.jsoftsip.core.call.CallLeg;
import com.jsoftsip.core.settings.baresip.BaresipOption;
import com.jsoftsip.core.settings.baresip.BaresipSettingsFacade;
import com.jsoftsip.core.settings.baresip.BaresipSettingsFacade.ApplyResult;
import com.jsoftsip.ui.RecordingCallService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaresipSettingsApplierTest {

    // --- fakes ------------------------------------------------------------

    /**
     * Captures the values handed to apply() and returns a fixed
     * outcome, mirroring the recording pattern used by the
     * form-model tests.
     */
    private static final class RecordingFacade implements BaresipSettingsFacade {

        final Map<String, String> captured = new HashMap<>();

        final ApplyResult nextResult;

        private RecordingFacade(ApplyResult nextResult) {

            this.nextResult = nextResult;
        }

        @Override
        public ApplyResult apply(Map<String, String> values) {

            captured.clear();

            captured.putAll(values);

            return nextResult;
        }

        @Override
        public String previewPatchedConfig(Map<String, String> pending) {

            return "";
        }

        @Override
        public List<String> readBaseConfigLines() {

            return List.of();
        }

        @Override
        public String previewPatchedConfig(Map<String, String> pending, List<String> baseLines) {

            return "";
        }

        @Override
        public List<String> listSinks() {

            return List.of();
        }

        @Override
        public List<String> listSources() {

            return List.of();
        }
    }

    // --- model helpers ----------------------------------------------------

    private static BaresipSettingsFormModel validForm() {

        return new BaresipSettingsFormModel(key -> Optional.empty());
    }

    private static BaresipSettingsFormModel invalidForm() {

        BaresipSettingsFormModel model = validForm();

        model.setValue(BaresipOption.CALL_MAX_CALLS, "-1");

        return model;
    }

    // --- tests ------------------------------------------------------------

    @Test
    void mockBackendReturnsNoBaresipWithoutCallingTheFacade() {

        BaresipSettingsApplier applier = new BaresipSettingsApplier(null, new RecordingCallService());

        BaresipSettingsApplier.Outcome outcome = applier.apply(validForm(), true);

        assertInstanceOf(BaresipSettingsApplier.Outcome.NoBaresip.class, outcome);
    }

    @Test
    void invalidFormIsReportedAndNeverReachesBaresip() {

        RecordingFacade facade = new RecordingFacade(ApplyResult.APPLIED);

        BaresipSettingsApplier applier = new BaresipSettingsApplier(facade, new RecordingCallService());

        BaresipSettingsApplier.Outcome outcome = applier.apply(invalidForm(), true);

        assertInstanceOf(BaresipSettingsApplier.Outcome.Invalid.class, outcome);

        assertTrue(facade.captured.isEmpty(), "the facade must not be called on invalid form");
    }

    @Test
    void activeCallsWithoutRestartConsentAreCancelled() {

        RecordingFacade facade = new RecordingFacade(ApplyResult.APPLIED);

        RecordingCallService calls = new RecordingCallService();

        calls.addActiveCall(new CallLeg());

        BaresipSettingsApplier applier = new BaresipSettingsApplier(facade, calls);

        BaresipSettingsApplier.Outcome outcome = applier.apply(validForm(), false);

        assertInstanceOf(BaresipSettingsApplier.Outcome.RestartCancelled.class, outcome);

        assertTrue(facade.captured.isEmpty(), "cancelled apply must not reach baresip");
    }

    @Test
    void activeCallsWithRestartConsentAreApplied() {

        RecordingFacade facade = new RecordingFacade(ApplyResult.APPLIED);

        RecordingCallService calls = new RecordingCallService();

        calls.addActiveCall(new CallLeg());

        BaresipSettingsApplier applier = new BaresipSettingsApplier(facade, calls);

        BaresipSettingsApplier.Outcome outcome = applier.apply(validForm(), true);

        assertInstanceOf(BaresipSettingsApplier.Outcome.Applied.class, outcome);

        assertEquals(ApplyResult.APPLIED, ((BaresipSettingsApplier.Outcome.Applied) outcome).result());

        assertEquals(BaresipOption.values().length, facade.captured.size(),
                     "the whole settings map must reach the facade");
    }

    @Test
    void restoredBackupIsPassedThrough() {

        RecordingFacade facade = new RecordingFacade(ApplyResult.RESTORED_BACKUP);

        BaresipSettingsApplier applier = new BaresipSettingsApplier(facade, new RecordingCallService());

        BaresipSettingsApplier.Outcome outcome = applier.apply(validForm(), true);

        assertInstanceOf(BaresipSettingsApplier.Outcome.Applied.class, outcome);

        assertEquals(ApplyResult.RESTORED_BACKUP, ((BaresipSettingsApplier.Outcome.Applied) outcome).result());
    }

    @Test
    void noActiveCallsDoesNotRequireRestartConsent() {

        RecordingFacade facade = new RecordingFacade(ApplyResult.APPLIED);

        BaresipSettingsApplier applier = new BaresipSettingsApplier(facade, new RecordingCallService());

        BaresipSettingsApplier.Outcome outcome = applier.apply(validForm(), false);

        assertInstanceOf(BaresipSettingsApplier.Outcome.Applied.class, outcome);
    }
}
