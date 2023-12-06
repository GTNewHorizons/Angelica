package com.gtnewhorizons.angelica.compat.mojang;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Deprecated
public interface VertexConsumerProvider {
    static Immediate immediate(BufferBuilder buffer) {
        return immediate(ImmutableMap.of(), buffer);
    }

    static Immediate immediate(Map<RenderLayer, BufferBuilder> layerBuffers, BufferBuilder fallbackBuffer) {
        return new Immediate(fallbackBuffer, layerBuffers);
    }

    VertexConsumer getBuffer(RenderLayer layer);

    public static class Immediate implements VertexConsumerProvider {
        protected final BufferBuilder fallbackBuffer;
        protected final Map<RenderLayer, BufferBuilder> layerBuffers;
        protected Optional<RenderLayer> currentLayer = Optional.empty();
        protected final Set<BufferBuilder> activeConsumers = Sets.newHashSet();

        protected Immediate(BufferBuilder fallbackBuffer, Map<RenderLayer, BufferBuilder> layerBuffers) {
            this.fallbackBuffer = fallbackBuffer;
            this.layerBuffers = layerBuffers;
        }

        public VertexConsumer getBuffer(RenderLayer arg) {
            Optional<RenderLayer> optional = arg.asOptional();
            BufferBuilder lv = this.getBufferInternal(arg);
            if (!Objects.equals(this.currentLayer, optional)) {
                if (this.currentLayer.isPresent()) {
                    RenderLayer lv2 = (RenderLayer)this.currentLayer.get();
                    if (!this.layerBuffers.containsKey(lv2)) {
                        this.draw(lv2);
                    }
                }

                if (this.activeConsumers.add(lv)) {
                    lv.begin(arg.getDrawMode(), arg.getVertexFormat());
                }

                this.currentLayer = optional;
            }

            return lv;
        }

        private BufferBuilder getBufferInternal(RenderLayer layer) {
            return (BufferBuilder)this.layerBuffers.getOrDefault(layer, this.fallbackBuffer);
        }

        public void draw() {
            this.currentLayer.ifPresent((arg) -> {
                VertexConsumer lv = this.getBuffer(arg);
                if (lv == this.fallbackBuffer) {
                    this.draw(arg);
                }

            });
            Iterator var1 = this.layerBuffers.keySet().iterator();

            while(var1.hasNext()) {
                RenderLayer lv = (RenderLayer)var1.next();
                this.draw(lv);
            }

        }

        public void draw(RenderLayer layer) {
            BufferBuilder lv = this.getBufferInternal(layer);
            boolean bl = Objects.equals(this.currentLayer, layer.asOptional());
            if (bl || lv != this.fallbackBuffer) {
                if (this.activeConsumers.remove(lv)) {
                    layer.draw(lv, 0, 0, 0);
                    if (bl) {
                        this.currentLayer = Optional.empty();
                    }

                }
            }
        }
    }
}

