package com.skcraft.launcher.fx;

import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.fx_legacy.FxLauncherWindow;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static com.skcraft.launcher.util.SharedLocale.tr;

public class LauncherWindow {
    private static final Logger log = Logger.getLogger(LauncherWindow.class.getName());
    private static final AtomicBoolean FX_STARTED = new AtomicBoolean(false);

    private final Launcher launcher;
    private final Stage stage;

    private LauncherWindow(Launcher launcher) {
        this.launcher = launcher;
        this.stage = new Stage();
        stage.setTitle(tr("launcher.title", launcher.getVersion()));
    }

    public static void show(Launcher launcher) {
        runOnFxThread(() -> {
            var window = new LauncherWindow(launcher);
            window.stage.show();
            // window.loadInstances();
        });
    }

    public static void showStartupError(Throwable t) {
        runOnFxThread(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Launcher error");
            alert.setHeaderText("The launcher could not start.");
            alert.setContentText(t.getMessage() == null ? t.toString() : t.getMessage());
            alert.showAndWait();
        });
    }

    private static void runOnFxThread(Runnable runnable) {
        if (FX_STARTED.compareAndSet(false, true)) {
            Platform.startup(() -> {
                Platform.setImplicitExit(false);
                runnable.run();
            });
        } else {
            Platform.runLater(runnable);
        }
    }
}
