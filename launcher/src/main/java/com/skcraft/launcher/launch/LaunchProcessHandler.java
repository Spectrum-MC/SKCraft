/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.launch;

import com.google.common.base.Function;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.fx_legacy.FxProcessConsole;
import javafx.application.Platform;
import lombok.NonNull;
import lombok.extern.java.Log;

/**
 * Handles post-process creation during launch.
 */
@Log
public class LaunchProcessHandler implements Function<Process, Void> {

    private final Launcher launcher;
    private FxProcessConsole console;

    public LaunchProcessHandler(@NonNull Launcher launcher) {
        this.launcher = launcher;
    }

    @Override
    public Void apply(final Process process) {
        log.info("Watching process " + process);

        Platform.runLater(() -> {
            if (console == null) {
                console = new FxProcessConsole(launcher.getTitle());
            }
            console.show(process);
        });

        try {
            process.waitFor();
        } catch (InterruptedException e) {
            // Orphan process
            Thread.currentThread().interrupt();
        }

        log.info("Process ended, re-showing launcher...");

        Platform.runLater(() -> {
            if (console != null) {
                console.clearProcess();
            }
        });

        return null;
    }

}
