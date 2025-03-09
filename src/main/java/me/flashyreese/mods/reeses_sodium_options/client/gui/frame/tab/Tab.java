package me.flashyreese.mods.reeses_sodium_options.client.gui.frame.tab;

import me.flashyreese.mods.reeses_sodium_options.client.gui.frame.AbstractFrame;
import me.flashyreese.mods.reeses_sodium_options.client.gui.frame.OptionPageFrame;
import me.flashyreese.mods.reeses_sodium_options.client.gui.frame.ScrollableFrame;
import com.gtnewhorizons.angelica.client.gui.options.OptionPage;
import me.jellysquid.mods.sodium.client.util.Dim2i;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class Tab<T extends AbstractFrame> {
    private final String title;
    private final Function<Dim2i, T> frameFunction;

    public Tab(String title, Function<Dim2i, T> frameFunction) {
        this.title = title;
        this.frameFunction = frameFunction;
    }

    public static Builder<?> createBuilder() {
        return new Builder<>();
    }

    public String getTitle() {
        return title;
    }

    public Function<Dim2i, T> getFrameFunction() {
        return this.frameFunction;
    }

    public static class Builder<T extends AbstractFrame> {
        private String title;
        private Function<Dim2i, T> frameFunction;

        public Builder<T> setTitle(String title) {
            this.title = title;
            return this;
        }

        public Builder<T> setFrameFunction(Function<Dim2i, T> frameFunction) {
            this.frameFunction = frameFunction;
            return this;
        }

        public Tab<T> build() {
            return new Tab<>(this.title, this.frameFunction);
        }

        public Tab<ScrollableFrame> from(OptionPage page, AtomicReference<Integer> verticalScrollBarOffset) {
            return new Tab<>(page.getName(), dim2i -> ScrollableFrame
                    .createBuilder()
                    .setDimension(dim2i)
                    .setFrame(OptionPageFrame
                            .createBuilder()
                            .setDimension(new Dim2i(dim2i.getOriginX(), dim2i.getOriginY(), dim2i.getWidth(), dim2i.getHeight()))
                            .setOptionPage(page)
                            .build())
                    .setVerticalScrollBarOffset(verticalScrollBarOffset)
                    .build());
        }
    }
}
