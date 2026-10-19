package com.gtnewhorizons.angelica.debug.flyby;

import net.minecraft.block.Block;
import net.minecraft.command.CommandBase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagShort;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityDispenser;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.Constants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class FlybyScene {

    private static final Logger LOGGER = LogManager.getLogger("Angelica/Flyby");
    private static final String[] NO_COMMANDS = new String[0];
    private static final Pattern COORD_TOKEN = Pattern.compile("\\$([xyz])([+-]\\d+)?");
    private static final Pattern DEFINE_TOKEN = Pattern.compile("\\$\\{(\\w+)}");

    static final String MARKER = "flyby";

    static String[] load(String path) {
        final List<String> lines;
        try {
            lines = Files.readAllLines(Paths.get(path));
        } catch (IOException e) {
            LOGGER.error("Could not read flyby scene '{}'", path, e);
            return NO_COMMANDS;
        }

        final String[] commands = new String[lines.size()];
        final Map<String, String> defines = new HashMap<>();
        int n = 0;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty() || line.charAt(0) == '#') continue;
            if (line.charAt(0) == '/') line = line.substring(1).trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("define ")) {
                final String[] parts = line.split("\\s+", 3);
                if (parts.length == 3) {
                    defines.put(parts[1], parts[2]);
                } else {
                    LOGGER.warn("Flyby scene '{}': malformed define '{}'", path, line);
                }
                continue;
            }
            commands[n++] = substitute(line, defines);
        }
        if (n == commands.length) return commands;
        final String[] trimmed = new String[n];
        System.arraycopy(commands, 0, trimmed, 0, n);
        return trimmed;
    }

    private static String substitute(String line, Map<String, String> defines) {
        if (line.indexOf("${") < 0) return line;
        final Matcher matcher = DEFINE_TOKEN.matcher(line);
        final StringBuilder sb = new StringBuilder(line.length() + 256);
        while (matcher.find()) {
            final String value = defines.get(matcher.group(1));
            if (value == null) LOGGER.warn("Flyby scene: undefined '{}'", matcher.group());
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value != null ? value : matcher.group()));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    static String expand(String line, int x, int y, int z) {
        if (line.indexOf('$') < 0) return line;
        final Matcher matcher = COORD_TOKEN.matcher(line);
        final StringBuilder sb = new StringBuilder(line.length() + 16);
        while (matcher.find()) {
            final int base = switch (matcher.group(1).charAt(0)) {
                case 'x' -> x;
                case 'y' -> y;
                default -> z;
            };
            final String offset = matcher.group(2);
            matcher.appendReplacement(sb, Integer.toString(offset == null ? base : base + Integer.parseInt(offset)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    static Deferred deferred(String line) {
        final String[] parts = line.split("\\s+", 3);
        if (parts.length < 3) return null;
        try {
            final int ticks = Integer.parseInt(parts[1]);
            return ticks > 0 ? new Deferred(ticks, parts[2]) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static boolean run(MinecraftServer server, FlybyCommandSender sender, String line) {
        try {
            if (line.startsWith("tile ")) return placeTile(sender, line);
            if (line.startsWith("activate ")) return activate(sender, line);
            if (line.startsWith("fill ")) return fill(sender, line);
        } catch (NBTException | RuntimeException e) {
            LOGGER.warn("Flyby scene: '{}' failed", line, e);
            return false;
        }
        return server.getCommandManager().executeCommand(sender, line) != 0;
    }

    private static boolean placeTile(FlybyCommandSender sender, String line) throws NBTException {
        final int brace = line.indexOf('{');
        if (brace < 0) return false;
        final String[] args = line.substring(0, brace).trim().split("\\s+");
        if (args.length < 5 || args.length > 6 || !Block.blockRegistry.containsKey(args[4])) return false;

        final ChunkCoordinates pos = position(sender, args, 1);
        final int x = pos.posX, y = pos.posY, z = pos.posZ;
        final Block block = Block.getBlockFromName(args[4]);
        final int meta = args.length == 6 ? Integer.parseInt(args[5]) : 0;
        final World world = sender.getEntityWorld();
        if (!world.blockExists(x, y, z) || !block.hasTileEntity(meta)) return false;
        if (!(JsonToNBT.func_150315_a(line.substring(brace)) instanceof NBTTagCompound parsed)) return false;

        final NBTTagCompound tag = decode(parsed);
        tag.setInteger("x", x);
        tag.setInteger("y", y);
        tag.setInteger("z", z);

        final TileEntity tile = block.createTileEntity(world, meta);
        if (tile == null) return false;
        tile.readFromNBT(tag);
        world.setBlock(x, y, z, block, meta, 3);
        world.setTileEntity(x, y, z, tile);
        return world.getTileEntity(x, y, z) == tile;
    }

    private static boolean activate(FlybyCommandSender sender, String line) {
        final String[] args = line.split("\\s+");
        final boolean sneak = args.length == 5 && "sneak".equals(args[4]);
        if (args.length < 4 || args.length > 5 || (args.length == 5 && !sneak)) return false;

        final ChunkCoordinates pos = position(sender, args, 1);
        final int x = pos.posX, y = pos.posY, z = pos.posZ;
        final EntityPlayerMP player = sender.player();
        final World world = player.worldObj;
        if (!world.blockExists(x, y, z)) return false;

        final double playerX = player.posX;
        final double playerY = player.posY;
        final double playerZ = player.posZ;
        final boolean sneaking = player.isSneaking();
        final ItemStack[] hotbar = player.inventory.mainInventory;
        final int slot = player.inventory.currentItem;
        final ItemStack held = hotbar[slot];
        player.posX = x + 0.5D;
        player.posY = y + 1.0D;
        player.posZ = z + 0.5D;
        player.setSneaking(sneak);
        hotbar[slot] = null;
        try {
            return world.getBlock(x, y, z).onBlockActivated(world, x, y, z, player, 1, 0.5F, 1.0F, 0.5F);
        } finally {
            player.posX = playerX;
            player.posY = playerY;
            player.posZ = playerZ;
            player.setSneaking(sneaking);
            hotbar[slot] = held;
        }
    }

    private static boolean fill(FlybyCommandSender sender, String line) {
        final String[] args = line.split("\\s+");
        if (args.length < 8 || args.length > 9 || !Block.blockRegistry.containsKey(args[7])) return false;

        final ChunkCoordinates from = position(sender, args, 1);
        final ChunkCoordinates to = position(sender, args, 4);
        final Block block = Block.getBlockFromName(args[7]);
        final int meta = args.length == 9 ? Integer.parseInt(args[8]) : 0;
        final World world = sender.getEntityWorld();
        for (int x = Math.min(from.posX, to.posX); x <= Math.max(from.posX, to.posX); x++) {
            for (int y = Math.min(from.posY, to.posY); y <= Math.max(from.posY, to.posY); y++) {
                for (int z = Math.min(from.posZ, to.posZ); z <= Math.max(from.posZ, to.posZ); z++) {
                    if (!world.blockExists(x, y, z)) return false;
                    world.setBlock(x, y, z, block, meta, 3);
                }
            }
        }
        return true;
    }

    private static ChunkCoordinates position(FlybyCommandSender sender, String[] args, int first) {
        return new ChunkCoordinates(coordinate(sender, 'x', args[first]), coordinate(sender, 'y', args[first + 1]), coordinate(sender, 'z', args[first + 2]));
    }

    private static int coordinate(FlybyCommandSender sender, char axis, String token) {
        final ChunkCoordinates anchor = sender.getPlayerCoordinates();
        final int base = switch (axis) {
            case 'x' -> anchor.posX;
            case 'y' -> anchor.posY;
            default -> anchor.posZ;
        };
        return MathHelper.floor_double(CommandBase.func_110666_a(sender, base, token));
    }

    private static NBTTagCompound decode(NBTTagCompound source) {
        final NBTTagCompound decoded = new NBTTagCompound();
        final boolean itemStack = source.hasKey("Count");
        for (String key : source.func_150296_c()) {
            String name = key.replace(';', ':');
            NBTBase value = source.getTag(key);
            if (value instanceof NBTTagCompound compound) {
                value = decode(compound);
            } else if (value instanceof NBTTagList list && list.func_150303_d() == Constants.NBT.TAG_COMPOUND) {
                final NBTTagList entries = new NBTTagList();
                for (int i = 0; i < list.tagCount(); i++) {
                    entries.appendTag(decode(list.getCompoundTagAt(i)));
                }
                value = entries;
            } else if (value instanceof NBTTagString string) {
                final String text = string.func_150285_a_();
                if (name.endsWith("#")) {
                    name = name.substring(0, name.length() - 1);
                    value = new NBTTagByteArray(text.getBytes(StandardCharsets.UTF_8));
                } else if (itemStack && "id".equals(name)) {
                    if (!Item.itemRegistry.containsKey(text)) throw new IllegalArgumentException("Unknown item " + text);
                    value = new NBTTagShort((short) Item.getIdFromItem((Item) Item.itemRegistry.getObject(text)));
                }
            }
            decoded.setTag(name, value);
        }
        return decoded;
    }

    static int count(WorldServer world) {
        final List<Entity> entities = world.loadedEntityList;
        int total = 0;
        for (int i = 0; i < entities.size(); i++) {
            final Entity entity = entities.get(i);
            if (entity != null && !entity.isDead && entity.getEntityData().getBoolean(MARKER)) total++;
        }

        final List<TileEntity> tileEntities = world.loadedTileEntityList;
        for (int i = 0; i < tileEntities.size(); i++) {
            if (marked(tileEntities.get(i))) total++;
        }
        return total;
    }

    static int clear(WorldServer world) {
        final List<Entity> entities = world.loadedEntityList;
        int cleared = 0;
        for (int i = 0; i < entities.size(); i++) {
            final Entity entity = entities.get(i);
            if (entity != null && entity.getEntityData().getBoolean(MARKER)) {
                entity.setDead();
                cleared++;
            }
        }

        final List<TileEntity> tileEntities = world.loadedTileEntityList;
        final List<TileEntity> markedBlocks = new ArrayList<>();
        for (int i = 0; i < tileEntities.size(); i++) {
            final TileEntity tileEntity = tileEntities.get(i);
            if (marked(tileEntity)) markedBlocks.add(tileEntity);
        }
        for (int i = 0; i < markedBlocks.size(); i++) {
            final TileEntity tileEntity = markedBlocks.get(i);
            world.setBlockToAir(tileEntity.xCoord, tileEntity.yCoord, tileEntity.zCoord);
        }
        return cleared + markedBlocks.size();
    }

    private static boolean marked(TileEntity tileEntity) {
        return tileEntity instanceof TileEntityDispenser dispenser && !dispenser.isInvalid() && dispenser.getClass() == TileEntityDispenser.class && MARKER.equals(dispenser.getInventoryName());
    }

    record Deferred(int ticks, String line) {}

    private FlybyScene() {}
}
