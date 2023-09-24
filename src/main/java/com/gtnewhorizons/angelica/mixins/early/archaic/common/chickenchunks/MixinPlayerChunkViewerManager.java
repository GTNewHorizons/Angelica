package com.gtnewhorizons.angelica.mixins.early.archaic.common.chickenchunks;

import codechicken.chunkloader.PlayerChunkViewerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

@Mixin(PlayerChunkViewerManager.class)
public class MixinPlayerChunkViewerManager {
    @Shadow(remap = false) public LinkedList<PlayerChunkViewerManager.TicketChange> ticketChanges;

    private ArrayList<PlayerChunkViewerManager.TicketChange> oldTicketChanges;

    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Ljava/util/LinkedList;iterator()Ljava/util/Iterator;"), remap = false)
    private Iterator<?> getSafeIterator(LinkedList<?> list) {
        if(list == this.ticketChanges) {
            oldTicketChanges = new ArrayList<>((LinkedList<PlayerChunkViewerManager.TicketChange>)list);
            return oldTicketChanges.iterator();
        } else {
            return list.iterator();
        }
    }

    @Redirect(method = "update", at = @At(value = "INVOKE", target = "Ljava/util/LinkedList;clear()V"), remap = false)
    private void clearListSafely(LinkedList<?> list) {
        if(list == this.ticketChanges) {
            this.ticketChanges.removeAll(oldTicketChanges);
            oldTicketChanges = null;
        } else {
            list.clear();
        }
    }

}
