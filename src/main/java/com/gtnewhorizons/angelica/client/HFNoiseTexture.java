package com.gtnewhorizons.angelica.client;

import java.nio.ByteBuffer;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

/**
 *
 * @author Nathanael Lane
 */
public class HFNoiseTexture {
    public int texID;
    public int textureUnit;

    public HFNoiseTexture(int width, int height){
        texID = GL11.glGenTextures();
        textureUnit = 15;

        byte[] image = genHFNoiseImage(width, height);
        ByteBuffer data = BufferUtils.createByteBuffer(image.length);
        data.put(image);
        data.flip();

        //GL13.glActiveTexture(GL13.GL_TEXTURE0 + this.textureUnit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texID);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGB, width, height, 0,
                GL11.GL_RGB, GL11.GL_UNSIGNED_BYTE, data);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        //GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
    }

    public int getID(){
        return texID;
    }

    public void destroy(){
        GL11.glDeleteTextures(texID);
        texID = 0;
    }


    //from George Marsaglia's paper on XORshift PRNGs
    private int random(int seed){
        seed ^= (seed << 13);
        seed ^= (seed >> 17);
        seed ^= (seed << 5);
        return seed;
    }

    private byte random(int x, int y, int z){
        int seed = (random(x) + random(y * 19)) * random(z * 23) - z;
        return (byte) (random(seed) % 128);
    }

    /*
     * Just a random value for each pixel to get maximum frequency
     */
    private byte[] genHFNoiseImage(int width, int height){

        byte[] image = new byte[width * height * 3];
        int index = 0;

        for(int y = 0; y < height; y++){
            for(int x = 0; x < width; x++){
                for(int z = 1; z < 4; z++){
                        image[index++] = random(x, y, z);
                }
            }
        }

        return image;
    }

}
