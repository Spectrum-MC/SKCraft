/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.creator.controller;

import com.skcraft.launcher.creator.dialog.ManifestEntryDialog;
import com.skcraft.launcher.creator.model.creator.ManifestEntry;

public class ManifestEntryController {

    private final ManifestEntryDialog dialog;
    private final ManifestEntry manifestEntry;

    private boolean save;

    public ManifestEntryController(ManifestEntryDialog dialog, ManifestEntry manifestEntry) {
        this.dialog = dialog;
        this.manifestEntry = manifestEntry;

        initListeners();
        copyFrom();
    }

    private void copyFrom() {
        dialog.getIncludeCheck().setSelected(manifestEntry.isSelected());
        dialog.getPrioritySpinner().setValue(manifestEntry.getManifestInfo().getPriority());
    }

    private void copyTo() {
        manifestEntry.setSelected(dialog.getIncludeCheck().isSelected());
        manifestEntry.getManifestInfo().setPriority((Integer) dialog.getPrioritySpinner().getValue());
    }

    public boolean show() {
        dialog.setVisible(true);
        return save;
    }

    private void initListeners() {
        dialog.getOkButton().addActionListener(e -> {
            copyTo();
            save = true;
            dialog.dispose();
        });

        dialog.getCancelButton().addActionListener(e -> dialog.dispose());
    }

}
