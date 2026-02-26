package com.skcraft.launcher.dialog;

import com.skcraft.launcher.Instance;
import com.skcraft.launcher.InstanceSettings;
import com.skcraft.launcher.launch.MemorySettings;
import com.skcraft.launcher.persistence.Persistence;
import com.skcraft.launcher.swing.FormPanel;
import com.skcraft.launcher.swing.LinedBoxPanel;
import com.skcraft.launcher.util.SharedLocale;
import lombok.extern.java.Log;

import javax.swing.*;
import java.awt.*;

@Log
public class InstanceSettingsDialog extends JDialog {
	private final InstanceSettings settings;

	private final LinedBoxPanel formsPanel = new LinedBoxPanel(false);
	private final FormPanel memorySettingsPanel = new FormPanel();
	private final JCheckBox enableMemorySettings = new JCheckBox(SharedLocale.tr("instance.options.customMemory"));
	private final JSpinner minMemorySpinner = new JSpinner();
	private final JSpinner maxMemorySpinner = new JSpinner();
	private final JTextField javaArgsBox = new JTextField();

	private final LinedBoxPanel buttonsPanel = new LinedBoxPanel(true);
	private final JButton okButton = new JButton(SharedLocale.tr("button.save"));
	private final JButton cancelButton = new JButton(SharedLocale.tr("button.cancel"));

	private boolean saved = false;

	public InstanceSettingsDialog(Window owner, InstanceSettings settings) {
		super(owner);
		this.settings = settings;

		setTitle(SharedLocale.tr("instance.options.title"));
		setModalityType(DEFAULT_MODALITY_TYPE);
		initComponents();
		setSize(new Dimension(400, 500));
		setLocationRelativeTo(owner);
	}

	private void initComponents() {
		memorySettingsPanel.addRow(enableMemorySettings);
		memorySettingsPanel.addRow(new JLabel(SharedLocale.tr("options.minMemory")), minMemorySpinner);
		memorySettingsPanel.addRow(new JLabel(SharedLocale.tr("options.maxMemory")), maxMemorySpinner);
		memorySettingsPanel.addRow(new JLabel(SharedLocale.tr("options.jvmArguments")), javaArgsBox);

		okButton.setMargin(new Insets(0, 10, 0, 10));
		buttonsPanel.addGlue();
		buttonsPanel.addElement(okButton);
		buttonsPanel.addElement(cancelButton);

		enableMemorySettings.addActionListener(e -> {
			if (enableMemorySettings.isSelected()) {
				settings.setMemorySettings(new MemorySettings());
			} else {
				settings.setMemorySettings(null);
			}

			updateComponents();
		});

		okButton.addActionListener(e -> {
			save();
			dispose();
		});

		cancelButton.addActionListener(e -> dispose());

		formsPanel.addElement(memorySettingsPanel);

		add(formsPanel, BorderLayout.NORTH);
		add(buttonsPanel, BorderLayout.SOUTH);

		updateComponents();
	}

	private void updateComponents() {
		if (settings.getMemorySettings() != null) {
			memorySettingsPanel.setEnabled(true);
			enableMemorySettings.setSelected(true);

			minMemorySpinner.setValue(settings.getMemorySettings().getMinMemory());
			maxMemorySpinner.setValue(settings.getMemorySettings().getMaxMemory());
		} else {
			memorySettingsPanel.setEnabled(false);
			enableMemorySettings.setSelected(false);
		}
		javaArgsBox.setText(settings.getCustomJvmArgs());
	}

	private void save() {
		if (enableMemorySettings.isSelected()) {
			MemorySettings memorySettings = settings.getMemorySettings();

			memorySettings.setMinMemory((int) minMemorySpinner.getValue());
			memorySettings.setMaxMemory((int) maxMemorySpinner.getValue());
		} else {
			settings.setMemorySettings(null);
		}

		settings.setCustomJvmArgs(javaArgsBox.getText());

		saved = true;
	}

	public static boolean open(Window parent, Instance instance) {
		InstanceSettingsDialog dialog = new InstanceSettingsDialog(parent, instance.getSettings());
		dialog.setVisible(true);

		if (dialog.saved) {
			Persistence.commitAndForget(instance);
		}

		return dialog.saved;
	}
}
