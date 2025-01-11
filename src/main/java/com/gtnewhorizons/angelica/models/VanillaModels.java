package com.gtnewhorizons.angelica.models;

import com.gtnewhorizon.gtnhlib.blockpos.IBlockPos;
import com.gtnewhorizon.gtnhlib.client.model.ModelLoader;
import com.gtnewhorizon.gtnhlib.client.model.ModelVariant;
import com.gtnewhorizon.gtnhlib.client.model.json.ModelLocation;
import com.gtnewhorizon.gtnhlib.client.model.template.BlockColoredCube;
import com.gtnewhorizon.gtnhlib.client.model.template.BlockStaticCube;
import com.gtnewhorizon.gtnhlib.client.model.template.Column3Rot;
import com.gtnewhorizon.gtnhlib.client.model.template.Model4Rot;
import com.gtnewhorizon.gtnhlib.client.renderer.quad.QuadProvider;
import com.gtnewhorizon.gtnhlib.client.renderer.quad.QuadView;
import com.gtnewhorizons.angelica.config.AngelicaConfig;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;
import net.minecraft.block.Block;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

public class VanillaModels {

    private static boolean init = false;

    public static final BlockStaticCube STONE = new BlockStaticCube("stone");
    public static final BlockStaticCube GLASS = new BlockStaticCube("glass");

    public static BlockColoredCube OAK_LEAVES;
    public static BlockColoredCube SPRUCE_LEAVES;
    public static BlockColoredCube BIRCH_LEAVES;
    public static BlockColoredCube JUNGLE_LEAVES;
    public static QuadProvider OLD_LEAF = new QuadProvider() {
        @Override
        public int getColor(IBlockAccess world, int x, int y, int z, Block block, int meta, Random random) {
            return QuadProvider.getDefaultColor(world, x, y, z, block);
        }

        @Override
        public List<QuadView> getQuads(IBlockAccess world, int x, int y, int z, Block block, int meta, ForgeDirection dir, Random random, int color, Supplier<QuadView> quadPool) {
            return (switch (meta % 4) {
                case 0 -> OAK_LEAVES;
                case 1 -> SPRUCE_LEAVES;
                case 2 -> BIRCH_LEAVES;
                case 3 -> JUNGLE_LEAVES;
                default -> throw new IllegalStateException("Unexpected value: " + meta);
            }).getQuads(world, x, y, z, block, meta, dir, random, color, quadPool);
        }
    };

    public static BlockColoredCube ACACIA_LEAVES;
    public static BlockColoredCube DARK_OAK_LEAVES;
    public static QuadProvider NEW_LEAF = new QuadProvider() {
        @Override
        public int getColor(IBlockAccess world, int x, int y, int z, Block block, int meta, Random random) {
            return QuadProvider.getDefaultColor(world, x, y, z, block);
        }

        @Override
        public List<QuadView> getQuads(IBlockAccess world, int x, int y, int z, Block block, int meta, ForgeDirection dir, Random random, int color, Supplier<QuadView> quadPool) {
            return (switch (meta % 2) {
                case 0 -> ACACIA_LEAVES;
                case 1 -> DARK_OAK_LEAVES;
                default -> throw new IllegalStateException("Unexpected value: " + meta);
            }).getQuads(world, x, y, z, block, meta, dir, random, color, quadPool);
        }
    };

    public static Column3Rot OAK_LOG;
    public static Column3Rot SPRUCE_LOG;
    public static Column3Rot BIRCH_LOG;
    public static Column3Rot JUNGLE_LOG;
    public static BlockStaticCube OAK_WOOD;
    public static BlockStaticCube BIRCH_WOOD;
    public static BlockStaticCube SPRUCE_WOOD;
    public static BlockStaticCube JUNGLE_WOOD;
    public static QuadProvider OLD_LOG = new QuadProvider() {
        @Override
        public List<QuadView> getQuads(IBlockAccess world, int x, int y, int z, Block block, int meta, ForgeDirection dir, Random random, int color, Supplier<QuadView> quadPool) {

            if (meta > 11) {
                return (switch (meta) {
                    case 12 -> OAK_WOOD;
                    case 13 -> SPRUCE_WOOD;
                    case 14 -> BIRCH_WOOD;
                    case 15 -> JUNGLE_WOOD;
                    default -> throw new IllegalStateException("Unexpected value: " + meta);
                }).getQuads(world, x, y, z, block, meta, dir, random, color, quadPool);
            }

            Column3Rot ret = switch (meta % 4) {
                case 0 -> OAK_LOG;
                case 1 -> SPRUCE_LOG;
                case 2 -> BIRCH_LOG;
                case 3 -> JUNGLE_LOG;
                default -> throw new IllegalStateException("Unexpected value: " + meta);
            };

            return (switch (meta / 4) {
                case 0 -> ret.updown();
                case 1 -> ret.eastwest();
                case 2 -> ret.northsouth();
                default -> throw new IllegalStateException("Unexpected value: " + meta);
            }).getQuads(world, x, y, z, block, meta, dir, random, color, quadPool);
        }
    };

    public static Column3Rot ACACIA_LOG;
    public static Column3Rot DARK_OAK_LOG;
    public static BlockStaticCube ACACIA_WOOD;
    public static BlockStaticCube DARK_OAK_WOOD;
    public static QuadProvider NEW_LOG = new QuadProvider() {
        @Override
        public List<QuadView> getQuads(IBlockAccess world, int x, int y, int z, Block block, int meta, ForgeDirection dir, Random random, int color, Supplier<QuadView> quadPool) {

            if (meta > 11) {
                return (switch (meta) {
                    case 12 -> ACACIA_WOOD;
                    case 13 -> DARK_OAK_WOOD;
                    default -> throw new IllegalStateException("Unexpected value: " + meta);
                }).getQuads(world, x, y, z, block, meta, dir, random, color, quadPool);
            }

            Column3Rot ret = switch (meta % 4) {
                case 0 -> ACACIA_LOG;
                case 1 -> DARK_OAK_LOG;
                default -> throw new IllegalStateException("Unexpected value: " + meta);
            };

            return (switch (meta / 4) {
                case 0 -> ret.updown();
                case 1 -> ret.eastwest();
                case 2 -> ret.northsouth();
                default -> throw new IllegalStateException("Unexpected value: " + meta);
            }).getQuads(world, x, y, z, block, meta, dir, random, color, quadPool);
        }
    };

    public static final ModelVariant workbench = new ModelVariant(
        new ModelLocation("block/crafting_table"),
        0,
        0,
        true
    );
    public static QuadProvider WORKBENCH;

    public static Model4Rot LECTERN;

    public static void init() {

        if (init) {
            throw new RuntimeException("Vanilla models were baked twice!");
        }

        if (AngelicaConfig.injectQPRendering) {

            ACACIA_LEAVES = new BlockColoredCube("leaves_acacia");
            BIRCH_LEAVES = new BlockColoredCube("leaves_birch");
            DARK_OAK_LEAVES = new BlockColoredCube("leaves_big_oak");
            JUNGLE_LEAVES = new BlockColoredCube("leaves_jungle");
            OAK_LEAVES = new BlockColoredCube("leaves_oak");
            SPRUCE_LEAVES = new BlockColoredCube("leaves_spruce");

            ACACIA_LOG = new Column3Rot(new ModelLocation("block/acacia_log"));
            BIRCH_LOG = new Column3Rot(new ModelLocation("block/birch_log"));
            DARK_OAK_LOG = new Column3Rot(new ModelLocation("block/dark_oak_log"));
            JUNGLE_LOG = new Column3Rot(new ModelLocation("block/jungle_log"));
            OAK_LOG = new Column3Rot(new ModelLocation("block/oak_log"));
            SPRUCE_LOG = new Column3Rot(new ModelLocation("block/spruce_log"));

            ACACIA_WOOD = new BlockStaticCube("log_acacia");
            BIRCH_WOOD = new BlockStaticCube("log_birch");
            DARK_OAK_WOOD = new BlockStaticCube("log_big_oak");
            JUNGLE_WOOD = new BlockStaticCube("log_jungle");
            OAK_WOOD = new BlockStaticCube("log_oak");
            SPRUCE_WOOD = new BlockStaticCube("log_spruce");

            ModelLoader.registerModels(VanillaModels::loadModels,
                workbench);
        }

        if (AngelicaConfig.enableTestBlocks)
            LECTERN = new Model4Rot(new ModelLocation("block/lectern"));

        init = true;
    }

    public static void loadModels() {
        WORKBENCH = ModelLoader.getModel(workbench);
    }
}
