package com.skcraft.launcher.fx;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;

import static com.skcraft.launcher.util.SharedLocale.tr;

public final class FxProcessConsole {

    private static final int MAX_BUFFER_BYTES = 20 * 1024 * 1024;
    private static final int FLUSH_INTERVAL_MS = 200;

    private static final class PendingLine {
        private final String raw;
        private int count;
        private int bytes;

        private PendingLine(String raw, int count, int bytes) {
            this.raw = raw;
            this.count = count;
            this.bytes = bytes;
        }
    }

    private static final class RenderedLine {
        private final String raw;
        private int count;
        private String text;
        private int bytes;
        private int chars;

        private RenderedLine(String raw, int count) {
            this.raw = raw;
            this.count = count;
            updateText();
        }

        private void updateText() {
            String rendered = count > 1 ? "[x" + count + "] " + raw : raw;
            this.text = rendered + "\n";
            this.chars = text.length();
            this.bytes = text.getBytes(StandardCharsets.UTF_8).length;
        }
    }

    private final Stage stage;
    private final TextArea output = new TextArea();
    private final Object queueLock = new Object();
    private final Deque<PendingLine> pending = new ArrayDeque<>();
    private final Deque<RenderedLine> rendered = new ArrayDeque<>();
    private final Timeline flusher;

    private volatile Process process;
    private int renderedBytes;
    private int renderedChars;

    public FxProcessConsole(String title) {
        stage = new Stage(StageStyle.UTILITY);
        stage.setTitle(title);
        stage.setMinWidth(760);
        stage.setMinHeight(420);

        output.setEditable(false);
        output.setWrapText(false);
        output.setStyle("-fx-font-family: monospace;");

        Button kill = new Button(tr("console.forceClose"));
        kill.setOnAction(e -> {
            Process current = process;
            if (current != null) {
                current.destroyForcibly();
            }
        });

        Button close = new Button(tr("console.closeWindow"));
        close.setOnAction(e -> stage.hide());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox footer = new HBox(8, spacer, kill, close);

        VBox root = new VBox(8, output, footer);
        root.setPadding(new Insets(10));
        VBox.setVgrow(output, Priority.ALWAYS);

        stage.setScene(new Scene(root, 900, 520));

        flusher = new Timeline(new KeyFrame(Duration.millis(FLUSH_INTERVAL_MS), e -> flushPending()));
        flusher.setCycleCount(Timeline.INDEFINITE);
        flusher.play();
    }

    public void show(Process process) {
        this.process = process;
        clearAll();
        stage.show();
        consume(process.getInputStream());
        consume(process.getErrorStream());
    }

    public void clearProcess() {
        this.process = null;
    }

    private void clearAll() {
        synchronized (queueLock) {
            pending.clear();
        }
        rendered.clear();
        renderedBytes = 0;
        renderedChars = 0;
        output.clear();
    }

    private void consume(InputStream stream) {
        Thread t = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    enqueueLine(line);
                }
            } catch (IOException ignored) {
            }
        }, "fx-process-console-reader");
        t.setDaemon(true);
        t.start();
    }

    private void enqueueLine(String line) {
        int lineBytes = (line + "\n").getBytes(StandardCharsets.UTF_8).length;
        synchronized (queueLock) {
            PendingLine last = pending.peekLast();
            if (last != null && last.raw.equals(line)) {
                last.count++;
                last.bytes += lineBytes;
            } else {
                pending.addLast(new PendingLine(line, 1, lineBytes));
            }
        }
    }

    private void flushPending() {
        Deque<PendingLine> batch = new ArrayDeque<>();
        synchronized (queueLock) {
            if (pending.isEmpty()) {
                return;
            }
            Iterator<PendingLine> it = pending.iterator();
            while (it.hasNext()) {
                PendingLine line = it.next();
                batch.addLast(new PendingLine(line.raw, line.count, line.bytes));
            }
            pending.clear();
        }

        while (!batch.isEmpty()) {
            PendingLine next = batch.removeFirst();
            appendOrMerge(next.raw, next.count);
        }

        trimToSizeLimit();
    }

    private void appendOrMerge(String raw, int count) {
        RenderedLine tail = rendered.peekLast();
        if (tail != null && tail.raw.equals(raw)) {
            int oldChars = tail.chars;
            int oldBytes = tail.bytes;
            int oldEnd = renderedChars;
            int oldStart = oldEnd - oldChars;

            tail.count += count;
            tail.updateText();

            output.replaceText(oldStart, oldEnd, tail.text);
            renderedChars += tail.chars - oldChars;
            renderedBytes += tail.bytes - oldBytes;
            return;
        }

        RenderedLine line = new RenderedLine(raw, count);
        rendered.addLast(line);
        output.appendText(line.text);
        renderedChars += line.chars;
        renderedBytes += line.bytes;
    }

    private void trimToSizeLimit() {
        while (renderedBytes > MAX_BUFFER_BYTES && !rendered.isEmpty()) {
            RenderedLine first = rendered.removeFirst();
            output.deleteText(0, first.chars);
            renderedChars -= first.chars;
            renderedBytes -= first.bytes;
        }
    }
}
