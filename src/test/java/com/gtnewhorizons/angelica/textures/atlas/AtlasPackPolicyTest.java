package com.gtnewhorizons.angelica.textures.atlas;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gtnewhorizons.angelica.config.AngelicaConfig;

import net.minecraft.client.resources.FallbackResourceManager;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.client.resources.data.IMetadataSerializer;
import net.minecraft.util.ResourceLocation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AtlasPackPolicyTest {

    private String[] savedTrusted;
    private String[] savedDistrusted;
    private String[] savedObserved;

    @BeforeEach
    void saveAndResetConfig() {
        savedTrusted = AngelicaConfig.atlasTrustedPacks;
        savedDistrusted = AngelicaConfig.atlasDistrustedPacks;
        savedObserved = AngelicaConfig.atlasObservedPacks;
        AngelicaConfig.atlasTrustedPacks = new String[0];
        AngelicaConfig.atlasDistrustedPacks = new String[0];
        AngelicaConfig.atlasObservedPacks = new String[0];
    }

    @AfterEach
    void restoreConfig() {
        AngelicaConfig.atlasTrustedPacks = savedTrusted;
        AngelicaConfig.atlasDistrustedPacks = savedDistrusted;
        AngelicaConfig.atlasObservedPacks = savedObserved;
    }

    private static final ResourceLocation PNG = new ResourceLocation("tinker", "textures/items/foo.png");
    private static final ResourceLocation MCMETA = new ResourceLocation("tinker", "textures/items/foo.png.mcmeta");

    private static final class FakePack implements IResourcePack {

        private final ResourceLocation claims;
        private final RuntimeException throwOnExists;

        FakePack(ResourceLocation claims) {
            this.claims = claims;
            this.throwOnExists = null;
        }

        FakePack(RuntimeException throwOnExists) {
            this.claims = null;
            this.throwOnExists = throwOnExists;
        }

        @Override
        public InputStream getInputStream(ResourceLocation location) throws IOException {
            throw new IOException("not used");
        }

        @Override
        public boolean resourceExists(ResourceLocation location) {
            if (throwOnExists != null) {
                throw throwOnExists;
            }
            return location.equals(claims);
        }

        @Override
        public Set<String> getResourceDomains() {
            return Collections.emptySet();
        }

        @Override
        public IMetadataSection getPackMetadata(IMetadataSerializer serializer, String name) throws IOException {
            return null;
        }

        @Override
        public BufferedImage getPackImage() throws IOException {
            return null;
        }

        @Override
        public String getPackName() {
            return "fake";
        }
    }

    @Test
    void noPackClaimsAllowsPrefetch() {
        final List<IResourcePack> packs = List.of(new FakePack(new ResourceLocation("tinker", "unrelated.png")));
        assertFalse(AtlasPackPolicy.isClaimed(packs, PNG));
    }

    @Test
    void packClaimingPngForcesSerial() {
        final List<IResourcePack> packs = List.of(new FakePack(PNG));
        assertTrue(AtlasPackPolicy.isClaimed(packs, PNG));
    }

    @Test
    void packClaimingOnlyMcmetaForcesSerial() {
        final List<IResourcePack> packs = List.of(new FakePack(MCMETA));
        assertTrue(AtlasPackPolicy.isClaimed(packs, PNG));
    }

    @Test
    void resourceExistsThrowingForcesSerial() {
        final List<IResourcePack> packs = List.of(new FakePack(new IllegalStateException("boom")));
        assertTrue(AtlasPackPolicy.isClaimed(packs, PNG));
    }

    @Test
    void setTrustedBuiltInRoundTrips() {
        final String builtIn = AtlasPackPolicy.builtInTrusted().iterator().next();

        AtlasPackPolicy.setTrusted(builtIn, false);
        assertArrayEquals(new String[] { builtIn }, AngelicaConfig.atlasDistrustedPacks);
        assertFalse(AtlasPackPolicy.isTrusted(builtIn));

        AtlasPackPolicy.setTrusted(builtIn, true);
        assertArrayEquals(new String[0], AngelicaConfig.atlasDistrustedPacks);
        assertTrue(AtlasPackPolicy.isTrusted(builtIn));
    }

    @Test
    void setTrustedNonBuiltInMovesBetweenTrustedAndObserved() {
        final String name = "com.example.SomePack";
        AngelicaConfig.atlasObservedPacks = new String[] { name };

        AtlasPackPolicy.setTrusted(name, true);
        assertArrayEquals(new String[] { name }, AngelicaConfig.atlasTrustedPacks);
        assertArrayEquals(new String[0], AngelicaConfig.atlasObservedPacks);
        assertTrue(AtlasPackPolicy.isTrusted(name));

        AtlasPackPolicy.setTrusted(name, false);
        assertArrayEquals(new String[0], AngelicaConfig.atlasTrustedPacks);
        assertArrayEquals(new String[] { name }, AngelicaConfig.atlasObservedPacks);
        assertFalse(AtlasPackPolicy.isTrusted(name));
    }

    @Test
    void setTrustedArraysStaySortedAndDeduplicated() {
        AtlasPackPolicy.setTrusted("b.Pack", true);
        AtlasPackPolicy.setTrusted("a.Pack", true);
        AtlasPackPolicy.setTrusted("a.Pack", true);
        assertArrayEquals(new String[] { "a.Pack", "b.Pack" }, AngelicaConfig.atlasTrustedPacks);

        AtlasPackPolicy.setTrusted("a.Pack", false);
        assertArrayEquals(new String[] { "b.Pack" }, AngelicaConfig.atlasTrustedPacks);
        assertArrayEquals(new String[] { "a.Pack" }, AngelicaConfig.atlasObservedPacks);
    }

    @Test
    void knownPackClassesIsSortedUnion() {
        AngelicaConfig.atlasTrustedPacks = new String[] { "z.Trusted" };
        AngelicaConfig.atlasDistrustedPacks = new String[] { "m.Distrusted" };
        AngelicaConfig.atlasObservedPacks = new String[] { "a.Observed" };

        final List<String> known = AtlasPackPolicy.knownPackClasses();

        assertTrue(known.contains("z.Trusted"));
        assertTrue(known.contains("m.Distrusted"));
        assertTrue(known.contains("a.Observed"));
        for (String builtIn : AtlasPackPolicy.builtInTrusted()) {
            assertTrue(known.contains(builtIn));
        }
        for (String builtIn : AtlasPackPolicy.builtInClaimChecked()) {
            assertTrue(known.contains(builtIn));
        }

        final List<String> sorted = new ArrayList<>(known);
        Collections.sort(sorted);
        assertEquals(sorted, known);
    }

    @Test
    void eligibleDomainsTrustsConfiguredPackClass() {
        AngelicaConfig.atlasTrustedPacks = new String[] { FakePack.class.getName() };

        final IMetadataSerializer serializer = new IMetadataSerializer();
        final SimpleReloadableResourceManager manager = new SimpleReloadableResourceManager(serializer);
        final FallbackResourceManager domain = new FallbackResourceManager(serializer);
        domain.resourcePacks.add(new FakePack(new ResourceLocation("tinker", "unrelated.png")));
        manager.domainResourceManagers.put("domainB", domain);

        final Map<String, List<IResourcePack>> result = AtlasPackPolicy.eligibleDomains(manager);
        assertTrue(result.containsKey("domainB"));
        assertTrue(result.get("domainB").isEmpty());
    }

    @Test
    void eligibleDomainsRecordsUnknownPackOnce() {
        final IMetadataSerializer serializer = new IMetadataSerializer();
        final SimpleReloadableResourceManager manager = new SimpleReloadableResourceManager(serializer);
        final FallbackResourceManager domain = new FallbackResourceManager(serializer);
        domain.resourcePacks.add(new FakePack(new ResourceLocation("tinker", "unrelated.png")));
        manager.domainResourceManagers.put("domainC", domain);

        assertFalse(AtlasPackPolicy.eligibleDomains(manager).containsKey("domainC"));
        final String[] recorded = AngelicaConfig.atlasObservedPacks;
        assertArrayEquals(new String[] { FakePack.class.getName() }, recorded);

        assertFalse(AtlasPackPolicy.eligibleDomains(manager).containsKey("domainC"));
        assertTrue(recorded == AngelicaConfig.atlasObservedPacks);
    }
}
