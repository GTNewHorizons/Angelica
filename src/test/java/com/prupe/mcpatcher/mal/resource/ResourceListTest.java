package com.prupe.mcpatcher.mal.resource;

import net.minecraft.client.resources.FileResourcePack;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;
import org.junit.jupiter.api.Test;

import com.gtnewhorizons.angelica.glsm.testutil.Reflect;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourceListTest {

    private static File buildZip(String prefix, String... entries) throws IOException {
        File file = File.createTempFile(prefix, ".zip");
        file.deleteOnExit();
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file))) {
            for (String entry : entries) {
                zos.putNextEntry(new ZipEntry(entry));
                if (!entry.endsWith("/")) {
                    zos.write(("data:" + entry).getBytes(StandardCharsets.UTF_8));
                }
                zos.closeEntry();
            }
        }
        return file;
    }

    private static ResourceList newAggregate(List<IResourcePack> packs) throws ReflectiveOperationException {
        Map<IResourcePack, Integer> orderMap = Reflect.getStatic(ResourceList.class, "resourcePackOrder");
        orderMap.clear();
        int order = packs.size();
        for (IResourcePack pack : packs) {
            orderMap.put(pack, order);
            order--;
        }
        Constructor<ResourceList> ctor = ResourceList.class.getDeclaredConstructor(List.class);
        ctor.setAccessible(true);
        return ctor.newInstance(packs);
    }

    private static List<String> describe(Iterable<? extends ResourceLocation> resources, List<IResourcePack> packs) {
        List<String> out = new ArrayList<>();
        for (ResourceLocation r : resources) {
            ResourceLocationWithSource rs = (ResourceLocationWithSource) r;
            out.add(rs.getResourceDomain() + ":" + rs.getResourcePath() + (rs.isDirectory() ? "/" : "") + "@"
                + "ABC".charAt(packs.indexOf(rs.getSource())));
        }
        return out;
    }

    @Test
    void laterPacksWinAndFirstEntryWinsWithinPack() throws Exception {
        File zipA = buildZip(
            "packA",
            "assets/minecraft/textures/blocks/foo.png",
            "assets/minecraft/textures/blocks/",
            "assets/minecraft/textures/blocks/nested/bar.png",
            "assets/minecraft/textures/blocks/nested/",
            "assets/minecraft/textures/shared.png",
            "assets/minecraft/mcpatcher/ctm/rule1.properties",
            "assets/other/textures/thing.png",
            "assets/minecraft/textures/dupe",
            "assets/minecraft/textures/dupe/",
            "pack.mcmeta");
        File zipB = buildZip(
            "packB",
            "assets/minecraft/textures/blocks/foo.png",
            "assets/minecraft/textures/onlyB.png",
            "assets/minecraft/textures/blocks/",
            "META-INF/MANIFEST.MF");
        File zipC = buildZip(
            "packC",
            "assets/minecraft/textures/blocks/foo.png",
            "assets/minecraft/textures/shared.png",
            "assets/minecraft/mcpatcher/ctm/rule1.properties",
            "assets/minecraft/textures/blocks/nested/bar.png",
            "assets/minecraft/textures/onlyC.png",
            "assets/minecraft/mcpatcher/ctm/",
            "assets/minecraft/textures/",
            "README.txt");

        FileResourcePack packA = new FileResourcePack(zipA);
        FileResourcePack packB = new FileResourcePack(zipB);
        FileResourcePack packC = new FileResourcePack(zipC);
        List<IResourcePack> packs = List.of(packA, packB, packC);

        try {
            ResourceList list = newAggregate(packs);

            Map<ResourceLocationWithSource, ResourceLocationWithSource> resources = Reflect.get(list, "resources");
            List<ResourceLocationWithSource> sorted = new ArrayList<>(resources.values());
            sorted.sort(new ResourceLocationWithSource.Comparator1());
            assertEquals(
                List.of(
                    "minecraft:mcpatcher/ctm/@C",
                    "minecraft:mcpatcher/ctm/rule1.properties@C",
                    "minecraft:textures/@C",
                    "minecraft:textures/blocks/@B",
                    "minecraft:textures/blocks/foo.png@C",
                    "minecraft:textures/blocks/nested/@A",
                    "minecraft:textures/blocks/nested/bar.png@C",
                    "minecraft:textures/dupe@A",
                    "minecraft:textures/onlyB.png@B",
                    "minecraft:textures/onlyC.png@C",
                    "minecraft:textures/shared.png@C",
                    "other:textures/thing.png@A"),
                describe(sorted, packs));

            assertEquals(
                List.of(
                    "minecraft:textures/blocks/foo.png@C",
                    "minecraft:textures/blocks/nested/bar.png@C",
                    "minecraft:textures/onlyC.png@C",
                    "minecraft:textures/shared.png@C",
                    "minecraft:textures/onlyB.png@B",
                    "other:textures/thing.png@A"),
                describe(list.listResources(null, "textures", ".png", true, false, false), packs));

            assertEquals(
                List.of(
                    "minecraft:textures/onlyC.png@C",
                    "minecraft:textures/shared.png@C",
                    "minecraft:textures/onlyB.png@B",
                    "other:textures/thing.png@A"),
                describe(list.listResources(null, "textures", ".png", false, false, false), packs));

            assertEquals(
                List.of("minecraft:mcpatcher/ctm/rule1.properties@C"),
                describe(list.listResources("minecraft", "mcpatcher/ctm", ".properties", true, false, true), packs));
        } finally {
            packA.close();
            packB.close();
            packC.close();
        }
    }
}
