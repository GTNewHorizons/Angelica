package com.gtnewhorizons.angelica.textures.atlas;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import cpw.mods.fml.client.FMLFileResourcePack;
import cpw.mods.fml.client.FMLFolderResourcePack;
import net.minecraft.client.resources.DefaultResourcePack;
import net.minecraft.client.resources.FallbackResourceManager;
import net.minecraft.client.resources.FileResourcePack;
import net.minecraft.client.resources.FolderResourcePack;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.util.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public final class AtlasPackPolicy {

    private static final Logger LOGGER = LogManager.getLogger("Angelica");

    private static final Set<String> BUILT_IN_TRUSTED = Set.of(
        "glowredman.txloader.TXResourcePack",
        "com.hfstudio.guidenh.guide.internal.DefaultGuideResourcePackManager$NamedFileResourcePack",
        "com.mitchej123.hodgepodge.mixins.hooks.IC2ResourcePack",
        "ganymedes01.etfuturum.client.BuiltInResourcePack$BuiltInFileResourcePack",
        "ganymedes01.etfuturum.client.BuiltInResourcePack$BuiltInFolderResourcePack",
        "ganymedes01.etfuturum.client.GrayscaleWaterResourcePack",
        "ganymedes01.etfuturum.client.DynamicSoundsResourcePack");

    private static final Set<String> BUILT_IN_CLAIM_CHECKED = Set.of(
        "com.rwtema.extrautils.modintegration.TConTextureResourcePackBedrockium",
        "com.rwtema.extrautils.modintegration.TConTextureResourcePackMagicWood",
        "com.rwtema.extrautils.modintegration.TConTextureResourcePackUnstableIngot",
        "fox.spiteful.avaritia.compat.ticon.InfinityIcons",
        "fox.spiteful.avaritia.compat.ticon.NeutroniumIcons",
        "com.arc.bloodarsenal.common.tinkers.TextureResourcePackBloodInfusedIron");

    private static final ResourceLocation PREWARM = new ResourceLocation("angelica", "atlas_prefetch_prewarm");
    private static final String PREWARM_ITEM_PATH = "textures/items/angelica_atlas_prefetch_prewarm.png";
    private static final Set<String> LOGGED_UNTRUSTED = ConcurrentHashMap.newKeySet();

    private enum Kind {
        PLAIN, ZIP, AUDITED, CLAIM_CHECKED
    }

    private static final Map<Class<?>, Kind> BASE_KINDS = buildBaseKinds();

    private static Map<Class<?>, Kind> buildBaseKinds() {
        final Map<Class<?>, Kind> map = new HashMap<>();
        map.put(DefaultResourcePack.class, Kind.PLAIN);
        map.put(FolderResourcePack.class, Kind.PLAIN);
        map.put(FMLFolderResourcePack.class, Kind.PLAIN);
        map.put(FileResourcePack.class, Kind.ZIP);
        map.put(FMLFileResourcePack.class, Kind.ZIP);
        return map;
    }

    private static final class Trust {
        final Set<String> trusted = new HashSet<>(Arrays.asList(AngelicaConfig.atlasTrustedPacks));
        final Set<String> distrusted = new HashSet<>(Arrays.asList(AngelicaConfig.atlasDistrustedPacks));
        final Set<String> observed = new HashSet<>(Arrays.asList(AngelicaConfig.atlasObservedPacks));
    }

    private AtlasPackPolicy() {
    }

    public static Map<String, List<IResourcePack>> eligibleDomains(SimpleReloadableResourceManager manager) {
        final Trust trust = new Trust();
        final Map<String, List<IResourcePack>> eligible = new HashMap<>();
        final Map<String, List<String>> untrustedDomains = new HashMap<>();
        for (Map.Entry<String, FallbackResourceManager> entry : manager.domainResourceManagers.entrySet()) {
            final List<IResourcePack> claimChecked = eligiblePacks(entry.getKey(), entry.getValue(), trust, untrustedDomains);
            if (claimChecked != null) {
                eligible.put(entry.getKey(), claimChecked);
            }
        }
        recordUntrusted(trust, untrustedDomains);
        return eligible;
    }

    private static List<IResourcePack> eligiblePacks(String domainName, FallbackResourceManager domain, Trust trust,
        Map<String, List<String>> untrustedDomains) {
        if (domain == null || domain.getClass() != FallbackResourceManager.class) {
            return null;
        }
        boolean eligible = true;
        List<IResourcePack> claimChecked = List.of();
        for (IResourcePack pack : domain.resourcePacks) {
            if (pack == null) {
                eligible = false;
                continue;
            }
            Kind kind = BASE_KINDS.get(pack.getClass());
            if (kind == null) {
                final String className = pack.getClass().getName();
                if (trust.distrusted.contains(className)) {
                    eligible = false;
                    continue;
                }
                if (BUILT_IN_TRUSTED.contains(className) || trust.trusted.contains(className)) {
                    kind = Kind.AUDITED;
                } else if (BUILT_IN_CLAIM_CHECKED.contains(className)) {
                    kind = Kind.CLAIM_CHECKED;
                } else {
                    eligible = false;
                    untrustedDomains.computeIfAbsent(className, _ -> new ArrayList<>()).add(domainName);
                    continue;
                }
            }
            switch (kind) {
                case ZIP -> {
                    try {
                        ((FileResourcePack) pack).getResourcePackZipFile();
                    } catch (IOException | RuntimeException _) {
                        eligible = false;
                    }
                }
                case AUDITED -> {
                    try {
                        pack.resourceExists(PREWARM);
                    } catch (RuntimeException _) {
                        eligible = false;
                    }
                }
                case CLAIM_CHECKED -> {
                    try {
                        pack.resourceExists(new ResourceLocation(domainName, PREWARM_ITEM_PATH));
                        if (claimChecked.isEmpty()) {
                            claimChecked = new ArrayList<>(4);
                        }
                        claimChecked.add(pack);
                    } catch (RuntimeException _) {
                        eligible = false;
                    }
                }
                case PLAIN -> {}
            }
        }
        return eligible ? claimChecked : null;
    }

    private static void recordUntrusted(Trust trust, Map<String, List<String>> untrustedDomains) {
        if (untrustedDomains.isEmpty()) {
            return;
        }
        boolean changed = false;
        final TreeSet<String> observed = new TreeSet<>(trust.observed);
        for (Map.Entry<String, List<String>> entry : untrustedDomains.entrySet()) {
            final String className = entry.getKey();
            if (LOGGED_UNTRUSTED.add(className)) {
                LOGGER.info("Resource pack {} is not trusted for parallel texture loading; domains {} load serially. Trust it in Video Settings > Performance > Texture loading.", className, entry.getValue());
            }
            if (observed.add(className)) {
                changed = true;
            }
        }
        if (changed) {
            AngelicaConfig.atlasObservedPacks = observed.toArray(String[]::new);
        }
    }

    static boolean isClaimed(List<IResourcePack> claimCheckedPacks, ResourceLocation candidate) {
        if (claimCheckedPacks.isEmpty()) {
            return false;
        }
        final ResourceLocation mcmeta = new ResourceLocation(candidate.getResourceDomain(), candidate.getResourcePath() + ".mcmeta");
        for (IResourcePack pack : claimCheckedPacks) {
            try {
                if (pack.resourceExists(candidate) || pack.resourceExists(mcmeta)) {
                    return true;
                }
            } catch (RuntimeException _) {
                return true;
            }
        }
        return false;
    }

    public static Set<String> builtInTrusted() {
        return BUILT_IN_TRUSTED;
    }

    public static Set<String> builtInClaimChecked() {
        return BUILT_IN_CLAIM_CHECKED;
    }

    public static boolean isBuiltIn(String className) {
        return BUILT_IN_TRUSTED.contains(className) || BUILT_IN_CLAIM_CHECKED.contains(className);
    }

    public static boolean isTrusted(String className) {
        if (Arrays.asList(AngelicaConfig.atlasDistrustedPacks).contains(className)) {
            return false;
        }
        if (isBuiltIn(className)) {
            return true;
        }
        return Arrays.asList(AngelicaConfig.atlasTrustedPacks).contains(className);
    }

    public static void setTrusted(String className, boolean trusted) {
        if (isBuiltIn(className)) {
            if (trusted) {
                AngelicaConfig.atlasDistrustedPacks = withRemoved(AngelicaConfig.atlasDistrustedPacks, className);
            } else {
                AngelicaConfig.atlasDistrustedPacks = withAdded(AngelicaConfig.atlasDistrustedPacks, className);
            }
            return;
        }
        if (trusted) {
            AngelicaConfig.atlasTrustedPacks = withAdded(AngelicaConfig.atlasTrustedPacks, className);
            AngelicaConfig.atlasObservedPacks = withRemoved(AngelicaConfig.atlasObservedPacks, className);
        } else {
            AngelicaConfig.atlasTrustedPacks = withRemoved(AngelicaConfig.atlasTrustedPacks, className);
            AngelicaConfig.atlasObservedPacks = withAdded(AngelicaConfig.atlasObservedPacks, className);
        }
    }

    public static List<String> knownPackClasses() {
        final TreeSet<String> all = new TreeSet<>();
        all.addAll(BUILT_IN_TRUSTED);
        all.addAll(BUILT_IN_CLAIM_CHECKED);
        all.addAll(Arrays.asList(AngelicaConfig.atlasTrustedPacks));
        all.addAll(Arrays.asList(AngelicaConfig.atlasDistrustedPacks));
        all.addAll(Arrays.asList(AngelicaConfig.atlasObservedPacks));
        return new ArrayList<>(all);
    }

    private static String[] withAdded(String[] array, String value) {
        final TreeSet<String> set = new TreeSet<>(Arrays.asList(array));
        set.add(value);
        return set.toArray(String[]::new);
    }

    private static String[] withRemoved(String[] array, String value) {
        final TreeSet<String> set = new TreeSet<>(Arrays.asList(array));
        set.remove(value);
        return set.toArray(String[]::new);
    }
}
