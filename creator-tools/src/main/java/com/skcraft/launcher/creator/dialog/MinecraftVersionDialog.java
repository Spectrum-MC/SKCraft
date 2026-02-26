package com.skcraft.launcher.creator.dialog;

import net.miginfocom.swing.MigLayout;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class MinecraftVersionDialog extends JDialog {

    private final List<BuilderConfigDialog.MinecraftVersionOption> versions;

    private final JCheckBox showReleases = new JCheckBox("Show releases", true);
    private final JCheckBox showSnapshots = new JCheckBox("Show snapshots", false);
    private final JCheckBox showBetas = new JCheckBox("Show beta", false);
    private final JCheckBox showAlphas = new JCheckBox("Show alpha", false);
    private final JComboBox<BuilderConfigDialog.MinecraftVersionOption> versionBox = new JComboBox<>();

    private boolean saved;

    public MinecraftVersionDialog(Window parent,
                                  List<BuilderConfigDialog.MinecraftVersionOption> versions,
                                  String selectedVersion) {
        super(parent, "Select Minecraft Version", ModalityType.DOCUMENT_MODAL);
        this.versions = versions;

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        initComponents();
        refreshVersions(selectedVersion);
        pack();
        setLocationRelativeTo(parent);
    }

    private void initComponents() {
        JPanel container = new JPanel(new MigLayout("fill, insets dialog", "[grow]", "[][][]"));

        JPanel filters = new JPanel(new MigLayout("insets 0", "[][][][]", "[]"));
        filters.add(showReleases);
        filters.add(showSnapshots);
        filters.add(showBetas);
        filters.add(showAlphas);
        container.add(filters, "growx, wrap");

        container.add(versionBox, "growx, wrap");

        JButton selectButton = new JButton("Select");
        JButton cancelButton = new JButton("Cancel");
        JPanel buttons = new JPanel(new MigLayout("insets 0", "[grow][][right]", "[]"));
        buttons.add(new JLabel(), "growx");
        buttons.add(selectButton, "sizegroup bttn");
        buttons.add(cancelButton, "sizegroup bttn");
        container.add(buttons, "growx");

        add(container, BorderLayout.CENTER);
        getRootPane().setDefaultButton(selectButton);

        Action refresh = new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                BuilderConfigDialog.MinecraftVersionOption selected =
                        (BuilderConfigDialog.MinecraftVersionOption) versionBox.getSelectedItem();
                refreshVersions(selected != null ? selected.id : null);
            }
        };

        showReleases.addActionListener(refresh);
        showSnapshots.addActionListener(refresh);
        showBetas.addActionListener(refresh);
        showAlphas.addActionListener(refresh);

        selectButton.addActionListener(e -> {
            saved = true;
            dispose();
        });
        cancelButton.addActionListener(e -> dispose());
    }

    private void refreshVersions(String preferredId) {
        List<BuilderConfigDialog.MinecraftVersionOption> filtered = new ArrayList<>();
        for (BuilderConfigDialog.MinecraftVersionOption v : versions) {
            if (matches(v.type)) {
                filtered.add(v);
            }
        }

        DefaultComboBoxModel<BuilderConfigDialog.MinecraftVersionOption> model = new DefaultComboBoxModel<>();
        for (BuilderConfigDialog.MinecraftVersionOption v : filtered) {
            model.addElement(v);
        }
        versionBox.setModel(model);

        if (preferredId != null) {
            for (int i = 0; i < model.getSize(); i++) {
                BuilderConfigDialog.MinecraftVersionOption option = model.getElementAt(i);
                if (preferredId.equals(option.id)) {
                    versionBox.setSelectedItem(option);
                    return;
                }
            }
        }

        if (model.getSize() > 0) {
            versionBox.setSelectedIndex(0);
        }
    }

    private boolean matches(String type) {
        if (type == null) {
            return false;
        }

        switch (type.toLowerCase()) {
            case "release":
                return showReleases.isSelected();
            case "snapshot":
                return showSnapshots.isSelected();
            case "old_beta":
            case "beta":
                return showBetas.isSelected();
            case "old_alpha":
            case "alpha":
                return showAlphas.isSelected();
            default:
                return false;
        }
    }

    public static String showSelector(Window parent,
                                      List<BuilderConfigDialog.MinecraftVersionOption> versions,
                                      String selectedVersion) {
        MinecraftVersionDialog dialog = new MinecraftVersionDialog(parent, versions, selectedVersion);
        dialog.setVisible(true);

        if (!dialog.saved) {
            return null;
        }

        BuilderConfigDialog.MinecraftVersionOption selected =
                (BuilderConfigDialog.MinecraftVersionOption) dialog.versionBox.getSelectedItem();
        return selected != null ? selected.id : null;
    }
}
