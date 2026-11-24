package com.marianocarrizo.dynamusic.service;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class PlayQueue {

    private final List<File> items = new ArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();

    public List<File> items() {
        return Collections.unmodifiableList(items);
    }

    public int size() {
        return items.size();
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public void playNext(List<File> files) {
        if (files.isEmpty()) return;
        items.addAll(0, files);
        changed();
    }

    public void add(List<File> files) {
        if (files.isEmpty()) return;
        items.addAll(files);
        changed();
    }

    public void remove(int index) {
        if (!inRange(index)) return;
        items.remove(index);
        changed();
    }

    public void move(int from, int to) {
        if (!inRange(from) || !inRange(to) || from == to) return;
        items.add(to, items.remove(from));
        changed();
    }

    public void moveToFront(int index) {
        move(index, 0);
    }

    public Optional<File> peek() {
        return items.isEmpty() ? Optional.empty() : Optional.of(items.get(0));
    }

    public Optional<File> poll() {
        if (items.isEmpty()) return Optional.empty();
        File head = items.remove(0);
        changed();
        return Optional.of(head);
    }

    public void clear() {
        if (items.isEmpty()) return;
        items.clear();
        changed();
    }

    public void addListener(Runnable listener) {
        listeners.add(listener);
    }

    private boolean inRange(int index) {
        return index >= 0 && index < items.size();
    }

    private void changed() {
        for (Runnable listener : listeners) listener.run();
    }
}
