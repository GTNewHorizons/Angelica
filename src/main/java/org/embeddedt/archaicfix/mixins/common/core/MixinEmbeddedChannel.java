package org.embeddedt.archaicfix.mixins.common.core;

import io.netty.channel.*;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.internal.PlatformDependent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.channels.ClosedChannelException;
import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.Queue;

@Mixin(EmbeddedChannel.class)
public abstract class MixinEmbeddedChannel extends AbstractChannel {
    protected MixinEmbeddedChannel(Channel parent) {
        super(parent);
    }

    @Shadow public abstract ChannelConfig config();

    @Shadow private Throwable lastException;
    /**
     * Used to simulate socket buffers. When autoRead is false, all inbound information will be temporarily stored here.
     */
    private Queue<AbstractMap.SimpleEntry<Object, ChannelPromise>> tempInboundMessages;

    private Queue<AbstractMap.SimpleEntry<Object, ChannelPromise>> tempInboundMessages() {
        if (tempInboundMessages == null) {
            tempInboundMessages = new ArrayDeque<>();
        }
        return tempInboundMessages;
    }

    @Inject(method = "writeInbound", at = @At(value = "INVOKE", target = "Lio/netty/channel/embedded/EmbeddedChannel;pipeline()Lio/netty/channel/ChannelPipeline;"), cancellable = true, remap = false)
    private void storeMsgs(Object[] msgs, CallbackInfoReturnable<Boolean> cir) {
        if (!config().isAutoRead()) {
            Queue<AbstractMap.SimpleEntry<Object, ChannelPromise>> tempInboundMessages = tempInboundMessages();
            for (Object msg : msgs) {
                tempInboundMessages.add(new AbstractMap.SimpleEntry<>(msg, null));
            }
            cir.setReturnValue(false);
        }
    }

    private static boolean isNotEmpty(Queue<?> queue) {
        return queue != null && !queue.isEmpty();
    }

    @Inject(method = "doClose", at = @At("RETURN"), remap = false)
    private void handleInboundClosing(CallbackInfo ci) {
        if (isNotEmpty(tempInboundMessages)) {
            ClosedChannelException exception = null;
            for (;;) {
                AbstractMap.SimpleEntry<Object, ChannelPromise> entry = tempInboundMessages.poll();
                if (entry == null) {
                    break;
                }
                Object value = entry.getKey();
                if (value != null) {
                    ReferenceCountUtil.release(value);
                }
                ChannelPromise promise = entry.getValue();
                if (promise != null) {
                    if (exception == null) {
                        exception = new ClosedChannelException();
                    }
                    promise.tryFailure(exception);
                }
            }
        }
    }

    private ChannelFuture checkException(ChannelPromise promise) {
        Throwable t = lastException;
        if (t != null) {
            lastException = null;

            return promise.setFailure(t);
        }

        return promise.setSuccess();
    }

    @Inject(method = "doBeginRead", at = @At("HEAD"), remap = false)
    private void readTmpInbounds(CallbackInfo ci) {
        // read from tempInboundMessages and fire channelRead.
        if (isNotEmpty(tempInboundMessages)) {
            for (;;) {
                AbstractMap.SimpleEntry<Object, ChannelPromise> pair = tempInboundMessages.poll();
                if (pair == null) {
                    break;
                }

                Object msg = pair.getKey();
                if (msg != null) {
                    pipeline().fireChannelRead(msg);
                }

                ChannelPromise promise = pair.getValue();
                if (promise != null) {
                    checkException(promise);
                }
            }

            // fire channelReadComplete.
            flush();
        }
    }
}
