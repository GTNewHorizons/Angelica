package me.jellysquid.mods.sodium.client.model.quad;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraftforge.common.util.ForgeDirection;

import static me.jellysquid.mods.sodium.client.util.ModelQuadUtil.COLOR_INDEX;
import static me.jellysquid.mods.sodium.client.util.ModelQuadUtil.LIGHT_INDEX;
import static me.jellysquid.mods.sodium.client.util.ModelQuadUtil.NORMAL_INDEX;
import static me.jellysquid.mods.sodium.client.util.ModelQuadUtil.POSITION_INDEX;
import static me.jellysquid.mods.sodium.client.util.ModelQuadUtil.TEXTURE_INDEX;
import static me.jellysquid.mods.sodium.client.util.ModelQuadUtil.VERTEX_SIZE;
import static me.jellysquid.mods.sodium.client.util.ModelQuadUtil.vertexOffset;

/**
 * A simple implementation of the {@link ModelQuadViewMutable} interface which can provide an on-heap scratch area
 * for storing quad vertex data.
 */
public class ModelQuad implements ModelQuadViewMutable {
    private final int[] data = new int[VERTEX_SIZE * 4];
    private int flags;

    private TextureAtlasSprite sprite;
    private int colorIdx;

    @Override
    public void setX(int idx, float x) {
        this.data[vertexOffset(idx) + POSITION_INDEX] = Float.floatToRawIntBits(x);
    }

    @Override
    public void setY(int idx, float y) {
        this.data[vertexOffset(idx) + POSITION_INDEX + 1] = Float.floatToRawIntBits(y);
    }

    @Override
    public void setZ(int idx, float z) {
        this.data[vertexOffset(idx) + POSITION_INDEX + 2] = Float.floatToRawIntBits(z);
    }

    @Override
    public void setColor(int idx, int color) {
        this.data[vertexOffset(idx) + COLOR_INDEX] = color;
    }

    @Override
    public void setTexU(int idx, float u) {
        this.data[vertexOffset(idx) + TEXTURE_INDEX] = Float.floatToRawIntBits(u);
    }

    @Override
    public void setTexV(int idx, float v) {
        this.data[vertexOffset(idx) + TEXTURE_INDEX + 1] = Float.floatToRawIntBits(v);
    }

    @Override
    public void setLight(int idx, int light) {
        this.data[vertexOffset(idx) + LIGHT_INDEX] = light;
    }

    @Override
    public void setNormal(int idx, int norm) {
        this.data[vertexOffset(idx) + NORMAL_INDEX] = norm;
    }

    @Override
    public void setFlags(int flags) {
        this.flags = flags;
    }

    @Override
    public void setSprite(TextureAtlasSprite sprite) {
        this.sprite = sprite;
    }

    @Override
    public void setColorIndex(int index) {
        this.colorIdx = index;
    }

    /**
     * Doesn't do anything useful, but I'm not sure if it has to.
     */
    @Override
    public void setCullFace(ForgeDirection f) {}

    @Override
    public int getLight(int idx) {
        return this.data[vertexOffset(idx) + LIGHT_INDEX];
    }

    @Override
    public int getNormal(int idx) {
        return this.data[vertexOffset(idx) + NORMAL_INDEX];
    }

    @Override
    public int getColorIndex() {
        return this.colorIdx;
    }

    /**
     * Doesn't do anything useful, but I'm not sure if it has to.
     */
    @Override
    public ForgeDirection getLightFace() {
        return ForgeDirection.UP;
    }

    /**
     * Doesn't do anything useful, but I'm not sure if it has to.
     */
    @Override
    public ForgeDirection getCullFace() {
        return ForgeDirection.UNKNOWN;
    }

    @Override
    public float getX(int idx) {
        return Float.intBitsToFloat(this.data[vertexOffset(idx) + POSITION_INDEX]);
    }

    @Override
    public float getY(int idx) {
        return Float.intBitsToFloat(this.data[vertexOffset(idx) + POSITION_INDEX + 1]);
    }

    @Override
    public float getZ(int idx) {
        return Float.intBitsToFloat(this.data[vertexOffset(idx) + POSITION_INDEX + 2]);
    }

    @Override
    public int getColor(int idx) {
    	if(vertexOffset(idx) + COLOR_INDEX < data.length) {
            return this.data[vertexOffset(idx) + COLOR_INDEX];
        }
        else {
            return data.length;
        }
    }

    @Override
    public float getTexU(int idx) {
        return Float.intBitsToFloat(this.data[vertexOffset(idx) + TEXTURE_INDEX]);
    }

    @Override
    public float getTexV(int idx) {
        return Float.intBitsToFloat(this.data[vertexOffset(idx) + TEXTURE_INDEX + 1]);
    }

    @Override
    public int getFlags() {
        return this.flags;
    }

    @Override
    public TextureAtlasSprite rubidium$getSprite() {
        return this.sprite;
    }

}
