package jss.notfine.gui.options.named;

import me.jellysquid.mods.sodium.client.gui.options.named.NamedState;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.ResourceLocation;

import java.util.Random;

public enum BackgroundSelect implements NamedState {
    /* getTexture() expects this to be the first value */
    RANDOM("options.background.random", null),

    DEFAULT("generator.default", (Gui.optionsBackground != null) ? Gui.optionsBackground : new ResourceLocation("textures/gui/options_background.png")),
    SAND("tile.sand.default.name", new ResourceLocation("textures/blocks/sand.png")),
    GRAVEL("tile.gravel.name", new ResourceLocation("textures/blocks/gravel.png")),
    MYCELIUM("tile.mycel.name", new ResourceLocation("textures/blocks/mycelium_top.png")),
    STONE("tile.stone.name", new ResourceLocation("textures/blocks/stone.png")),
    STONEBRICK("tile.stonebricksmooth.name", new ResourceLocation("textures/blocks/stonebrick.png")),
    MOSSY_STONEBRICK("tile.stonebricksmooth.mossy.name", new ResourceLocation("textures/blocks/stonebrick_mossy.png")),
    COBBLESTONE("tile.stonebrick.name", new ResourceLocation("textures/blocks/cobblestone.png")),
    OAK_PLANKS("tile.wood.oak.name", new ResourceLocation("textures/blocks/planks_oak.png")),
    BIRCH_PLANKS("tile.wood.birch.name", new ResourceLocation("textures/blocks/planks_birch.png")),
    NETHER_BRICKS("tile.netherBrick.name", new ResourceLocation("textures/blocks/nether_brick.png")),
    SOUL_SAND("tile.hellsand.name", new ResourceLocation("textures/blocks/soul_sand.png")),
    SPONGE("tile.sponge.name", new ResourceLocation("textures/blocks/sponge.png")),
    OBSIDIAN("tile.obsidian.name", new ResourceLocation("textures/blocks/obsidian.png"));

    private final String name;
    private final ResourceLocation texture;

    private final Random random = new Random();

    BackgroundSelect(String name, ResourceLocation texture) {
        this.name = name;
        this.texture = texture;
    }

    @Override
    public String getKey() {
        return this.name;
    }

    public ResourceLocation getTexture() {
        if (this == RANDOM) return values()[1 + random.nextInt(values().length - 1)].getTexture();
        else return this.texture;
    }

}

