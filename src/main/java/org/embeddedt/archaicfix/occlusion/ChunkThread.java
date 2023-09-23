package org.embeddedt.archaicfix.occlusion;

import net.minecraft.world.chunk.Chunk;
import org.embeddedt.archaicfix.occlusion.util.LinkedHashList;
import org.embeddedt.archaicfix.occlusion.util.SynchronizedIdentityLinkedHashList;

public class ChunkThread extends Thread {

    public ChunkThread() {

        super("Chunk Worker");
    }

    public LinkedHashList<Chunk> loaded = new SynchronizedIdentityLinkedHashList<Chunk>();
    public LinkedHashList<Chunk> modified = new SynchronizedIdentityLinkedHashList<Chunk>();

    @Override
    public void run() {

        for (;;) {
            int i = 0;
            boolean work = false;
            for (; loaded.size() > 0; ++i) {
                Chunk chunk = ((ICulledChunk)loaded.shift()).buildCulledSides();
                if (chunk != null) {
                    modified.add(chunk);
                    work = true;
                }
                if ((i & 3) == 0) {
                    Thread.yield();
                }
            }
            for (i = 0; modified.size() > 0; ++i) {
                Chunk chunk = modified.shift();
                if (loaded.contains(chunk)) {
                    continue;
                }
                for (VisGraph graph : ((ICulledChunk)chunk).getVisibility()) {
                    if (graph.isDirty()) {
                        long a = graph.getVisibility();
                        graph.computeVisibility();
                        work |= a != graph.getVisibility();
                    }
                }
                if ((i & 7) == 0) {
                    Thread.yield();
                }
            }
            OcclusionHelpers.worker.dirty = work;
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
            }
        }
    }
}
