package com.gtnewhorizons.angelica.glsm.profiling;

import java.util.ArrayList;
import java.util.List;

public final class SectionRegistry {

    public interface Emitter {
        long enter(int category, String text);
        void leave(long nativeId);
        void setup(int category, String name);
        boolean isConnected();
    }

    private static final class Category {
        final int id;
        final String name;

        Category(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static final class Section {
        final long token;
        final int category;
        final String text;
        long nativeId;

        Section(long token, int category, String text, long nativeId) {
            this.token = token;
            this.category = category;
            this.text = text;
            this.nativeId = nativeId;
        }
    }

    private final Emitter emitter;
    private final List<Category> categories = new ArrayList<>();
    private final List<Section> open = new ArrayList<>();
    private long nextToken;
    private boolean connected;

    public SectionRegistry(Emitter emitter) {
        this.emitter = emitter;
    }

    public synchronized void registerCategory(int category, String name) {
        categories.add(new Category(category, name));
    }

    public synchronized long enter(int category, String text) {
        syncConnection();
        final long token = ++nextToken;
        open.add(new Section(token, category, text, emitter.enter(category, text)));
        return token;
    }

    public synchronized void leave(long token) {
        for (int i = 0; i < open.size(); i++) {
            final Section section = open.get(i);
            if (section.token == token) {
                open.remove(i);
                if (section.nativeId != 0L) emitter.leave(section.nativeId);
                return;
            }
        }
    }

    public synchronized void poll() {
        syncConnection();
    }

    private void syncConnection() {
        final boolean now = emitter.isConnected();
        if (now == connected) return;
        connected = now;
        if (now) replay();
    }

    private void replay() {
        for (int i = 0; i < categories.size(); i++) {
            final Category category = categories.get(i);
            emitter.setup(category.id, category.name);
        }
        for (int i = 0; i < open.size(); i++) {
            final Section section = open.get(i);
            section.nativeId = emitter.enter(section.category, section.text);
        }
    }
}
