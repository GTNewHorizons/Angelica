package org.embeddedt.archaicfix.threadedupdates;

/** A helper class to detect re-entrance of renderBlockByRenderType. */
public class RenderBlocksStack {

    private int level;

    /** Re-entrance should be impossible on the render threads, since only vanilla blocks are rendered there. So let's
     * just assume this is the case.
     */
    private boolean isMainThread() {
        return Thread.currentThread() == ThreadedChunkUpdateHelper.MAIN_THREAD;
    }

    public void push() {
        if(!isMainThread()) return;
        level++;
    }

    public void pop() {
        if(!isMainThread()) return;
        level--;
    }

    public void reset() {
        if(!isMainThread()) return;
        level = 0;
    }

    public int getLevel() {
        if(!isMainThread()) return 0;
        return level;
    }
}
