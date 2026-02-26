/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.creator.dialog;

import com.skcraft.launcher.swing.SwingHelper;
import com.skcraft.launcher.swing.TextFieldPopupMenu;
import lombok.Getter;
import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;

public class ManifestEntryDialog extends JDialog {

    @Getter private final JSpinner prioritySpinner = new JSpinner();
    @Getter private final JCheckBox includeCheck = new JCheckBox("Include in package listing");

    @Getter private final JButton okButton = new JButton("OK");
    @Getter private final JButton cancelButton = new JButton("Cancel");

    public ManifestEntryDialog(Window parent) {
        super(parent, "Modpack Entry", ModalityType.DOCUMENT_MODAL);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        initComponents();
        setResizable(false);
        pack();
        setLocationRelativeTo(parent);
    }

    private void initComponents() {
        prioritySpinner.setComponentPopupMenu(TextFieldPopupMenu.INSTANCE);

        JPanel container = new JPanel();
        container.setLayout(new MigLayout("insets dialog"));

        container.add(includeCheck, "span, gapbottom unrel");

        container.add(new JLabel("Priority:"));
        container.add(prioritySpinner, "span, split 2, w 50");
        container.add(new JLabel("(Greater is higher)"));

        container.add(okButton, "tag ok, span, split 2, sizegroup bttn, gaptop unrel");
        container.add(cancelButton, "tag cancel, sizegroup bttn");

        add(container, BorderLayout.CENTER);

        getRootPane().setDefaultButton(okButton);
        getRootPane().registerKeyboardAction(e -> cancelButton.doClick(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

}
