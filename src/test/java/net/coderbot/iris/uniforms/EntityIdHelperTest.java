package net.coderbot.iris.uniforms;

import it.unimi.dsi.fastutil.objects.Object2IntFunction;
import net.coderbot.iris.block_rendering.BlockRenderingSettings;
import net.coderbot.iris.block_rendering.NbtConditionalIdMap;
import net.coderbot.iris.shaderpack.materialmap.NamespacedId;
import net.coderbot.iris.shaderpack.materialmap.PropertiesTokenizer.NbtValue;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class EntityIdHelperTest {
    @Test
    void replaySharesNormalEntityCacheAndInvalidatesItOnPackChanges() {
        final BlockRenderingSettings settings = BlockRenderingSettings.INSTANCE;
        final Object2IntFunction<NamespacedId> previousIds = settings.getEntityIds();
        final Entity entity = mock(Entity.class);
        final NamespacedId name = new NamespacedId("angelica", "cached_entity_test");
        final String previousName = EntityList.classToStringMapping.put(entity.getClass(), "angelica:cached_entity_test");
        final AtomicInteger lookups = new AtomicInteger();
        try {
            final EntityIdHelper.EntityIdentity identity = EntityIdHelper.snapshot(entity);
            for (int id : new int[] { 17, -1, 23 }) {
                settings.setEntityIds(key -> {
                    if (!name.equals(key)) return -1;
                    lookups.incrementAndGet();
                    return id;
                });
                lookups.set(0);
                for (int i = 0; i < 4; i++) {
                    assertEquals(id, identity.resolve());
                    assertEquals(id, EntityIdHelper.getEntityId(entity));
                }
                assertEquals(1, lookups.get(), "live rendering and replay share one lookup per class and pack");
            }
        } finally {
            settings.setEntityIds(previousIds);
            if (previousName == null) EntityList.classToStringMapping.remove(entity.getClass());
            else EntityList.classToStringMapping.put(entity.getClass(), previousName);
        }
    }

    @Test
    void snapshotKeepsNbtIdentityAcrossFirstPackLoadAndReplacement() {
        final BlockRenderingSettings settings = BlockRenderingSettings.INSTANCE;
        final Object2IntFunction<NamespacedId> previousIds = settings.getEntityIds();
        final NbtConditionalIdMap<NamespacedId> previousNbt = settings.getEntityNbtMap();
        final Entity entity = mock(Entity.class);
        final NamespacedId name = new NamespacedId("angelica", "spawner_entity_test");
        final String previousName = EntityList.classToStringMapping.put(entity.getClass(), "angelica:spawner_entity_test");
        entity.worldObj = mock(World.class);
        final NBTTagCompound liveTag = new NBTTagCompound();
        liveTag.setString("Variant", "original");
        doAnswer(invocation -> {
            invocation.<NBTTagCompound>getArgument(0).setTag("Data", liveTag);
            return null;
        }).when(entity).writeToNBT(any(NBTTagCompound.class));
        try {
            settings.setEntityIds(null);
            settings.setEntityNbtMap(null);
            final EntityIdHelper.EntityIdentity identity = EntityIdHelper.snapshot(entity);
            assertEquals(-1, identity.resolve());
            liveTag.setString("Variant", "changed");
            for (int id : new int[] { 41, 42 }) {
                settings.setEntityIds(key -> name.equals(key) ? 17 : -1);
                final NbtConditionalIdMap<NamespacedId> nbt = new NbtConditionalIdMap<>();
                nbt.addCondition(name, Collections.singletonMap("Data.Variant", new NbtValue("original", true)), id);
                settings.setEntityNbtMap(nbt);
                assertEquals(id, identity.resolve(), "resolve the captured variant against the current pack");
                assertEquals(17, EntityIdHelper.getEntityId(entity), "the live entity has changed variants");
            }
            settings.setEntityNbtMap(null);
            assertEquals(17, identity.resolve());
            settings.setEntityIds(key -> -1);
            assertEquals(-1, identity.resolve());
            assertEquals(-1, EntityIdHelper.getEntityId(null));
            assertNull(EntityIdHelper.snapshot(null));
        } finally {
            settings.setEntityIds(previousIds);
            settings.setEntityNbtMap(previousNbt);
            if (previousName == null) EntityList.classToStringMapping.remove(entity.getClass());
            else EntityList.classToStringMapping.put(entity.getClass(), previousName);
        }
    }
}
