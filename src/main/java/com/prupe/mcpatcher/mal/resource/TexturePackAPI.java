package com.prupe.mcpatcher.mal.resource;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ThreadDownloadImageData;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.resources.FallbackResourceManager;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.io.IOUtils;

import com.prupe.mcpatcher.MCLogger;
import com.prupe.mcpatcher.MCPatcherUtils;

import com.gtnewhorizons.angelica.mixins.interfaces.AbstractTextureExpansion;

public class TexturePackAPI {

    private static final MCLogger logger = MCLogger.getLogger(MCLogger.Category.TEXTURE_PACK);

    public static final String DEFAULT_NAMESPACE = "minecraft";

    public static final String MCPATCHER_SUBDIR = "mcpatcher/";
    public static final ResourceLocation ITEMS_PNG = new ResourceLocation("textures/atlas/items.png");

    private static final String ASSETS = "assets/";

    public static List<IResourcePack> getResourcePacks(String namespace) {
        List<IResourcePack> resourcePacks = new ArrayList<>();
        IResourceManager resourceManager = getResourceManager();
        if (resourceManager instanceof SimpleReloadableResourceManager) {
            Set<Map.Entry<String, FallbackResourceManager>> entrySet = ((SimpleReloadableResourceManager) resourceManager).domainResourceManagers
                .entrySet();
            for (Map.Entry<String, FallbackResourceManager> entry : entrySet) {
                if (namespace == null || namespace.equals(entry.getKey())) {
                    List<IResourcePack> packs = entry.getValue().resourcePacks;
                    if (packs != null) {
                        resourcePacks.removeAll(packs);
                        resourcePacks.addAll(packs);
                    }
                }
            }
        }
        return resourcePacks;
    }

    public static Set<String> getNamespaces() {
        Set<String> namespaces = new HashSet<>();
        namespaces.add(DEFAULT_NAMESPACE);
        IResourceManager resourceManager = getResourceManager();
        if (resourceManager instanceof SimpleReloadableResourceManager simpleReloadableResourceManager) {
            namespaces.addAll(simpleReloadableResourceManager.domainResourceManagers.keySet());
        }
        return namespaces;
    }

    public static boolean isDefaultTexturePack() {
        return getResourcePacks(DEFAULT_NAMESPACE).size() <= 1;
    }

    public static InputStream getInputStream(ResourceLocation resource) {
        try {
            if (resource instanceof ResourceLocationWithSource resourceLocationWithSource) {
                try {
                    return resourceLocationWithSource.getSource()
                        .getInputStream(resource);
                } catch (IOException e) {}
            }
            return resource == null ? null
                : Minecraft.getMinecraft()
                    .getResourceManager()
                    .getResource(resource)
                    .getInputStream();
        } catch (IOException e) {
            return null;
        }
    }

    public static boolean hasResource(ResourceLocation resource) {
        if (resource == null) {
            return false;
        } else if (resource.getResourcePath()
            .endsWith(".png")) {
                return getImage(resource) != null;
            } else if (resource.getResourcePath()
                .endsWith(".properties")) {
                    return getProperties(resource) != null;
                } else {
                    InputStream is = getInputStream(resource);
                    MCPatcherUtils.close(is);
                    return is != null;
                }
    }

    public static boolean hasCustomResource(ResourceLocation resource) {
        InputStream jar = null;
        InputStream pack = null;
        try {
            pack = getInputStream(resource);
            jar = Minecraft.getMinecraft().mcDefaultResourcePack.getInputStream(resource);

            if (pack == null || jar == null) {
                return false;
            }
            return !Arrays.equals(IOUtils.toByteArray(jar), IOUtils.toByteArray(pack));
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            MCPatcherUtils.close(jar);
            MCPatcherUtils.close(pack);
        }
        return false;
    }

    public static BufferedImage getImage(ResourceLocation resource) {
        if (resource == null) {
            return null;
        }
        InputStream input = getInputStream(resource);
        BufferedImage image = null;
        if (input != null) {
            try {
                image = ImageIO.read(input);
            } catch (IOException e) {
                logger.error("could not read %s", resource);
                e.printStackTrace();
            } finally {
                MCPatcherUtils.close(input);
            }
        }
        return image;
    }

    public static Properties getProperties(ResourceLocation resource) {
        Properties properties = new Properties();
        if (getProperties(resource, properties)) {
            return properties;
        } else {
            return null;
        }
    }

    public static boolean getProperties(ResourceLocation resource, Properties properties) {
        if (properties != null) {
            InputStream input = getInputStream(resource);
            try {
                if (input != null) {
                    properties.load(input);
                    return true;
                }
            } catch (IOException e) {
                logger.error("could not read %s", resource);
                e.printStackTrace();
            } finally {
                MCPatcherUtils.close(input);
            }
        }
        return false;
    }

    public static ResourceLocation transformResourceLocation(ResourceLocation resource, String oldExt, String newExt) {
        return new ResourceLocation(
            resource.getResourceDomain(),
            resource.getResourcePath()
                .replaceFirst(Pattern.quote(oldExt) + "$", newExt));
    }

    public static ResourceLocation parsePath(String path) {
        if (MCPatcherUtils.isNullOrEmpty(path)) {
            return null;
        }
        path = path.replace(File.separatorChar, '/');
        if (path.startsWith(ASSETS)) {
            path = path.substring(ASSETS.length());
            int slash = path.indexOf('/');
            if (slash > 0 && slash + 1 < path.length()) {
                return new ResourceLocation(path.substring(0, slash), path.substring(slash + 1));
            }
        }
        return null;
    }

    public static ResourceLocation parseResourceLocation(ResourceLocation baseResource, String path) {
        if (MCPatcherUtils.isNullOrEmpty(path)) {
            return null;
        }
        boolean absolute = false;
        if (path.startsWith("%blur%")) {
            path = path.substring(6);
        }
        if (path.startsWith("%clamp%")) {
            path = path.substring(7);
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
            absolute = true;
        }
        if (path.startsWith("assets/minecraft/")) {
            path = path.substring(17);
            absolute = true;
        }
        // Absolute path, including namespace:
        // namespace:path/filename -> assets/namespace/path/filename
        int colon = path.indexOf(':');
        if (colon >= 0) {
            return new ResourceLocation(path.substring(0, colon), path.substring(colon + 1));
        }
        ResourceLocation resource;
        if (path.startsWith("~/")) {
            // Relative to namespace mcpatcher dir:
            // ~/path -> assets/(namespace of base file)/mcpatcher/path
            resource = new ResourceLocation(baseResource.getResourceDomain(), MCPATCHER_SUBDIR + path.substring(2));
        } else if (path.startsWith("./")) {
            // Relative to properties file:
            // ./path -> (dir of base file)/path
            resource = new ResourceLocation(
                baseResource.getResourceDomain(),
                baseResource.getResourcePath()
                    .replaceFirst("[^/]+$", "") + path.substring(2));
        } else if (!absolute && !path.contains("/")) {
            // Relative to properties file:
            // filename -> (dir of base file)/filename
            resource = new ResourceLocation(
                baseResource.getResourceDomain(),
                baseResource.getResourcePath()
                    .replaceFirst("[^/]+$", "") + path);
        } else {
            // Absolute path, w/o namespace:
            // path/filename -> assets/(namespace of base file)/path/filename
            resource = new ResourceLocation(baseResource.getResourceDomain(), path);
        }
        if (baseResource instanceof ResourceLocationWithSource) {
            resource = new ResourceLocationWithSource(
                ((ResourceLocationWithSource) baseResource).getSource(),
                resource);
        }
        return resource;
    }

    public static ResourceLocation newMCPatcherResourceLocation(String path) {
        return new ResourceLocation(MCPATCHER_SUBDIR + path.replaceFirst("^/+", ""));
    }

    public static int getTextureIfLoaded(ResourceLocation resource) {
        if (resource == null) {
            return -1;
        }
        ITextureObject texture = Minecraft.getMinecraft()
            .getTextureManager()
            .getTexture(resource);
        return texture instanceof AbstractTexture ? texture.getGlTextureId() : -1;
    }

    public static boolean isTextureLoaded(ResourceLocation resource) {
        return getTextureIfLoaded(resource) >= 0;
    }

    public static ITextureObject getTextureObject(ResourceLocation resource) {
        return Minecraft.getMinecraft()
            .getTextureManager()
            .getTexture(resource);
    }

    public static void bindTexture(ResourceLocation resource) {
        if (resource != null) {
            Minecraft.getMinecraft()
                .getTextureManager()
                .bindTexture(resource);
        }
    }

    public static void unloadTexture(ResourceLocation resource) {
        if (resource != null) {
            TextureManager textureManager = Minecraft.getMinecraft()
                .getTextureManager();
            ITextureObject texture = textureManager.getTexture(resource);
            if (texture != null && !(texture instanceof TextureMap) && !(texture instanceof DynamicTexture)) {
                if (texture instanceof AbstractTexture) {
                    ((AbstractTextureExpansion) texture).unloadGLTexture();
                }
                logger.finer("unloading texture %s", resource);
                textureManager.mapTextureObjects.remove(resource);
            }
        }
    }

    public static void flushUnusedTextures() {
        TextureManager textureManager = Minecraft.getMinecraft()
            .getTextureManager();
        if (textureManager != null) {
            Set<ResourceLocation> texturesToUnload = new HashSet<>();
            Set<Map.Entry<ResourceLocation, ITextureObject>> entrySet = textureManager.mapTextureObjects.entrySet();
            for (Map.Entry<ResourceLocation, ITextureObject> entry : entrySet) {
                ResourceLocation resource = entry.getKey();
                ITextureObject texture = entry.getValue();
                if (texture instanceof SimpleTexture && !(texture instanceof ThreadDownloadImageData)
                    && !TexturePackAPI.hasResource(resource)) {
                    texturesToUnload.add(resource);
                }
            }
            for (ResourceLocation resource : texturesToUnload) {
                unloadTexture(resource);
            }
        }
    }

    private static IResourceManager getResourceManager() {
        return Minecraft.getMinecraft()
            .getResourceManager();
    }
}
