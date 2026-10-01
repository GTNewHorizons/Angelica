package com.gtnewhorizons.angelica.textures.atlas;

import com.gtnewhorizon.gtnhlib.config.ConfigurationManager;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.glsm.threading.AngelicaWorkers;
import com.gtnewhorizons.angelica.utils.PrefetchLane;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;

public final class AtlasLoadSession {

    private static final Logger LOGGER = LogManager.getLogger("Angelica");
    private static final Consumer<TextureAtlasSprite> KEEP = _ -> {};

    public record Decoded(ResourceLocation location, IResource resource, BufferedImage image) {}

    private final IResourceManager manager;
    private final int textureType;
    private final boolean decodeEnabled;
    private final boolean mipmapsEnabled;
    private int eligibleDomains;
    private PrefetchLane<TextureAtlasSprite, Decoded> decodes;
    private Function<InputStream, BufferedImage> decoder;
    private InputStream pendingStream;
    private BufferedImage pendingImage;
    private int substituted;
    private PrefetchLane<TextureAtlasSprite, TextureAtlasSprite> mipmaps;
    private int mipmapsOffloaded;
    private int mipmapLevels;
    private boolean closed;

    private AtlasLoadSession(TextureMap map, IResourceManager manager, boolean decodeEnabled, boolean mipmapsEnabled) {
        this.manager = manager;
        this.textureType = map.getTextureType();
        this.decodeEnabled = decodeEnabled;
        this.mipmapsEnabled = mipmapsEnabled;
    }

    public static AtlasLoadSession begin(TextureMap map, IResourceManager manager) {
        final boolean decodeEnabled = AngelicaConfig.enableParallelAtlasDecode;
        final boolean mipmapsEnabled = AngelicaConfig.enableParallelAtlasMipmaps;
        if ((!decodeEnabled && !mipmapsEnabled) || map.skipFirst) {
            return null;
        }
        final AtlasLoadSession session = new AtlasLoadSession(map, manager, decodeEnabled, mipmapsEnabled);
        if (!decodeEnabled) {
            return session;
        }
        if (manager.getClass() != SimpleReloadableResourceManager.class || !metadataReady()) {
            LOGGER.warn("Parallel atlas decode disabled for atlas {}: resource manager {} or metadata serializer not supported", session.textureType, manager.getClass().getName());
            return session;
        }
        final String[] observed = AngelicaConfig.atlasObservedPacks;
        final Map<String, List<IResourcePack>> eligible = AtlasPackPolicy.eligibleDomains((SimpleReloadableResourceManager) manager);
        if (AngelicaConfig.atlasObservedPacks != observed) {
            ConfigurationManager.save(AngelicaConfig.class);
        }
        session.eligibleDomains = eligible.size();
        if (eligible.isEmpty()) {
            return session;
        }
        final PrefetchLane<TextureAtlasSprite, Decoded> lane = new PrefetchLane<>(AngelicaWorkers::submit, lookahead(), AtlasLoadSession::dispose);
        final Map<String, TextureAtlasSprite> sprites = map.mapRegisteredSprites;
        for (Map.Entry<String, TextureAtlasSprite> entry : sprites.entrySet()) {
            final TextureAtlasSprite sprite = entry.getValue();
            lane.add(sprite, session.decodeTask(map, eligible, entry.getKey(), sprite));
        }
        session.decodes = lane;
        return session;
    }

    private static int lookahead() {
        return 32 * AngelicaWorkers.threads();
    }

    private static boolean metadataReady() {
        final IMetadataSerializer serializer = Minecraft.getMinecraft().metadataSerializer_;
        return serializer != null && serializer.gson != null;
    }

    private Callable<Decoded> decodeTask(TextureMap map, Map<String, List<IResourcePack>> eligible, String key,
        TextureAtlasSprite sprite) {
        if (sprite == null || sprite.getClass() != TextureAtlasSprite.class) {
            return null;
        }
        final ResourceLocation candidate;
        final List<IResourcePack> claimChecked;
        try {
            final ResourceLocation location = new ResourceLocation(key);
            claimChecked = eligible.get(location.getResourceDomain());
            if (claimChecked == null) {
                return null;
            }
            candidate = map.completeResourceLocation(location, 0);
        } catch (RuntimeException _) {
            return null;
        }
        if (!claimChecked.isEmpty() && AtlasPackPolicy.isClaimed(claimChecked, candidate)) {
            return null;
        }
        return () -> decode(candidate);
    }

    private Decoded decode(ResourceLocation candidate) throws IOException {
        IResource resource = null;
        BufferedImage image = null;
        try {
            resource = this.manager.getResource(candidate);
            image = this.decoder.apply(resource.getInputStream());
            if (image == null) {
                IOUtils.closeQuietly(resource.getInputStream());
                return null;
            }
            resource.getMetadata("texture");
            resource.getMetadata("animation");
            return new Decoded(candidate, resource, image);
        } catch (Throwable t) {
            closeImage(image);
            if (resource != null) {
                IOUtils.closeQuietly(resource.getInputStream());
            }
            throw t;
        }
    }

    public IResource takeResource(IResourceManager manager, TextureAtlasSprite sprite, ResourceLocation location) {
        if (this.decodes == null) {
            return null;
        }
        releasePending();
        final Decoded decoded = this.decodes.take(sprite);
        if (decoded == null) {
            return null;
        }
        if (manager != this.manager || !location.equals(decoded.location())) {
            dispose(decoded);
            return null;
        }
        this.pendingStream = decoded.resource().getInputStream();
        this.pendingImage = decoded.image();
        this.substituted++;
        return decoded.resource();
    }

    public BufferedImage takeImage(InputStream stream) {
        if (this.pendingStream == null || stream != this.pendingStream) {
            return null;
        }
        final BufferedImage image = this.pendingImage;
        this.pendingStream = null;
        this.pendingImage = null;
        return image;
    }

    public boolean wantsDecoder() {
        return this.decodes != null && this.decoder == null;
    }

    public void onMainDecode(Function<InputStream, BufferedImage> decoder) {
        if (this.decodes == null || this.decoder != null) {
            return;
        }
        this.decoder = decoder;
        this.decodes.start();
    }

    public void beginMipmaps(Collection<TextureAtlasSprite> sprites, int levels) {
        closeDecodes();
        if (!this.mipmapsEnabled || this.mipmaps != null) {
            return;
        }
        this.mipmapLevels = levels;
        final PrefetchLane<TextureAtlasSprite, TextureAtlasSprite> lane = new PrefetchLane<>(AngelicaWorkers::submit, lookahead(), KEEP);
        final Set<TextureAtlasSprite> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (TextureAtlasSprite sprite : sprites) {
            if (sprite != null && MipmapSafety.isSafe(sprite.getClass()) && seen.add(sprite)) {
                lane.add(sprite, () -> {
                    sprite.generateMipmaps(levels);
                    return sprite;
                });
            } else {
                lane.add(sprite, null);
            }
        }
        this.mipmaps = lane;
        lane.start();
    }

    public boolean joinMipmaps(TextureAtlasSprite sprite, int levels) {
        if (this.mipmaps == null) {
            return false;
        }
        final boolean joined = this.mipmaps.take(sprite) != null;
        if (joined && levels == this.mipmapLevels) {
            this.mipmapsOffloaded++;
            return true;
        }
        if (this.mipmaps.isDesynced()) {
            this.mipmaps.awaitQuiescence();
        }
        return false;
    }

    public void close() {
        if (this.closed) {
            return;
        }
        this.closed = true;
        closeDecodes();
        if (this.mipmaps != null) {
            this.mipmaps.close();
            this.mipmaps.awaitQuiescence();
        }
        final PrefetchLane<TextureAtlasSprite, Decoded> d = this.decodes;
        final PrefetchLane<TextureAtlasSprite, TextureAtlasSprite> m = this.mipmaps;
        LOGGER.debug("Parallel atlas load {}: {} eligible domains, {} decodes submitted, {} substituted, {} decode failures (first: {}), decode desync {}, {} mipmaps offloaded, {} mipmap failures (first: {}), mipmap desync {}",
            this.textureType, this.eligibleDomains,
            d == null ? 0 : d.submitted(), this.substituted,
            d == null ? 0 : d.failures(), d == null ? null : d.firstFailure(), d != null && d.isDesynced(),
            this.mipmapsOffloaded,
            m == null ? 0 : m.failures(), m == null ? null : m.firstFailure(), m != null && m.isDesynced());
    }

    private void closeDecodes() {
        if (this.decodes != null) {
            this.decodes.close();
            this.decodes.awaitQuiescence();
        }
        releasePending();
    }

    private void releasePending() {
        if (this.pendingImage != null) {
            closeImage(this.pendingImage);
        }
        this.pendingStream = null;
        this.pendingImage = null;
    }

    private static void dispose(Decoded decoded) {
        closeImage(decoded.image());
        IOUtils.closeQuietly(decoded.resource().getInputStream());
    }

    private static void closeImage(BufferedImage image) {
        if (image instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception _) {}
        }
    }
}
