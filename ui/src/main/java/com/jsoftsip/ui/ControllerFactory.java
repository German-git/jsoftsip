package com.jsoftsip.ui;

import com.jsoftsip.ui.controller.AccountsPaneController;
import com.jsoftsip.ui.controller.ActiveCallsPaneController;
import com.jsoftsip.ui.controller.CallCardController;
import com.jsoftsip.ui.controller.DialerDialogController;
import com.jsoftsip.ui.controller.HistoryPaneController;
import com.jsoftsip.ui.controller.SettingsDialogController;
import com.jsoftsip.ui.controller.TopToolbarController;
import com.jsoftsip.ui.controller.VideoCallDialogController;
import javafx.util.Callback;

import java.util.Map;
import java.util.function.Function;

/**
 * FXML controller factory. Controllers that need the
 * {@link AppContext} are registered in a map, all
 * others fall back to their no-arg constructor.
 * JavaFX propagates this factory to nested loaders
 * created by &lt,fx:include&gt,, so it covers the whole
 * MainView tree.
 */
public final class ControllerFactory implements Callback<Class<?>, Object> {

    private static final Map<Class<?>, Function<AppContext, Object>> CONTROLLERS = Map.of(AccountsPaneController.class,
                                                                                          AccountsPaneController::new,
                                                                                          HistoryPaneController.class,
                                                                                          HistoryPaneController::new,
                                                                                          TopToolbarController.class,
                                                                                          TopToolbarController::new,
                                                                                          ActiveCallsPaneController.class,
                                                                                          ActiveCallsPaneController::new,
                                                                                          SettingsDialogController.class,
                                                                                          SettingsDialogController::new,
                                                                                          DialerDialogController.class,
                                                                                          DialerDialogController::new,
                                                                                          CallCardController.class,
                                                                                          CallCardController::new,
                                                                                          VideoCallDialogController.class,
                                                                                          VideoCallDialogController::new);

    private final AppContext context;

    public ControllerFactory(AppContext context) {
        this.context = context;
    }

    @Override
    public Object call(Class<?> type) {

        Function<AppContext, Object> supplier = CONTROLLERS.get(type);

        if (supplier != null) {

            return supplier.apply(context);
        }

        try {

            return type.getDeclaredConstructor().newInstance();

        } catch (ReflectiveOperationException exception) {

            throw new RuntimeException("Failed to instantiate FXML controller: " + type.getName(), exception);
        }
    }
}
