package com.gtnewhorizons.angelica.mixins.early.archaic.common.chickenchunks;

import codechicken.chunkloader.PlayerChunkViewerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

@Mixin(PlayerChunkViewerManager.class)
public class MixinPlayerChunkViewerManager {
    @Shadow(remap = false) public LinkedList<PlayerChunkViewerManager.TicketChange> ticketChanges;

    @Redirect(method = "update", at = @At(ordinal = 7, value = "INVOKE", target = "Ljava/util/LinkedList;iterator()Ljava/util/Iterator;"), remap = false)
    private Iterator<?> getSafeIterator(LinkedList<PlayerChunkViewerManager.TicketChange> list, @Share("oldTicketChanges") LocalRef<List<PlayerChunkViewerManager.TicketChange>> oldTicketChanges) {
        oldTicketChanges.set(new ArrayList<>(list));
        return oldTicketChanges.get().iterator();
    }

    @Redirect(method = "update", at = @At(ordinal = 4, value = "INVOKE", target = "Ljava/util/LinkedList;clear()V"), remap = false)
    private void clearListSafely(LinkedList<PlayerChunkViewerManager.TicketChange> list, @Share("oldTicketChanges") LocalRef<List<PlayerChunkViewerManager.TicketChange>> oldTicketChanges) {
        this.ticketChanges.removeAll(oldTicketChanges.get());
        oldTicketChanges.set(null);
    }

}
