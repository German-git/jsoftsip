package com.jsoftsip.launcher;

import com.jsoftsip.core.logging.JSoftSipLog;
import com.jsoftsip.nativebridge.baresip.BaresipLogConfig;
import com.jsoftsip.ui.SceneFactory;
import com.jsoftsip.ui.I18n;
import com.jsoftsip.ui.Language;
import com.jsoftsip.ui.ThemeManager;
import com.jsoftsip.ui.UiLoader;
import com.jsoftsip.ui.UiPreferencesService;

import com.jsoftsip.ui.dialog.DialerWindowManager;
import com.jsoftsip.ui.dialog.DialogService;
import com.jsoftsip.ui.dialog.VideoDialogManager;
import com.jsoftsip.ui.window.ExitConfirmationPolicy;
import com.jsoftsip.ui.window.MainWindowRegistry;
import com.jsoftsip.ui.window.ModalWindowTracker;
import com.jsoftsip.ui.UiTaskExecutor;
import com.jsoftsip.ui.window.ShutdownCleanup;
import com.jsoftsip.ui.window.WindowGeometry;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

public class JSoftSipApplication extends Application {

    private static final double DEFAULT_WIDTH = 932;

    private static final double DEFAULT_HEIGHT = 700;

    private JSoftSipContext context;

    private UiPreferencesService preferences;

    private volatile boolean shutdownInProgress;

    private volatile boolean startupFailed;

    // JavaFX entry point required by jpackage to launch the app image
    public static void main(String[] args) {

        Application.launch(JSoftSipApplication.class, args);
    }

    @Override
    public void stop() {

        try {

            if (context != null) {

                context.shutdown();
            }

        } finally {

            ShutdownCleanup.close();
            UiTaskExecutor.close();
        }

        JSoftSipLog.info("Application shutting down");
    }

    @Override
    public void start(Stage primaryStage) {

        // Wire the console appenders and default levels before
        // the first log line, so startup diagnostics never fall
        // back to Logback's unconfigured console
        BaresipLogConfig.ensureConfigured();

        // Install the global uncaught-exception handler before any
        // UI code runs, so exceptions that escape button handlers
        // (e.g. a dead ctrl_tcp connection) are logged and surfaced
        // to the user instead of silently killing the FX thread.
        FxExceptionHandler.install();

        JSoftSipLog.info("Application starting");

        StartupSplash splash = new StartupSplash();
        splash.show();

        Thread.ofVirtual().start(() -> initializeContext(splash, primaryStage));
    }

    private void initializeContext(StartupSplash splash, Stage primaryStage) {

        try {

            context = new JSoftSipContext();

            Platform.runLater(() -> showMainStage(primaryStage, splash));

        } catch (Exception exception) {

            JSoftSipLog.error("Application startup failed", exception);

            Platform.runLater(() -> handleStartupFailure(splash, exception));
        }
    }

    private void showMainStage(Stage primaryStage, StartupSplash splash) {

        if (startupFailed) {

            return;
        }

        try {

            preferences = context.getUiPreferencesService();

            Language language = preferences.getLanguage();
            I18n.setLocale(language.getLocale());

            ThemeManager.applyTheme(preferences.getTheme());

            Scene scene = SceneFactory.create(UiLoader.loadMainView(context));

            primaryStage.setWidth(DEFAULT_WIDTH);
            primaryStage.setHeight(DEFAULT_HEIGHT);

            restoreWindowGeometry(primaryStage, preferences);

            primaryStage.setTitle("JSoftSip");

            primaryStage.getIcons()
                        .add(new Image(JSoftSipApplication.class.getResourceAsStream("/icons/jsoftsip-icon-64px.png")));

            primaryStage.setScene(scene);

            // Modal dialogs opened anywhere need
            // the real owner, so publish the stage before showing it
            MainWindowRegistry.register(primaryStage);

            primaryStage.setOnCloseRequest(event -> onCloseRequest(event, primaryStage));

            // hiding only fires when the close was not consumed,
            // so a declined exit never overwrites the geometry
            primaryStage.setOnHiding(event -> persistWindowGeometry(primaryStage, preferences));

            primaryStage.show();

            splash.close();

        } catch (Exception exception) {

            handleStartupFailure(splash, exception);
        }
    }

    private void handleStartupFailure(StartupSplash splash, Throwable exception) {

        startupFailed = true;

        splash.close();

        DialogService.showError(null, I18n.get("startup.error.title"), I18n.get("startup.error.header"),
                                I18n.format("startup.error.content", exception.getMessage()));

        Platform.exit();
    }

    /**
     * Applies the persisted rectangle over the default size.
     * Malformed stored values are ignored: parse returns
     * empty and the defaults stay.
     */
    private void restoreWindowGeometry(Stage stage, UiPreferencesService preferences) {

        if (!preferences.isRememberWindowGeometry()) {
            return;
        }

        preferences.getWindowGeometry().flatMap(WindowGeometry::parse).ifPresent(geometry -> {

            stage.setX(geometry.x());
            stage.setY(geometry.y());
            stage.setWidth(geometry.width());
            stage.setHeight(geometry.height());
        });
    }

    private void persistWindowGeometry(Stage stage, UiPreferencesService preferences) {

        if (!preferences.isRememberWindowGeometry()) {
            return;
        }

        preferences.saveWindowGeometry(new WindowGeometry(stage.getX(), stage.getY(), stage.getWidth(),
            stage.getHeight()).serialize());
    }

    /**
     * Handles the window close request. If the user confirms,
     * the event is consumed and the cleanup is performed on a
     * background thread so the FX thread is never blocked by
     * network IO. Once the cleanup finishes, the stage is
     * closed programmatically. A second close request while
     * cleanup is in progress is consumed as well: letting it
     * through would close the stage immediately,
     * start stop() while the ShutdownCleanup is still tearing
     * services down, and race both cleanups against each other.
     */
    private void onCloseRequest(WindowEvent event, Stage stage) {

        if (shutdownInProgress) {

            event.consume();

            return;
        }

        if (!confirmExitWithActiveCalls()) {
            event.consume();

            return;
        }

        event.consume();
        shutdownInProgress = true;

        ModalWindowTracker.closeAll();

        DialerWindowManager.closeAll();

        VideoDialogManager.closeAll();

        new ShutdownCleanup(context.getCallService(),
            context.getRegistrationService()).runAsync().whenComplete((result, exception) -> {

                if (exception != null) {

                    JSoftSipLog.error("Shutdown cleanup failed", exception);
                }

                Platform.runLater(stage::close);
            });
    }

    /**
     * Decision (enabled + active call count) is delegated to
     * the javafx-free ExitConfirmationPolicy so it stays
     * unit-testable. Declining consumes the close request.
     */
    private boolean confirmExitWithActiveCalls() {

        int activeCalls = context.getCallService().getActiveCalls().size();

        if (!ExitConfirmationPolicy.shouldConfirmExit(preferences.isConfirmExitWithCalls(), activeCalls)) {
            return true;
        }

        return DialogService.confirm(null, I18n.get("exit.confirmation.title"), I18n.get("exit.confirmation.header"),
                                     I18n.format("exit.confirmation.content", activeCalls));
    }
}
