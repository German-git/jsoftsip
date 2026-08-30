package com.jsoftsip.ui.dialog;

import com.jsoftsip.ui.FxTestToolkit;
import javafx.application.Platform;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.Dialog;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Button identification must come from a stable
 * role on the action, never from translated label text, and the
 * cancel action must map to CANCEL_CLOSE so Escape cancels and
 * Enter can never fire a semantically-cancel first button.
 */
class DialogFactoryButtonDataTest {

    @BeforeAll
    static void startFxToolkit() {

        FxTestToolkit.acquire();
    }

    @AfterAll
    static void stopFxToolkit() {

        FxTestToolkit.release();
    }

    @Test
    void cancelActionsCarryTheCancelKindWhilePlainActionsDoNot() {

        assertEquals(DialogAction.Kind.CANCEL, DialogAction.cancel("Cancelar").kind());

        assertEquals(DialogAction.Kind.ACTION, DialogAction.of("Continuar").kind());
    }

    @Test
    void factoryMapsCancelKindToCancelButtonDataAndEverythingElseToOkDone() throws InterruptedException {

        DialogSpec spec = DialogBuilder.builder().actions(DialogAction.of("Delete", "danger-button"),
                                                          DialogAction.cancel("Keep it"))
                                       .build();

        CountDownLatch built = new CountDownLatch(1);

        AtomicReference<Throwable> failure = new AtomicReference<>();

        // Dialog construction owns a stage, so it belongs on the FX thread
        Platform.runLater(() -> {

            try {

                Dialog<DialogAction> dialog = DialogFactory.createDialog(spec);

                var buttonTypes = dialog.getDialogPane().getButtonTypes();

                assertEquals(2, buttonTypes.size());

                assertEquals(ButtonBar.ButtonData.OK_DONE, buttonTypes.get(0).getButtonData(),
                             "the leading destructive action must stay OK_DONE so Enter fires it only when intended");

                assertEquals(ButtonBar.ButtonData.CANCEL_CLOSE, buttonTypes.get(1).getButtonData(),
                             "the cancel action must be CANCEL_CLOSE so Escape resolves it");

            } catch (Throwable throwable) {

                failure.set(throwable);

            } finally {

                built.countDown();
            }
        });

        assertTrue(built.await(5, TimeUnit.SECONDS), "dialog construction must finish on the FX thread");

        if (failure.get() != null) {

            throw new AssertionError(failure.get());
        }
    }

    @Test
    void builderDefaultActionIsAnActionKind() {

        DialogSpec spec = DialogBuilder.builder().okCancel().build();

        assertEquals(DialogAction.Kind.ACTION, spec.actions().get(0).kind());
        assertEquals(DialogAction.Kind.CANCEL, spec.actions().get(1).kind());
    }
}
