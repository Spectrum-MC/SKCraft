package com.skcraft.launcher.fx_legacy;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.skcraft.launcher.Instance;
import com.skcraft.launcher.InstanceList;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.persistence.Persistence;
import com.skcraft.launcher.launch.LaunchListener;
import com.skcraft.launcher.launch.LaunchOptions;
import com.skcraft.launcher.launch.LaunchOptions.UpdatePolicy;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.google.common.util.concurrent.MoreExecutors.sameThreadExecutor;
import static com.skcraft.launcher.util.SharedLocale.tr;

public final class FxLauncherWindow {

    private static final Logger log = Logger.getLogger(FxLauncherWindow.class.getName());
    private static final AtomicBoolean FX_STARTED = new AtomicBoolean(false);

    private final Launcher launcher;
    private final Stage stage;
    private final ListView<Instance> instancesView = new ListView<>();
    private final ObservableList<Instance> instances = FXCollections.observableArrayList();
    private final CheckBox updateCheck = new CheckBox(tr("launcher.downloadUpdates"));
    private final Button refreshButton = new Button(tr("launcher.checkForUpdates"));
    private final Button launchButton = new Button(tr("launcher.launch"));
    private final Button optionsButton = new Button(tr("launcher.options"));

    private FxLauncherWindow(Launcher launcher) {
        this.launcher = launcher;
        this.stage = new Stage();
        stage.setTitle(tr("launcher.title", launcher.getVersion()));
        stage.setScene(new Scene(createRoot(), 1100, 700));
        stage.setMinWidth(720);
        stage.setMinHeight(460);
        stage.setOnCloseRequest(event -> {
            launcher.getExecutor().shutdownNow();
            Platform.exit();
            System.exit(0);
        });
        configureControls();
    }

    public static void show(Launcher launcher) {
        runOnFxThread(() -> {
            FxLauncherWindow window = new FxLauncherWindow(launcher);
            window.stage.show();
            window.loadInstances();
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

    private BorderPane createRoot() {
        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        Label title = new Label(launcher.getTitle() + " " + launcher.getVersion());
        title.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        root.setTop(title);

        instancesView.setItems(instances);
        instancesView.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(Instance item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                String suffix = item.isLocal() ? "" : " (remote)";
                if (item.isUpdatePending()) {
                    suffix += " (update)";
                }
                setText(item.getTitle() + suffix);
            }
        });

        root.setCenter(instancesView);

        HBox controls = new HBox(8);
        controls.setPadding(new Insets(10, 0, 0, 0));
        updateCheck.setSelected(true);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        launchButton.setStyle("-fx-font-weight: bold;");
        controls.getChildren().addAll(refreshButton, updateCheck, spacer, optionsButton, launchButton);
        root.setBottom(controls);
        return root;
    }

    private void configureControls() {
        refreshButton.setOnAction(event -> {
            loadInstances();
        });

        launchButton.setOnAction(event -> launchSelectedInstance());

        optionsButton.setOnAction(event -> {
            showOptionsDialog();
        });

        instancesView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                launchSelectedInstance();
            }
        });

        MenuItem launchItem = new MenuItem(tr("instance.launch"));
        launchItem.setOnAction(event -> launchSelectedInstance());
        MenuItem openFolderItem = new MenuItem(tr("instance.openFolder"));
        openFolderItem.setOnAction(event -> openSelectedFolder());
        MenuItem refreshItem = new MenuItem(tr("launcher.refreshList"));
        refreshItem.setOnAction(event -> loadInstances());
        ContextMenu menu = new ContextMenu(launchItem, openFolderItem, refreshItem);
        instancesView.setContextMenu(menu);
    }

    private void loadInstances() {
        refreshButton.setDisable(true);
        ListenableFuture<InstanceList> future = launcher.getExecutor().submit(launcher.getInstances().createEnumerator());
        Futures.addCallback(future, new FutureCallback<>() {
            @Override
            public void onSuccess(InstanceList result) {
                List<Instance> updated = new ArrayList<>();
                synchronized (result) {
                    result.sort();
                    updated.addAll(result.getInstances());
                }
                Platform.runLater(() -> {
                    instances.setAll(updated);
                    if (!instances.isEmpty()) {
                        instancesView.getSelectionModel().select(0);
                    }
                    refreshButton.setDisable(false);
                });
            }

            @Override
            public void onFailure(Throwable t) {
                log.log(Level.WARNING, "Failed to load instances", t);
                Platform.runLater(() -> {
                    showError("Unable to load instances.", t);
                    refreshButton.setDisable(false);
                });
            }
        }, sameThreadExecutor());
    }

    private void launchSelectedInstance() {
        Instance selected = instancesView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showError(tr("launcher.noInstanceError"), null);
            return;
        }

        LaunchOptions options = new LaunchOptions.Builder()
                .setInstance(selected)
                .setUpdatePolicy(updateCheck.isSelected() ? UpdatePolicy.UPDATE_IF_SESSION_ONLINE : UpdatePolicy.NO_UPDATE)
                .setListener(new FxLaunchListener(this, launcher))
                .build();

        launcher.getLaunchSupervisor().launch(options);
    }

    private void openSelectedFolder() {
        Instance selected = instancesView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }

        File folder = selected.getContentDir();
        folder.mkdirs();
        try {
            Desktop.getDesktop().open(folder);
        } catch (Exception e) {
            showError("Could not open folder: " + folder.getAbsolutePath(), e);
        }
    }

    private void showError(String message, Throwable t) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(tr("errorTitle"));
        alert.setHeaderText(message);
        if (t != null) {
            alert.setContentText(t.getMessage() == null ? t.toString() : t.getMessage());
        }
        alert.showAndWait();
    }

    private void showOptionsDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.initOwner(stage);
        dialog.setTitle(tr("options.title"));
        dialog.setResizable(true);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        TextField jvmArgsText = new TextField(valueOrEmpty(launcher.getConfig().getJvmArgs()));
        Spinner<Integer> minMemory = createIntSpinner(launcher.getConfig().getMinMemory(), 128, 1024 * 128);
        Spinner<Integer> maxMemory = createIntSpinner(launcher.getConfig().getMaxMemory(), 0, 1024 * 128);
        Spinner<Integer> permGen = createIntSpinner(launcher.getConfig().getPermGen(), 32, 8192);
        GridPane javaPane = createFormPane();
        addFormRow(javaPane, 0, tr("options.jvmArguments"), jvmArgsText);
        addFormRow(javaPane, 1, tr("options.minMemory"), minMemory);
        addFormRow(javaPane, 2, tr("options.maxMemory"), maxMemory);
        addFormRow(javaPane, 3, tr("options.permGen"), permGen);

        Spinner<Integer> width = createIntSpinner(launcher.getConfig().getWindowWidth(), 320, 8192);
        Spinner<Integer> height = createIntSpinner(launcher.getConfig().getWindowHeight(), 240, 8192);
        GridPane gamePane = createFormPane();
        addFormRow(gamePane, 0, tr("options.windowWidth"), width);
        addFormRow(gamePane, 1, tr("options.windowHeight"), height);

        CheckBox useProxy = new CheckBox(tr("options.useProxyCheck"));
        useProxy.setSelected(launcher.getConfig().isProxyEnabled());
        TextField proxyHost = new TextField(valueOrEmpty(launcher.getConfig().getProxyHost()));
        Spinner<Integer> proxyPort = createIntSpinner(launcher.getConfig().getProxyPort(), 1, 65535);
        TextField proxyUser = new TextField(valueOrEmpty(launcher.getConfig().getProxyUsername()));
        PasswordField proxyPass = new PasswordField();
        proxyPass.setText(valueOrEmpty(launcher.getConfig().getProxyPassword()));
        GridPane proxyPane = createFormPane();
        proxyPane.add(useProxy, 0, 0, 2, 1);
        addFormRow(proxyPane, 1, tr("options.proxyHost"), proxyHost);
        addFormRow(proxyPane, 2, tr("options.proxyPort"), proxyPort);
        addFormRow(proxyPane, 3, tr("options.proxyUsername"), proxyUser);
        addFormRow(proxyPane, 4, tr("options.proxyPassword"), proxyPass);

        tabs.getTabs().addAll(
                new Tab(tr("options.javaTab"), javaPane),
                new Tab(tr("options.minecraftTab"), gamePane),
                new Tab(tr("options.proxyTab"), proxyPane)
        );
        dialog.getDialogPane().setContent(tabs);

        dialog.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                launcher.getConfig().setJvmArgs(emptyToNull(jvmArgsText.getText()));
                launcher.getConfig().setMinMemory(minMemory.getValue());
                launcher.getConfig().setMaxMemory(maxMemory.getValue());
                launcher.getConfig().setPermGen(permGen.getValue());
                launcher.getConfig().setWindowWidth(width.getValue());
                launcher.getConfig().setWindowHeight(height.getValue());
                launcher.getConfig().setProxyEnabled(useProxy.isSelected());
                launcher.getConfig().setProxyHost(emptyToNull(proxyHost.getText()));
                launcher.getConfig().setProxyPort(proxyPort.getValue());
                launcher.getConfig().setProxyUsername(emptyToNull(proxyUser.getText()));
                launcher.getConfig().setProxyPassword(emptyToNull(proxyPass.getText()));
                Persistence.commitAndForget(launcher.getConfig());
            }
        });
    }

    private static Spinner<Integer> createIntSpinner(int initial, int min, int max) {
        Spinner<Integer> spinner = new Spinner<>();
        spinner.setEditable(true);
        spinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, clamp(initial, min, max)));
        spinner.setMaxWidth(Double.MAX_VALUE);
        return spinner;
    }

    private static GridPane createFormPane() {
        GridPane pane = new GridPane();
        pane.setHgap(10);
        pane.setVgap(10);
        pane.setPadding(new Insets(12));
        ColumnConstraints left = new ColumnConstraints();
        left.setMinWidth(220);
        left.setPrefWidth(240);
        left.setHalignment(HPos.LEFT);
        ColumnConstraints right = new ColumnConstraints();
        right.setMinWidth(260);
        right.setHgrow(Priority.ALWAYS);
        pane.getColumnConstraints().addAll(left, right);
        return pane;
    }

    private static void addFormRow(GridPane pane, int row, String label, javafx.scene.Node control) {
        pane.add(new Label(label), 0, row);
        if (control instanceof Region) {
            ((Region) control).setMaxWidth(Double.MAX_VALUE);
        }
        pane.add(control, 1, row);
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String emptyToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static final class FxLaunchListener implements LaunchListener {
        private final FxLauncherWindow window;
        private final Launcher launcher;

        private FxLaunchListener(FxLauncherWindow window, Launcher launcher) {
            this.window = window;
            this.launcher = launcher;
        }

        @Override
        public void instancesUpdated() {
            Platform.runLater(window::loadInstances);
        }

        @Override
        public void gameStarted() {
            Platform.runLater(window.stage::hide);
        }

        @Override
        public void gameClosed() {
            FxLauncherWindow.show(launcher);
        }
    }
}
