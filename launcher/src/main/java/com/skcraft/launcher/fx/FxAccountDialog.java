package com.skcraft.launcher.fx;

import com.skcraft.launcher.Configuration;
import com.skcraft.launcher.Launcher;
import com.skcraft.launcher.LauncherProperties;
import com.skcraft.launcher.auth.AuthenticationException;
import com.skcraft.launcher.auth.LoginService;
import com.skcraft.launcher.auth.OfflineSession;
import com.skcraft.launcher.auth.SavedSession;
import com.skcraft.launcher.auth.Session;
import com.skcraft.launcher.auth.UserType;
import com.skcraft.launcher.persistence.Persistence;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;

import static com.skcraft.launcher.util.SharedLocale.tr;

public final class FxAccountDialog {

    private final Launcher launcher;
    private final ObservableList<SavedSession> accounts = FXCollections.observableArrayList();
    private Session selected;

    private FxAccountDialog(Launcher launcher) {
        this.launcher = launcher;
        reloadAccounts();
    }

    public static Session showAccountRequest(Window owner, Launcher launcher) {
        if (owner == null) {
            for (Window candidate : Window.getWindows()) {
                if (candidate != null && candidate.isShowing()) {
                    owner = candidate;
                    break;
                }
            }
        }
        FxAccountDialog dialog = new FxAccountDialog(launcher);
        dialog.show(owner);

        if (dialog.selected != null && dialog.selected.isOnline()) {
            launcher.getAccounts().update(dialog.selected.toSavedSession());
        }
        Persistence.commitAndForget(launcher.getAccounts());
        return dialog.selected;
    }

    private void show(Window owner) {
        Stage stage = new Stage();
        stage.initStyle(StageStyle.UTILITY);
        stage.initModality(Modality.WINDOW_MODAL);
        if (owner != null) {
            stage.initOwner(owner);
        }
        String title = tr("accounts.title");
        stage.setTitle(title == null || title.trim().isEmpty() ? "Account Login" : title);
        stage.setResizable(false);

        ListView<SavedSession> accountList = new ListView<>(accounts);
        accountList.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(SavedSession item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getUsername() + " (" + item.getType().name().toLowerCase() + ")");
                }
            }
        });
        if (!accounts.isEmpty()) {
            accountList.getSelectionModel().select(0);
        }

        Button playButton = new Button(tr("accounts.play"));
        Button cancelButton = new Button(tr("button.cancel"));
        Button addMojangButton = new Button(tr("accounts.addMojang"));
        Button addMicrosoftButton = new Button(tr("accounts.addMicrosoft"));
        Button removeButton = new Button(tr("accounts.removeSelected"));
        Button offlineButton = new Button(tr("login.playOffline"));
        applyUniformButtonWidth(220, addMojangButton, addMicrosoftButton, removeButton);
        applyUniformButtonWidth(160, offlineButton, cancelButton, playButton);

        VBox right = new VBox(8, addMojangButton, addMicrosoftButton, removeButton);
        right.setPadding(new Insets(0, 0, 0, 8));
        right.setPrefWidth(236);
        right.setMinWidth(236);

        HBox center = new HBox(8, accountList, right);
        HBox.setHgrow(accountList, Priority.ALWAYS);
        accountList.setMinWidth(360);
        accountList.setPrefHeight(250);

        HBox bottom = new HBox(8);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        if (launcher.getConfig().isOfflineEnabled()) {
            bottom.getChildren().add(offlineButton);
        }
        bottom.getChildren().addAll(spacer, cancelButton, playButton);

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(12));
        root.setCenter(center);
        root.setBottom(bottom);
        BorderPane.setMargin(bottom, new Insets(12, 0, 0, 0));
        stage.setScene(new Scene(root));
        stage.sizeToScene();
        stage.setMinWidth(stage.getWidth());
        stage.setMinHeight(stage.getHeight());

        playButton.setOnAction(event -> {
            SavedSession session = accountList.getSelectionModel().getSelectedItem();
            if (session != null) {
                attemptExistingLogin(session, stage);
            }
        });

        cancelButton.setOnAction(event -> stage.close());

        addMojangButton.setOnAction(event -> {
            Session newSession = showMojangLoginDialog(stage, null, null);
            if (newSession != null) {
                launcher.getAccounts().update(newSession.toSavedSession());
                selected = newSession;
                stage.close();
            }
        });

        addMicrosoftButton.setOnAction(event -> {
            attemptMicrosoftLoginAsync(stage, tr("login.microsoft.seeBrowser"), result -> {
                launcher.getAccounts().update(result.toSavedSession());
                selected = result;
                stage.close();
            });
        });

        removeButton.setOnAction(event -> {
            SavedSession session = accountList.getSelectionModel().getSelectedItem();
            if (session == null) {
                return;
            }
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, tr("accounts.confirmForget"), ButtonType.YES, ButtonType.NO);
            confirm.setTitle(tr("accounts.confirmForgetTitle"));
            confirm.setHeaderText(null);
            if (confirm.showAndWait().orElse(ButtonType.NO) == ButtonType.YES) {
                launcher.getAccounts().remove(session);
                reloadAccounts();
            }
        });

        offlineButton.setOnAction(event -> {
            selected = new OfflineSession(LauncherProperties.getInstance().get("offlinePlayerName"));
            stage.close();
        });

        stage.showAndWait();
    }

    private void attemptExistingLogin(SavedSession session, Stage owner) {
        try {
            LoginService loginService = launcher.getLoginService(session.getType());
            Session restored = runSessionTask(owner, tr("login.loggingInTitle"), tr("login.loggingInStatus"),
                    () -> loginService.restore(session));
            selected = restored;
            owner.close();
        } catch (AuthenticationException e) {
            if (e.isInvalidatedSession()) {
                relogin(session, owner, e.getLocalizedMessage());
            } else {
                showError(e);
            }
        } catch (Exception e) {
            showError(e);
        }
    }

    private void relogin(SavedSession session, Stage owner, String message) {
        if (session.getType() == UserType.MICROSOFT) {
            attemptMicrosoftLoginAsync(owner, message, result -> {
                launcher.getAccounts().update(result.toSavedSession());
                selected = result;
                owner.close();
            });
            return;
        }

        Session result = showMojangLoginDialog(owner, session.getUsername(), tr("login.relogin", message));
        if (result != null) {
            launcher.getAccounts().update(result.toSavedSession());
            selected = result;
            owner.close();
        }
    }

    private void attemptMicrosoftLoginAsync(Stage owner, String status, java.util.function.Consumer<Session> onSuccess) {
        Stage progressStage = createProgressStage(owner, tr("login.loggingInTitle"), status);
        owner.getScene().getRoot().setDisable(true);
        launcher.getExecutor().submit(() -> {
            try {
                Session session = launcher.getMicrosoftLogin().login(() -> {
                });
                javafx.application.Platform.runLater(() -> {
                    progressStage.close();
                    onSuccess.accept(session);
                });
            } catch (Throwable t) {
                javafx.application.Platform.runLater(() -> {
                    progressStage.close();
                    owner.getScene().getRoot().setDisable(false);
                    showError(t);
                });
            }
        });
        progressStage.show();
    }

    private Session showMojangLoginDialog(Stage owner, String prefilledUsername, String message) {
        Stage loginStage = new Stage(StageStyle.UTILITY);
        loginStage.initOwner(owner);
        loginStage.initModality(Modality.WINDOW_MODAL);
        String title = tr("login.title");
        loginStage.setTitle(title == null || title.trim().isEmpty() ? "Mojang Login" : title);
        loginStage.setResizable(false);

        AtomicReference<Session> resultRef = new AtomicReference<>();
        TextField username = new TextField(prefilledUsername == null ? "" : prefilledUsername);
        PasswordField password = new PasswordField();

        GridPane pane = new GridPane();
        pane.setHgap(10);
        pane.setVgap(10);
        pane.setPadding(new Insets(14));
        ColumnConstraints left = new ColumnConstraints();
        left.setMinWidth(130);
        left.setPrefWidth(150);
        left.setHalignment(HPos.LEFT);
        ColumnConstraints right = new ColumnConstraints();
        right.setMinWidth(260);
        right.setHgrow(Priority.ALWAYS);
        pane.getColumnConstraints().addAll(left, right);
        username.setMaxWidth(Double.MAX_VALUE);
        password.setMaxWidth(Double.MAX_VALUE);
        int row = 0;
        if (message != null && !message.isEmpty()) {
            Label info = new Label(message);
            info.setWrapText(true);
            pane.add(info, 0, row++, 2, 1);
        }
        pane.add(new Label(tr("login.idEmail")), 0, row);
        pane.add(username, 1, row++);
        pane.add(new Label(tr("login.password")), 0, row);
        pane.add(password, 1, row);

        Button loginButton = new Button(tr("login.login"));
        Button cancelButton = new Button(tr("button.cancel"));
        applyUniformButtonWidth(120, cancelButton, loginButton);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttons = new HBox(8, spacer, cancelButton, loginButton);
        VBox root = new VBox(12, pane, buttons);
        root.setPadding(new Insets(12));

        loginButton.setDefaultButton(true);
        cancelButton.setCancelButton(true);
        cancelButton.setOnAction(event -> loginStage.close());
        loginButton.setOnAction(event -> {
            String userValue = username.getText() == null ? "" : username.getText().trim();
            String passValue = password.getText();
            if (userValue.isEmpty()) {
                showSimpleError(tr("login.noLoginError"));
                return;
            }
            if (passValue == null || passValue.isEmpty()) {
                showSimpleError(tr("login.noPasswordError"));
                return;
            }

            try {
                Session session = runSessionTask(loginStage, tr("login.loggingInTitle"), tr("login.loggingInStatus"),
                        () -> launcher.getYggdrasil().login(userValue, passValue));
                Configuration config = launcher.getConfig();
                if (!config.isOfflineEnabled()) {
                    config.setOfflineEnabled(true);
                    Persistence.commitAndForget(config);
                }
                resultRef.set(session);
                loginStage.close();
            } catch (Exception e) {
                showError(e);
            }
        });

        loginStage.setScene(new Scene(root));
        loginStage.sizeToScene();
        loginStage.setMinWidth(loginStage.getWidth());
        loginStage.setMinHeight(loginStage.getHeight());
        loginStage.showAndWait();
        return resultRef.get();
    }

    private Session runSessionTask(Stage owner, String title, String status, Callable<Session> action) throws Exception {
        Stage progressStage = createProgressStage(owner, title, status);

        AtomicReference<Session> sessionRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        launcher.getExecutor().submit(() -> {
            try {
                sessionRef.set(action.call());
            } catch (Throwable t) {
                errorRef.set(t);
            } finally {
                javafx.application.Platform.runLater(progressStage::close);
            }
        });

        progressStage.showAndWait();
        if (errorRef.get() != null) {
            Throwable t = errorRef.get();
            if (t instanceof Exception) {
                throw (Exception) t;
            }
            throw new ExecutionException(t);
        }
        return sessionRef.get();
    }

    private Stage createProgressStage(Stage owner, String title, String status) {
        Stage progressStage = new Stage(StageStyle.UTILITY);
        progressStage.initOwner(owner);
        progressStage.initModality(Modality.WINDOW_MODAL);
        progressStage.setResizable(false);
        progressStage.setTitle(title);
        VBox box = new VBox(12, new Label(status), new ProgressIndicator());
        box.setPadding(new Insets(16));
        progressStage.setScene(new Scene(box));
        return progressStage;
    }

    private void reloadAccounts() {
        List<SavedSession> snapshot = new ArrayList<>();
        synchronized (launcher.getAccounts()) {
            for (int i = 0; i < launcher.getAccounts().getSize(); i++) {
                snapshot.add(launcher.getAccounts().getElementAt(i));
            }
        }
        accounts.setAll(snapshot);
    }

    private void showSimpleError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.setTitle(tr("errorTitle"));
        alert.showAndWait();
    }

    private void showError(Throwable error) {
        String message = error.getLocalizedMessage();
        if (message == null || message.trim().isEmpty()) {
            if (error instanceof IOException) {
                message = error.toString();
            } else {
                message = "Unexpected error";
            }
        }
        showSimpleError(message);
    }

    private static void applyUniformButtonWidth(double width, Button... buttons) {
        for (Button button : buttons) {
            button.setMinWidth(width);
            button.setPrefWidth(width);
        }
    }
}
