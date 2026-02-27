package com.skcraft.launcher;

import com.skcraft.launcher.util.HttpRequest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.util.Properties;

public final class LauncherProperties {

    private static volatile LauncherProperties instance;

    private final Properties properties;

    private LauncherProperties() throws IOException {
        this.properties = LauncherUtils.loadProperties(
                Launcher.class,
                "launcher.properties",
                "com.skcraft.launcher.propertiesFile");
    }

    public static void init() throws IOException {
        if (instance != null) {
            return;
        }

        synchronized (LauncherProperties.class) {
            if (instance == null) {
                instance = new LauncherProperties();
            }
        }
    }

    public static LauncherProperties getInstance() {
        LauncherProperties current = instance;
        if (current != null) {
            return current;
        }

        synchronized (LauncherProperties.class) {
            if (instance == null) {
                try {
                    instance = new LauncherProperties();
                } catch (IOException e) {
                    throw new UncheckedIOException("Failed to load launcher.properties", e);
                }
            }
            return instance;
        }
    }

    public String get(String key) {
        return properties.getProperty(key);
    }

    public String get(String key, String... args) {
        return String.format(get(key), (Object[]) args);
    }

    public URL getUrl(String key) {
        return HttpRequest.url(get(key));
    }

    public URL getUrl(String key, String... args) {
        return HttpRequest.url(get(key, args));
    }
}
