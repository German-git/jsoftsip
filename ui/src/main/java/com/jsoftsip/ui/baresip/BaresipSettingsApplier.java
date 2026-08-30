package com.jsoftsip.ui.baresip;

import com.jsoftsip.core.call.CallLeg;
import com.jsoftsip.core.call.CallService;
import com.jsoftsip.core.settings.baresip.BaresipOption;
import com.jsoftsip.core.settings.baresip.BaresipSettingsFacade;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Owns the baresip apply policy that used to live inline in
 * {@code SettingsDialogController.save()}: applying settings
 * restarts baresip and therefore drops every active call.
 *
 * <p>The controller stays the JavaFX adapter (reads the controls,
 * confirms the restart with the user, publishes the outcome), this
 * class is framework-agnostic and therefore testable without the
 * JavaFX toolkit.
 *
 * <p>Policy, in order:
 * <ol>
 *   <li>on the MOCK backend there is no baresip process, so the
 *   facade is absent and apply is a no-op,</li>
 *   <li>the form is validated first and a broken form never reaches
 *   the process,</li>
 *   <li>when active calls exist the user must have approved the
 *   restart, otherwise apply is cancelled before touching baresip,</li>
 *   <li>otherwise the facade applies, restarts baresip and restores
 *   the last known-good config on failure,</li>
 * </ol>
 */
public final class BaresipSettingsApplier {

    private final BaresipSettingsFacade facade;

    private final CallService callService;

    public BaresipSettingsApplier(BaresipSettingsFacade facade, CallService callService) {

        this.facade = facade;

        this.callService = callService;
    }

    /**
     * Runs the apply policy for the given form. Does not block the
     * caller's thread on the facade except for the apply call itself,
     * which serialises the baresip restart, the controller owns the
     * background threading around this method.
     */
    public Outcome apply(BaresipSettingsFormModel formModel, boolean restartApproved) {

        Objects.requireNonNull(formModel, "formModel");
        Objects.requireNonNull(callService, "callService");

        // MOCK backend: no process to reconfigure.
        if (facade == null) {
            return Outcome.NO_BARESIP;
        }

        // Reject invalid forms before they can reach baresip.
        if (!formModel.isValid()) {
            return new Outcome.Invalid(formModel.validationErrors());
        }

        // A restart drops active calls, the controller must have
        // obtained explicit consent when the form was dirty.
        List<CallLeg> activeCalls = callService.getActiveCalls();

        if (!restartApproved && !activeCalls.isEmpty()) {
            return Outcome.RESTART_CANCELLED;
        }

        BaresipSettingsFacade.ApplyResult result = facade.apply(formModel.toSettingsMap());

        return new Outcome.Applied(result);
    }

    /**
     * Framework-agnostic result of a {@link #apply apply} attempt.
     */
    public sealed interface Outcome {

        Outcome NO_BARESIP = new NoBaresip();

        Outcome RESTART_CANCELLED = new RestartCancelled();

        record Applied(BaresipSettingsFacade.ApplyResult result) implements Outcome {
        }

        record Invalid(Map<BaresipOption, String> errors) implements Outcome {
        }

        record NoBaresip() implements Outcome {
        }

        record RestartCancelled() implements Outcome {
        }
    }
}
