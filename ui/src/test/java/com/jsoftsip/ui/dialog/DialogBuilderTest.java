package com.jsoftsip.ui.dialog;

import com.jsoftsip.ui.I18n;
import javafx.stage.Window;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DialogBuilderTest {

    @Test
    void defaultTypeIsError() {

        DialogSpec spec = DialogBuilder.builder().build();

        assertEquals(DialogType.ERROR, spec.type());
    }

    @Test
    void errorFactorySetsErrorType() {

        DialogSpec spec = DialogBuilder.error().build();

        assertEquals(DialogType.ERROR, spec.type());
    }

    @Test
    void warningFactorySetsWarningType() {

        DialogSpec spec = DialogBuilder.warning().build();

        assertEquals(DialogType.WARNING, spec.type());
    }

    @Test
    void infoFactorySetsInfoType() {

        DialogSpec spec = DialogBuilder.info().build();

        assertEquals(DialogType.INFO, spec.type());
    }

    @Test
    void successFactorySetsSuccessType() {

        DialogSpec spec = DialogBuilder.success().build();

        assertEquals(DialogType.SUCCESS, spec.type());
    }

    @Test
    void defaultActionIsOk() {

        DialogSpec spec = DialogBuilder.builder().build();

        assertEquals(1, spec.actions().size());
        assertEquals(I18n.get("dialog.ok"), spec.actions().get(0).text());
    }

    @Test
    void okCancelAddsTwoActions() {

        DialogSpec spec = DialogBuilder.builder().okCancel().build();

        assertEquals(2, spec.actions().size());
        assertEquals(I18n.get("dialog.ok"), spec.actions().get(0).text());
        assertEquals(I18n.get("dialog.cancel"), spec.actions().get(1).text());
    }

    @Test
    void yesNoAddsTwoActionsWithStyles() {

        DialogSpec spec = DialogBuilder.builder().yesNo().build();

        assertEquals(2, spec.actions().size());
        assertEquals(I18n.get("dialog.yes"), spec.actions().get(0).text());
        assertEquals("success-button", spec.actions().get(0).styleClass());
        assertEquals(I18n.get("dialog.no"), spec.actions().get(1).text());
        assertEquals("secondary-button", spec.actions().get(1).styleClass());
    }

    @Test
    void defaultModalIsTrue() {

        DialogSpec spec = DialogBuilder.builder().build();

        assertTrue(spec.modal());
    }

    @Test
    void modalCanBeDisabled() {

        DialogSpec spec = DialogBuilder.builder().modal(false).build();

        assertFalse(spec.modal());
    }

    @Test
    void defaultOwnerIsNull() {

        DialogSpec spec = DialogBuilder.builder().build();

        assertNull(spec.owner());
    }

    @Test
    void ownerCanBeSet() {

        Window mockOwner = null;
        DialogSpec spec = DialogBuilder.builder().owner(mockOwner).build();

        assertNull(spec.owner());
    }

    @Test
    void titleIsStored() {

        String title = "Test Title";

        DialogSpec spec = DialogBuilder.error().title(title).content("Content").build();

        assertEquals(title, spec.title());
    }

    @Test
    void contentIsStored() {

        String content = "Test content message";

        DialogSpec spec = DialogBuilder.error().content(content).build();

        assertEquals(content, spec.content());
    }

    @Test
    void headerIsStored() {

        String header = "Test Header";

        DialogSpec spec = DialogBuilder.error().header(header).content("Content").build();

        assertEquals(header, spec.header());
    }

    @Test
    void headerIsNullWhenNotSet() {

        DialogSpec spec = DialogBuilder.error().title("Title").content("Content").build();

        assertNull(spec.header());
    }

    @Test
    void actionsAreCopiedImmutably() {

        DialogSpec spec = DialogBuilder.builder().actions(DialogAction.of("A", "success-button"),
                                                          DialogAction.of("B", "secondary-button"))
                                       .build();

        List<DialogAction> actions = spec.actions();

        assertEquals(2, actions.size());
        assertEquals("A", actions.get(0).text());
        assertEquals("B", actions.get(1).text());

        assertThrows(UnsupportedOperationException.class, () -> actions.add(DialogAction.of("C")));
    }
}
