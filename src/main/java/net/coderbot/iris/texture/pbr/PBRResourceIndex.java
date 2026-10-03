package net.coderbot.iris.texture.pbr;

import com.gtnewhorizons.angelica.glsm.threading.AngelicaWorkers;
import cpw.mods.fml.client.FMLFileResourcePack;
import cpw.mods.fml.client.FMLFolderResourcePack;
import net.minecraft.client.resources.AbstractResourcePack;
import net.minecraft.client.resources.FallbackResourceManager;
import net.minecraft.client.resources.FileResourcePack;
import net.minecraft.client.resources.FolderResourcePack;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class PBRResourceIndex {
    private static final Logger LOGGER = LogManager.getLogger("Angelica");
    private static final String ASSETS = "assets";
    private static final String NORMAL_NAME = PBRType.NORMAL.getSuffix() + ".png";
    private static final String SPECULAR_NAME = PBRType.SPECULAR.getSuffix() + ".png";

    private static IResourceManager owner;
    private static CompletableFuture<Map<IResourcePack, PackIndex>> pending;
    private static Map<IResourcePack, PackIndex> built;
    private static boolean disabled;
    private static boolean warned;

    private PBRResourceIndex() {
    }

    private record PackIndex(Set<String> names, boolean lowerCase) {}

    private record ScanTarget(IResourcePack pack, ZipFile zip, File folder) {}

    public static synchronized void start(IResourceManager resourceManager) {
        drop();
        owner = resourceManager;
        disabled = false;
        if (!(resourceManager instanceof SimpleReloadableResourceManager manager)) {
            return;
        }
        try {
            final List<ScanTarget> targets = collectTargets(manager);
            pending = AngelicaWorkers.submit(() -> scan(targets));
        } catch (RuntimeException e) {
            disable(e);
        }
    }

    private static synchronized void drop() {
        owner = null;
        pending = null;
        built = null;
    }

    public static synchronized boolean mayExist(IResourceManager resourceManager, ResourceLocation location) {
        if (!(resourceManager instanceof SimpleReloadableResourceManager manager)) {
            return true;
        }
        final String path = location.getResourcePath();
        if (!path.endsWith(NORMAL_NAME) && !path.endsWith(SPECULAR_NAME)) {
            return true;
        }
        if (owner != resourceManager) {
            start(resourceManager);
        }
        if (disabled) {
            return true;
        }
        if (built == null) {
            try {
                built = pending.join();
            } catch (RuntimeException e) {
                disable(e);
                return true;
            }
        }
        final Object domain = manager.domainResourceManagers.get(location.getResourceDomain());
        if (domain == null) {
            return false;
        }
        if (!(domain instanceof FallbackResourceManager fallback)) {
            return true;
        }
        final String name = ASSETS + "/" + location.getResourceDomain() + "/" + path;
        String lower = null;
        final List<IResourcePack> packs = fallback.resourcePacks;
        for (int i = packs.size() - 1; i >= 0; i--) {
            final IResourcePack pack = packs.get(i);
            final PackIndex index = built.get(pack);
            final boolean present;
            if (index == null) {
                present = pack.resourceExists(location);
            } else if (index.lowerCase()) {
                if (lower == null) lower = name.toLowerCase(Locale.ROOT);
                present = index.names().contains(lower);
            } else {
                present = index.names().contains(name);
            }
            if (present) {
                return true;
            }
        }
        return false;
    }

    private static void disable(RuntimeException e) {
        disabled = true;
        built = null;
        pending = null;
        if (!warned) {
            warned = true;
            LOGGER.warn("PBR resource index disabled, falling back to direct lookups", e);
        }
    }

    private static List<ScanTarget> collectTargets(SimpleReloadableResourceManager manager) {
        final Set<IResourcePack> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        final List<ScanTarget> targets = new ArrayList<>();
        for (Object domain : manager.domainResourceManagers.values()) {
            if (domain instanceof FallbackResourceManager fallback) {
                for (IResourcePack pack : fallback.resourcePacks) {
                    if (seen.add(pack)) {
                        final ScanTarget target = target(pack);
                        if (target != null) {
                            targets.add(target);
                        }
                    }
                }
            }
        }
        return targets;
    }

    private static ScanTarget target(IResourcePack pack) {
        final Class<?> type = pack.getClass();
        if (type == FileResourcePack.class || type == FMLFileResourcePack.class) {
            final FileResourcePack zipPack = (FileResourcePack) pack;
            try {
                return new ScanTarget(pack, zipPack.getResourcePackZipFile(), null);
            } catch (IOException e) {
                throw new IllegalStateException("Unable to index " + zipPack.resourcePackFile, e);
            }
        }
        if (type == FolderResourcePack.class || type == FMLFolderResourcePack.class) {
            return new ScanTarget(pack, null, ((AbstractResourcePack) pack).resourcePackFile);
        }
        return null;
    }

    private static Map<IResourcePack, PackIndex> scan(List<ScanTarget> targets) {
        final Map<IResourcePack, PackIndex> result = new IdentityHashMap<>();
        for (ScanTarget t : targets) {
            result.put(t.pack(), t.zip() != null ? scanZip(t.zip()) : scanFolder(t.folder()));
        }
        return result;
    }

    private static boolean isPbrName(String name) {
        return name.endsWith(NORMAL_NAME) || name.endsWith(SPECULAR_NAME);
    }

    private static PackIndex scanZip(ZipFile zip) {
        final Set<String> names = new HashSet<>();
        final Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            final String name = entries.nextElement().getName();
            final String key = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
            if (isPbrName(key)) {
                names.add(key);
            }
        }
        return new PackIndex(names, false);
    }

    private static PackIndex scanFolder(File file) {
        final Set<String> names = new HashSet<>();
        final Path root = file.toPath();
        final Path assets = root.resolve(ASSETS);
        final boolean caseInsensitive = Files.exists(root.resolve(ASSETS.toUpperCase(Locale.ROOT)));
        if (Files.isDirectory(assets)) {
            try {
                Files.walkFileTree(assets, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                        final String relative = root.relativize(path).toString().replace(File.separatorChar, '/');
                        final String name = caseInsensitive ? relative.toLowerCase(Locale.ROOT) : relative;
                        if (isPbrName(name)) {
                            names.add(name);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path path, IOException e) throws IOException {
                        if (e instanceof NoSuchFileException) {
                            return FileVisitResult.CONTINUE;
                        }
                        throw e;
                    }
                });
            } catch (IOException e) {
                throw new IllegalStateException("Unable to index " + file, e);
            }
        }
        return new PackIndex(names, caseInsensitive);
    }
}
