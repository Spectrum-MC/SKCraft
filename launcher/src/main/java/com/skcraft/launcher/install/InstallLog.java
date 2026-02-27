/*
 * SK's Minecraft Launcher
 * Copyright (C) 2010-2014 Albert Pham <http://www.sk89q.com> and contributors
 * Please see LICENSE.txt for license information.
 */

package com.skcraft.launcher.install;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.NonNull;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

@Data
public class InstallLog {

    @JsonIgnore
    private File baseDir;
    private Map<String, Set<String>> entries = new HashMap<>();
    @JsonIgnore
    private Set<String> cache = new HashSet<>();

    public synchronized void add(@NonNull String group, @NonNull String entry) {
        cache.add(entry);
        Set<String> subEntries = entries.computeIfAbsent(group, k -> new HashSet<>());
        subEntries.add(entry);
    }

    public synchronized void add(@NonNull File group, @NonNull File entry) {
        add(relativize(group), relativize(entry));
    }

    public synchronized boolean has(@NonNull String entry) {
        return cache.contains(entry);
    }

    public synchronized boolean has(@NonNull File entry) {
        return has(relativize(entry));
    }

    public synchronized boolean copyGroupFrom(InstallLog other, String group) {
        Set<String> otherSet = other.entries.get(group);
        if (otherSet == null) {
            return false;
        }
        for (String entry : otherSet) {
            add(group, entry);
        }
        return true;
    }

    public synchronized boolean copyGroupFrom(@NonNull InstallLog other, @NonNull File entry) {
        return copyGroupFrom(other, relativize(entry));
    }

    @JsonIgnore
    public synchronized Set<Map.Entry<String, Set<String>>> getEntrySet() {
        return entries.entrySet();
    }

    public synchronized boolean hasGroup(String group) {
        return entries.containsKey(group);
    }

    private String relativize(File child) {
        checkNotNull(baseDir);
        URI uri = child.toURI();
        String relative = baseDir.toURI().relativize(uri).getPath();
        if (relative.equals(uri.toString())) {
            throw new IllegalArgumentException("Child path not in base");
        }
        return relative;
    }

}
