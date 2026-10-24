package com.gtnewhorizons.angelica.rendering.tesr;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.hooks.BatchStateGuard;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfo;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.coderbot.iris.texture.pbr.PBRTextureManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL30;

import java.nio.ByteBuffer;

/**
 * Copies entity and block entity textures into a shared atlas, so model boxes with different textures land in one instanced draw.
 */
public final class EntityTextureAtlas {

    public static final EntityTextureAtlas INSTANCE = new EntityTextureAtlas();

    static final int PAGE_SIZE = 2048;
    private static final int MAX_PAGES = 4;
    private static final int MAX_EDGE = 512;
    private static final int MAX_QUICK_REFRESHES = 8;
    private static final long QUICK_REFRESH_MS = 1000L;

    public static final class Slot {
        static final Slot WHOLE_TEXTURE = new Slot();

        final Page page;
        final int width, height;
        final int x, y;
        final float u, v, w, h;
        final Matrix4f uvMatrix;

        Slot(Page page, int x, int y, int width, int height) {
            this.page = page;
            this.x = x;
            this.y = y;
            this.width  = width;
            this.height = height;
            this.u = (x + 1) / (float) PAGE_SIZE;
            this.v = (y + 1) / (float) PAGE_SIZE;
            this.w = width   / (float) PAGE_SIZE;
            this.h = height  / (float) PAGE_SIZE;
            this.uvMatrix = new Matrix4f().translation(u, v, 0.0f).scale(w, h, 1.0f);
        }

        private Slot() {
            this.page = null;
            this.x = this.y = this.width = this.height = 0;
            this.u = this.v = 0.0f;
            this.w = this.h = 1.0f;
            this.uvMatrix = new Matrix4f();
        }
    }

    private static final class Shelf {
        final int y, height;
        int x;

        Shelf(int y, int height) {
            this.y = y;
            this.height = height;
        }
    }

    static final class Page {
        final ResourceLocation location;
        final int texture;
        final ObjectArrayList<Shelf> shelves = new ObjectArrayList<>();
        int nextShelfY;

        Page(ResourceLocation location, int texture) {
            this.location = location;
            this.texture = texture;
        }

        long place(int width, int height) {
            Shelf best = null;
            for (Shelf shelf : shelves) {
                if (shelf.height >= height && PAGE_SIZE - shelf.x >= width && (best == null || shelf.height < best.height)) best = shelf;
            }
            if (best != null && best.height > 2 * height && PAGE_SIZE - nextShelfY >= height) best = null;
            if (best == null) {
                if (PAGE_SIZE - nextShelfY < height || width > PAGE_SIZE) return -1L;
                best = new Shelf(nextShelfY, height);
                shelves.add(best);
                nextShelfY += height;
            }
            final long at = ((long) best.x << 32) | best.y;
            best.x += width;
            return at;
        }

        void empty() {
            shelves.clear();
            nextShelfY = 0;
        }
    }

    private static final class PageTexture extends AbstractTexture {
        PageTexture(int texture) {
            this.glTextureId = texture;
        }

        @Override
        public void loadTexture(IResourceManager resourceManager) {}
    }

    private static final class Entry {
        final TextureInfo info;
        int uploadGeneration;
        int quickRefreshes;
        long lastRefreshMs;
        Slot slot;
        int noRoomWidth = -1, noRoomHeight = -1;

        Entry(TextureInfo info) {
            this.info = info;
        }
    }

    private final Int2ObjectOpenHashMap<Entry> entries = new Int2ObjectOpenHashMap<>();
    private final ObjectArrayList<Page> pages = new ObjectArrayList<>();
    private final ObjectArrayList<Slot> freeSlots = new ObjectArrayList<>();
    private final ObjectArrayList<Slot> releasedThisPass = new ObjectArrayList<>();
    private int readFbo, drawFbo;
    private int drawFboPage;
    private int lastTexture = -1;
    private boolean lastPbrPipeline;
    private TextureInfo lastInfo;
    private int lastChangeStamp, lastDeleteCount, lastPbrHolderChanges;
    private Slot lastSlot;

    private EntityTextureAtlas() {}

    Slot slotFor(int texture, boolean pbrPipeline) {
        if (texture == lastTexture && pbrPipeline == lastPbrPipeline && lastInfo.getChangeStamp() == lastChangeStamp
            && TextureInfoCache.INSTANCE.deleteCount() == lastDeleteCount && PBRTextureManager.INSTANCE.holderChanges() == lastPbrHolderChanges) {
            return lastSlot;
        }
        final TextureInfo info = TextureInfoCache.INSTANCE.getInfo(texture);
        if (info == null) return null;
        lastSlot = lookUp(texture, info, pbrPipeline);
        lastTexture = texture;
        lastPbrPipeline = pbrPipeline;
        lastInfo = info;
        lastChangeStamp = info.getChangeStamp();
        lastDeleteCount = TextureInfoCache.INSTANCE.deleteCount();
        lastPbrHolderChanges = PBRTextureManager.INSTANCE.holderChanges();
        return lastSlot;
    }

    private Slot lookUp(int texture, TextureInfo info, boolean pbrPipeline) {
        Entry entry = entries.get(texture);
        if (entry == null || entry.info != info) {
            if (entry != null) release(entry);
            entry = new Entry(info);
            entries.put(texture, entry);
        }
        if (!eligible(texture, entry, pbrPipeline)) {
            if (info.isWrittenOutsideUploads()) release(entry);
            return null;
        }
        final int width = info.getWidth(), height = info.getHeight();
        if (entry.slot != null && (entry.slot.width != width || entry.slot.height != height)) release(entry);
        if (entry.slot == null) {
            entry.slot = takeFreeSlot(width, height);
            if (entry.slot == null && (width != entry.noRoomWidth || height != entry.noRoomHeight)) entry.slot = placeOnPage(width, height);
            if (entry.slot == null) {
                entry.noRoomWidth = width;
                entry.noRoomHeight = height;
                return null;
            }
        } else if (entry.uploadGeneration == info.getUploadGeneration()) {
            return entry.slot;
        } else if (recordRefresh(entry)) {
            release(entry);
            return null;
        }
        copy(texture, entry.slot);
        entry.uploadGeneration = info.getUploadGeneration();
        return entry.slot;
    }

    private static boolean recordRefresh(Entry entry) {
        final long now = System.currentTimeMillis();
        entry.quickRefreshes = now - entry.lastRefreshMs < QUICK_REFRESH_MS ? entry.quickRefreshes + 1 : 1;
        entry.lastRefreshMs = now;
        return entry.quickRefreshes > MAX_QUICK_REFRESHES;
    }

    private static boolean eligible(int texture, Entry entry, boolean pbrPipeline) {
        final TextureInfo info = entry.info;
        final int width = info.getWidth();
        final int height = info.getHeight();
        if (width <= 0 || height <= 0 || width > MAX_EDGE || height > MAX_EDGE) return false;
        if (info.getTarget() != GL11.GL_TEXTURE_2D || info.isWrittenOutsideUploads() || entry.quickRefreshes > MAX_QUICK_REFRESHES) return false;
        final int format = info.getInternalFormat();
        if (format != GL11.GL_RGBA && format != GL11.GL_RGBA8) return false;
        if (info.getMinFilter() != GL11.GL_NEAREST || info.getMagFilter() != GL11.GL_NEAREST) return false;
        return !pbrPipeline || !PBRTextureManager.INSTANCE.hasLoadedPbrTextures(texture);
    }

    private void release(Entry entry) {
        if (entry.slot != null) releasedThisPass.add(entry.slot);
        entry.slot = null;
    }

    void recycleReleasedRegions() {
        if (releasedThisPass.isEmpty()) return;
        freeSlots.addAll(releasedThisPass);
        releasedThisPass.clear();
    }

    public void onTextureDeleted(int texture) {
        final Entry entry = entries.remove(texture);
        if (entry != null) release(entry);
    }

    private Slot takeFreeSlot(int width, int height) {
        for (int i = 0, n = freeSlots.size(); i < n; i++) {
            final Slot free = freeSlots.get(i);
            if (free.width == width && free.height == height) return freeSlots.remove(i);
        }
        return null;
    }

    private Slot placeOnPage(int width, int height) {
        for (int i = 0, n = pages.size(); i <= n; i++) {
            final Page page = i < n ? pages.get(i) : newPage();
            if (page == null) return null;
            final long at = page.place(width + 2, height + 2);
            if (at >= 0) return new Slot(page, (int) (at >>> 32), (int) at, width, height);
        }
        return null;
    }

    private Page newPage() {
        if (pages.size() == MAX_PAGES) return null;
        final int previous = GLStateManager.getBoundTextureForServerState();
        final int texture;
        BatchStateGuard.suspend();
        try {
            texture = GLStateManager.glGenTextures();
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
            GLStateManager.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
            GLStateManager.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, PAGE_SIZE, PAGE_SIZE, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        } finally {
            GLStateManager.glBindTexture(GL11.GL_TEXTURE_2D, previous);
            BatchStateGuard.resume();
        }
        final ResourceLocation location = new ResourceLocation("angelica", "entity_atlas_" + pages.size());
        Minecraft.getMinecraft().getTextureManager().loadTexture(location, new PageTexture(texture));
        final Page page = new Page(location, texture);
        pages.add(page);
        return page;
    }

    private void copy(int texture, Slot slot) {
        final int previousRead = GLStateManager.getReadFramebuffer();
        final int previousDraw = GLStateManager.getDrawFramebuffer();
        final boolean scissor = GLStateManager.getScissorTest().isEnabled();
        BatchStateGuard.suspend();
        try {
            if (readFbo == 0) {
                readFbo = GLStateManager.glGenFramebuffers();
                drawFbo = GLStateManager.glGenFramebuffers();
            }
            if (scissor) GLStateManager.glDisable(GL11.GL_SCISSOR_TEST);
            GLStateManager.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
            GLStateManager.attachTextureForReading(GL30.GL_COLOR_ATTACHMENT0, texture);
            GLStateManager.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
            assert slot.page != null;
            if (drawFboPage != slot.page.texture) {
                GLStateManager.glFramebufferTexture2D(GL30.GL_DRAW_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, slot.page.texture, 0);
                drawFboPage = slot.page.texture;
            }
            final int w = slot.width, h = slot.height, x = slot.x + 1, y = slot.y + 1;
            final int[] fromX = {0, 0, w - 1}, widths =  {w, 1, 1}, toX = {x, x - 1, x + w};
            final int[] fromY = {0, 0, h - 1}, heights = {h, 1, 1}, toY = {y, y - 1, y + h};
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) blit(fromX[col], fromY[row], widths[col], heights[row], toX[col], toY[row]);
            }
        } finally {
            GLStateManager.attachTextureForReading(GL30.GL_COLOR_ATTACHMENT0, 0);
            GLStateManager.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
            GLStateManager.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
            if (scissor) GLStateManager.glEnable(GL11.GL_SCISSOR_TEST);
            BatchStateGuard.resume();
        }
    }

    private static void blit(int srcX, int srcY, int width, int height, int dstX, int dstY) {
        GLStateManager.glBlitFramebuffer(srcX, srcY, srcX + width, srcY + height, dstX, dstY, dstX + width, dstY + height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
    }

    public void clear() {
        for (Page page : pages) page.empty();
        entries.clear();
        freeSlots.clear();
        releasedThisPass.clear();
        lastTexture = -1;
        lastInfo = null;
        lastSlot = null;
    }

    void freePages() {
        if (pages.isEmpty() && readFbo == 0) return;
        clear();
        for (Page page : pages) GLStateManager.glDeleteTextures(page.texture);
        pages.clear();
        if (readFbo != 0) {
            GLStateManager.glDeleteFramebuffers(readFbo);
            GLStateManager.glDeleteFramebuffers(drawFbo);
            readFbo = 0;
            drawFbo = 0;
        }
        drawFboPage = 0;
    }
}
