/*
 * SKCraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.launch;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.skcraft.concurrency.ObservableFuture;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.auth.Session;
import com.skcraft.launcher.fx_legacy.FxAccountDialog;
import com.skcraft.launcher.fx_legacy.FxTaskDialogs;
import com.skcraft.launcher.launch.LaunchOptions.UpdatePolicy;
import com.skcraft.launcher.launch.runtime.JavaRuntime;
import com.skcraft.launcher.model.minecraft.JavaVersion;
import com.skcraft.launcher.persistence.Persistence;
import com.skcraft.launcher.update.Updater;
import com.skcraft.launcher.util.SharedLocale;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import lombok.RequiredArgsConstructor;
import lombok.extern.java.Log;
import org.apache.commons.io.FileUtils;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.function.BiPredicate;
import java.util.logging.Level;

import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;
import static com.skcraft.launcher.util.SharedLocale.tr;

@Log
public class LaunchSupervisor {

    private final Launcher launcher;

    public LaunchSupervisor(Launcher launcher) {
        this.launcher = launcher;
    }

    public void launch(LaunchOptions options) {
        final Instance instance = options.getInstance();
        final LaunchListener listener = options.getListener();

        try {
            boolean update = options.getUpdatePolicy().isUpdateEnabled() && instance.isUpdatePending();

            // Store last access date
            Date now = new Date();
            instance.setLastAccessed(now);
            Persistence.commitAndForget(instance);

            // Perform login
            final Session session;
            if (options.getSession() != null) {
                session = options.getSession();
            } else {
                session = FxAccountDialog.showAccountRequest(null, launcher);
                if (session == null) {
                    return;
                }
            }

            // If we have to update, we have to update
            if (!instance.isInstalled()) {
                update = true;
            }

            if (update) {
                // Execute the updater
                Updater updater = new Updater(launcher, instance);
                updater.setOnline(options.getUpdatePolicy() == UpdatePolicy.ALWAYS_UPDATE || session.isOnline());
                ObservableFuture<Instance> future = new ObservableFuture<>(
                        launcher.getExecutor().submit(updater), updater);

                // Show progress
                FxTaskDialogs.showProgress(future, SharedLocale.tr("launcher.updatingTitle"), tr("launcher.updatingStatus", instance.getTitle()));
                FxTaskDialogs.addErrorDialogCallback(future);

                // Update the list of instances after updating
                future.addListener(listener::instancesUpdated, sameThreadExecutor());

                // On success, launch also
                Futures.addCallback(future, new FutureCallback<>() {
                    @Override
                    public void onSuccess(Instance result) {
                        launch(instance, session, listener);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                    }
                }, sameThreadExecutor());
            } else {
                launch(instance, session, listener);
            }
        } catch (ArrayIndexOutOfBoundsException e) {
            FxTaskDialogs.showError(SharedLocale.tr("launcher.noInstanceError"), e);
        }
    }

    private void launch(Instance instance, Session session, final LaunchListener listener) {
        final File extractDir = launcher.createExtractDir();

        // Get the process
        Runner task = new Runner(launcher, instance, session, extractDir, new RuntimeVerifier(instance));
        ObservableFuture<Process> processFuture = new ObservableFuture<Process>(
                launcher.getExecutor().submit(task), task);

        // Show process for the process retrieval
        FxTaskDialogs.showProgress(
                processFuture, SharedLocale.tr("launcher.launchingTitle"), tr("launcher.launchingStatus", instance.getTitle()));

        // If the process is started, get rid of this window
        Futures.addCallback(processFuture, new FutureCallback<>() {
            @Override
            public void onSuccess(Process result) {
                listener.gameStarted();
            }

            @Override
            public void onFailure(Throwable t) {
            }
        });

        // Watch the created process
        ListenableFuture<Void> future = Futures.transform(
                processFuture, new LaunchProcessHandler(launcher), launcher.getExecutor());
        FxTaskDialogs.addErrorDialogCallback(future);

        // Clean up at the very end
        future.addListener(() -> {
            try {
                log.info("Process ended; cleaning up " + extractDir.getAbsolutePath());
                FileUtils.deleteDirectory(extractDir);
            } catch (IOException e) {
                log.log(Level.WARNING, "Failed to clean up " + extractDir.getAbsolutePath(), e);
            }
        }, sameThreadExecutor());

        // Hook up launch listener
        Futures.addCallback(future, new FutureCallback<>() {
            @Override
            public void onSuccess(@Nullable Void result) {
                // gameStarted was only invoked on success above, so only call gameClosed on success
                listener.gameClosed();
            }

            @Override
            public void onFailure(Throwable t) {
                // likely user cancellation
                if (!(t instanceof CancellationException)) {
                    log.info("Process failure: " + t.getLocalizedMessage());
                }
            }
        }, sameThreadExecutor());
    }

    @RequiredArgsConstructor
    static class RuntimeVerifier implements BiPredicate<JavaRuntime, JavaVersion> {
        private final Instance instance;

        @Override
        public boolean test(JavaRuntime javaRuntime, JavaVersion javaVersion) {
            SettableFuture<Boolean> fut = SettableFuture.create();
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle(tr("launcher.javaMismatchTitle"));
                alert.setHeaderText(tr("launcher.javaMismatchTitle"));
                alert.setContentText(tr("runner.wrongJavaVersion",
                        javaVersion.getMajorVersion(), javaRuntime.getVersion()));

                ButtonType cancel = new ButtonType(tr("button.cancel"));
                ButtonType launchAnyway = new ButtonType(tr("button.launchAnyway"));
                alert.getButtonTypes().setAll(cancel, launchAnyway);
                ButtonType picked = alert.showAndWait().orElse(cancel);
                fut.set(picked == launchAnyway);
            });

            try {
                return fut.get();
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
