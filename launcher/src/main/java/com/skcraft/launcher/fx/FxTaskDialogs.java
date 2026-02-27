package com.skcraft.launcher.fx;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.skcraft.concurrency.ProgressObservable;
import com.skcraft.launcher.LauncherException;
import com.skcraft.launcher.util.SharedLocale;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import javafx.util.Duration;
import lombok.extern.java.Log;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;

import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;

@Log
public final class FxTaskDialogs {

    private FxTaskDialogs() {
    }

    public static void showProgress(ListenableFuture<?> future, String title, String initialStatus) {
        runOnFxThread(() -> {
            Stage stage = new Stage(StageStyle.UTILITY);
            stage.initModality(Modality.NONE);
            stage.setTitle(title);
            stage.setResizable(false);

            Label status = new Label(initialStatus == null ? "" : initialStatus);
            ProgressBar bar = new ProgressBar(-1);
            bar.setMaxWidth(Double.MAX_VALUE);
            TextArea details = new TextArea();
            details.setEditable(false);
            details.setWrapText(false);
            details.setPrefRowCount(8);
            details.setPrefHeight(220);
            details.setMaxHeight(Double.MAX_VALUE);
            details.setMaxWidth(Double.MAX_VALUE);
            details.setStyle("-fx-font-family: monospace;");
            details.setVisible(false);
            details.setManaged(false);
            Region filler = new Region();
            BorderPane root = new BorderPane();

            Button cancel = new Button(SharedLocale.tr("button.cancel"));
            cancel.setOnAction(e -> future.cancel(true));
            Button toggleDetails = new Button(SharedLocale.tr("progress.details"));
            toggleDetails.setOnAction(e -> {
                boolean show = !details.isVisible();
                details.setVisible(show);
                details.setManaged(show);
                root.setCenter(show ? details : filler);
                toggleDetails.setText(SharedLocale.tr(show ? "progress.less" : "progress.details"));
                stage.sizeToScene();
            });

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox buttons = new HBox(8, toggleDetails, spacer, cancel);

            VBox header = new VBox(10, status, bar);
            root.setPadding(new Insets(12));
            root.setTop(header);
            root.setCenter(filler);
            root.setBottom(buttons);
            stage.setScene(new Scene(root));
            stage.sizeToScene();
            stage.show();
            stage.setMinWidth(stage.getWidth());
            stage.setMinHeight(stage.getHeight());

            Timeline poller = new Timeline(new KeyFrame(Duration.millis(150), e -> {
                if (future instanceof ProgressObservable) {
                    ProgressObservable po = (ProgressObservable) future;
                    String s = po.getStatus();
                    if (s != null && !s.trim().isEmpty()) {
                        int newline = s.indexOf('\n');
                        status.setText(newline >= 0 ? s.substring(0, newline) : s);
                        details.setText(s);
                    }
                    double p = po.getProgress();
                    bar.setProgress((p >= 0 && p <= 1) ? p : -1);
                }
            }));
            poller.setCycleCount(Timeline.INDEFINITE);
            poller.play();

            future.addListener(() -> runOnFxThread(() -> {
                poller.stop();
                stage.close();
            }), sameThreadExecutor());
        });
    }

    public static void addErrorDialogCallback(ListenableFuture<?> future) {
        Futures.addCallback(future, new FutureCallback<Object>() {
            @Override
            public void onSuccess(Object result) {
            }

            @Override
            public void onFailure(Throwable t) {
                if (t instanceof InterruptedException || t instanceof CancellationException) {
                    return;
                }

                String message;
                Throwable details = t;
                if (t instanceof LauncherException) {
                    message = t.getLocalizedMessage();
                    details = t.getCause();
                } else {
                    message = t.getLocalizedMessage();
                    if (message == null || message.trim().isEmpty()) {
                        message = SharedLocale.tr("errors.genericError");
                    }
                }
                log.log(Level.WARNING, "Task failed", details);
                showError(message, details);
            }
        }, sameThreadExecutor());
    }

    public static void showError(String message, Throwable details) {
        runOnFxThread(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.initStyle(StageStyle.UTILITY);
            Window owner = findOwnerWindow();
            if (owner != null) {
                alert.initOwner(owner);
                alert.initModality(Modality.WINDOW_MODAL);
            }
            alert.setTitle(SharedLocale.tr("errorTitle"));
            alert.setHeaderText(message);
            if (details != null) {
                alert.setContentText(details.getMessage() == null ? details.toString() : details.getMessage());
            }
            alert.showAndWait();
        });
    }

    private static Window findOwnerWindow() {
        Window firstShowing = null;
        for (Window window : Window.getWindows()) {
            if (window != null && window.isShowing()) {
                if (firstShowing == null) {
                    firstShowing = window;
                }
                if (window.isFocused()) {
                    return window;
                }
            }
        }
        return firstShowing;
    }

    private static void runOnFxThread(Runnable runnable) {
        if (Platform.isFxApplicationThread()) {
            runnable.run();
            return;
        }

        try {
            Platform.runLater(runnable);
        } catch (IllegalStateException e) {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(() -> {
                try {
                    runnable.run();
                } finally {
                    latch.countDown();
                }
            });
            try {
                latch.await();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
