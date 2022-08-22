package shadersmodcore.client;

import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GLAllocation;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.ITextureObject;
import net.minecraft.client.renderer.texture.LayeredTexture;
import net.minecraft.client.renderer.texture.Stitcher;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.ARBTextureStorage;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL30.*;

public class ShadersTex {
	public static final int initialBufferSize = 1048576;
	public static ByteBuffer byteBuffer = BufferUtils.createByteBuffer(initialBufferSize*4);
	public static IntBuffer intBuffer = byteBuffer.asIntBuffer();
	public static int[] intArray = new int[initialBufferSize];
	public static final int defBaseTexColor = 0x00000000;
	public static final int defNormTexColor = 0xFF7F7FFF;
	public static final int defSpecTexColor = 0x00000000;
	public static Map<Integer,MultiTexID> multiTexMap = new HashMap();
	public static TextureMap updatingTextureMap = null;
	public static TextureAtlasSprite updatingSprite = null;
	public static MultiTexID updatingTex = null;
	public static MultiTexID boundTex = null;
	public static int updatingPage = 0;
	public static String iconName = null;

	public static IntBuffer getIntBuffer(int size)
	{
		if (intBuffer.capacity() < size)
		{
			int bufferSize = roundUpPOT(size);
			byteBuffer = BufferUtils.createByteBuffer(bufferSize*4);
			intBuffer = byteBuffer.asIntBuffer();
		}
		return intBuffer;
	}
	
	public static int[] getIntArray(int size)
	{
		if (intArray.length < size)
		{
			intArray = null;
			intArray = new int[roundUpPOT(size)];
		}
		return intArray;
	}
	
	public static int roundUpPOT(int x)
	{
		int i = x-1;
		i |= (i>>1);
		i |= (i>>2);
		i |= (i>>4);
		i |= (i>>8);
		i |= (i>>16);
		return i+1;
	}
	
	public static IntBuffer fillIntBuffer(int size, int value)
	{
		int[] aint = getIntArray(size);
		IntBuffer intBuf = getIntBuffer(size);
		Arrays.fill(intArray, 0, size, value);
		intBuffer.put(intArray, 0, size);
		return intBuffer;
	}
	
	public static int[] createAIntImage(int size)
	{
		int[] aint = new int[size*3];
		Arrays.fill(aint, 0,      size,   defBaseTexColor);
		Arrays.fill(aint, size,   size*2, defNormTexColor);
		Arrays.fill(aint, size*2, size*3, defSpecTexColor);
		return aint;
	}
	
	public static int[] createAIntImage(int size, int color)
	{
		int[] aint = new int[size*3];
		Arrays.fill(aint, 0,      size,   color);
		Arrays.fill(aint, size,   size*2, defNormTexColor);
		Arrays.fill(aint, size*2, size*3, defSpecTexColor);
		return aint;
	}
	
	public static MultiTexID getMultiTexID(AbstractTexture tex)
	{
		MultiTexID multiTex = tex.multiTex;
		if (multiTex==null)
		{
			int baseTex = tex.getGlTextureId();
			multiTex = multiTexMap.get(Integer.valueOf(baseTex));
			if (multiTex == null)
			{
				multiTex = new MultiTexID(baseTex, GL11.glGenTextures(), GL11.glGenTextures());
				multiTexMap.put(baseTex, multiTex);
			}
			tex.multiTex = multiTex;
		}
		return multiTex;
	}

	public static void deleteTextures(AbstractTexture atex)
	{
		int texid = atex.glTextureId;
		if (texid != -1)
		{
			GL11.glDeleteTextures(texid);
			atex.glTextureId = -1;
		}
		MultiTexID multiTex = atex.multiTex;
		if (multiTex != null)
		{
			atex.multiTex = null;
			multiTexMap.remove(Integer.valueOf(multiTex.base));
			GL11.glDeleteTextures(multiTex.norm);
			GL11.glDeleteTextures(multiTex.spec);
			if (multiTex.base != texid)
			{
				System.err.println("Error : MultiTexID.base mismatch.");
				GL11.glDeleteTextures(multiTex.base);
			}
		}
	}
	
	/** Remove MultiTexID object reference and delete textures */
	public static int deleteMultiTex(ITextureObject tex)
	{
		if (tex instanceof AbstractTexture) {
			deleteTextures((AbstractTexture)tex);
		} else {
			GL11.glDeleteTextures(tex.getGlTextureId());
		}
		return 0;
	}
	
	public static void bindNSTextures(int normTex, int specTex)
	{
		//Shaders.checkGLError("pre bindNSTextures");
		if (Shaders.isRenderingWorld && Shaders.activeTexUnit == GL13.GL_TEXTURE0)
		{
			GL13.glActiveTexture(GL13.GL_TEXTURE2);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, normTex);
			GL13.glActiveTexture(GL13.GL_TEXTURE3);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, specTex);
			GL13.glActiveTexture(GL13.GL_TEXTURE0);
		}
		//Shaders.checkGLError("bindNSTextures");
	}
	
	public static void bindNSTextures(MultiTexID multiTex)
	{
		bindNSTextures(multiTex.norm, multiTex.spec);
	}
	
	public static void bindTextures(int baseTex, int normTex, int specTex)
	{
		//Shaders.checkGLError("pre bindTextures");
		if (Shaders.isRenderingWorld && Shaders.activeTexUnit == GL13.GL_TEXTURE0)
		{
			GL13.glActiveTexture(GL13.GL_TEXTURE2);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, normTex);
			GL13.glActiveTexture(GL13.GL_TEXTURE3);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, specTex);
			GL13.glActiveTexture(GL13.GL_TEXTURE0);
		}
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, baseTex);
		//Shaders.checkGLError("bindTextures");
	}

	public static void bindTextures(MultiTexID multiTex)
	{
		boundTex = multiTex;
		if (Shaders.isRenderingWorld && Shaders.activeTexUnit == GL13.GL_TEXTURE0)
		{
			if (Shaders.configNormalMap) {
				GL13.glActiveTexture(GL13.GL_TEXTURE2);
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, multiTex.norm);
			}
			if (Shaders.configSpecularMap) {
				GL13.glActiveTexture(GL13.GL_TEXTURE3);
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, multiTex.spec);
			}
			GL13.glActiveTexture(GL13.GL_TEXTURE0);
		}
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, multiTex.base);
	}
	
	public static void bindTexture(ITextureObject tex)
	{
		if (tex instanceof TextureMap) {
			Shaders.atlasSizeX = ((TextureMap)tex).atlasWidth;
			Shaders.atlasSizeY = ((TextureMap)tex).atlasHeight;
		} else {
			Shaders.atlasSizeX = 0;
			Shaders.atlasSizeY = 0;
		}
		bindTextures(tex.getMultiTexID());
	}
	
	/** not used */
	public static void bindTextures(int baseTex)
	{
		MultiTexID multiTex = multiTexMap.get(Integer.valueOf(baseTex));
		bindTextures(multiTex);
	}

	public static void allocTexStorage(int width, int height, int maxLevel)
	{
		Shaders.checkGLError("pre allocTexStorage");
		int level;
		for (level=0; (width>>level)>0 && (height>>level)>0 /*&& level<=maxLevel*/; ++level) {
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, level, GL11.GL_RGBA, (width>>level), (height>>level), 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, (IntBuffer)null);
		}
		GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, level-1);
		Shaders.checkGLError("allocTexStorage");
		//clear unused level otherwise glTexSubImage2D will crash on AMD when reallocation texture of different size.
		//for ( ; level < 16 ; ++level) {
		//	GL11.glTexImage2D(GL11.GL_TEXTURE_2D, level, GL11.GL_RGBA, 0, 0, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, (IntBuffer)null);
		//}
		GL11.glGetError(); // It usually returns error 0x0501 Invalid value for width 0 height 0. Ignore it. 
	}
	
	// for Dynamic Texture
	public static void initDynamicTexture(int texID, int width, int height, DynamicTexture tex)
	{
		MultiTexID multiTex = tex.getMultiTexID();
		int[] aint = tex.getTextureData();
		int size = width * height;
		Arrays.fill(aint, size,   size*2, defNormTexColor);
		Arrays.fill(aint, size*2, size*3, defSpecTexColor);
		// base texture
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, multiTex.base);
		allocTexStorage(width,height,0);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);

		// norm texture
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, multiTex.norm);
		allocTexStorage(width,height,0);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);

		// spec texture
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, multiTex.spec);
		allocTexStorage(width,height,0);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
		
		// base texture
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, multiTex.base);
	}
	
	public static ITextureObject createDefaultTexture()
	{
		DynamicTexture tex = new DynamicTexture(1,1);
		tex.getTextureData()[0] = 0xffffffff;
		tex.updateDynamicTexture();
		return tex;
	}

	// for TextureMap
	public static void allocateTextureMap(int texID, int mipmapLevels, int width, int height, float anisotropy, Stitcher stitcher, TextureMap tex)
	{
		System.out.println("allocateTextureMap "+tex.getTextureType()+" "+mipmapLevels+" "+width+" "+height+" "+anisotropy+" ");
		updatingTextureMap = tex;
		tex.atlasWidth  = width;
		tex.atlasHeight = height;
		MultiTexID multiTex = getMultiTexID(tex);
		updatingTex = multiTex;
		TextureUtil.allocateTextureImpl(multiTex.base, mipmapLevels, width, height, anisotropy);
		if (Shaders.configNormalMap  )
			TextureUtil.allocateTextureImpl(multiTex.norm, mipmapLevels, width, height, anisotropy);
		if (Shaders.configSpecularMap)
			TextureUtil.allocateTextureImpl(multiTex.spec, mipmapLevels, width, height, anisotropy);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, texID);
	}
	
	public static TextureAtlasSprite setSprite(TextureAtlasSprite tas)
	{
		return updatingSprite = tas;
	}
	
	public static String setIconName(String name)
	{
		return iconName = name;
	}

	public static void uploadTexSubForLoadAtlas(int[][] data, int width, int height, int xoffset, int yoffset, boolean linear, boolean clamp)
	{
		TextureUtil.uploadTextureMipmap(data, width, height, xoffset, yoffset, linear, clamp);
		boolean border = updatingSprite.useAnisotropicFiltering;
		int[][] aaint;
		//
		if (Shaders.configNormalMap  ) {
			aaint = readImageAndMipmaps(iconName+"_n", width, height, data.length, border, defNormTexColor);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, updatingTex.norm);
			TextureUtil.uploadTextureMipmap(aaint, width, height, xoffset, yoffset, linear, clamp);
		}
		//
		if (Shaders.configSpecularMap) {
			aaint = readImageAndMipmaps(iconName+"_s", width, height, data.length, border, defSpecTexColor);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, updatingTex.spec);
			TextureUtil.uploadTextureMipmap(aaint, width, height, xoffset, yoffset, linear, clamp);
		}
		//
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, updatingTex.base);
	}

	public static int[][] readImageAndMipmaps(String name, int width, int height, int numLevels, boolean border, int defColor)
	{
		int[][] aaint = new int[numLevels][];
		int[] aint;
		aaint[0] = aint = new int[width*height];
		boolean goodImage = false;
		BufferedImage image = readImage(updatingTextureMap.completeResourceLocation(new ResourceLocation(name), 0));
		if (image != null) {
			int imageWidth = image.getWidth();
			int imageHeight = image.getHeight();
			if (imageWidth + (border? 16: 0) == width)
			{
				goodImage = true;
				image.getRGB(0, 0, imageWidth, imageWidth, aint, 0, imageWidth);
				if (border)
					TextureUtil.prepareAnisotropicData(aint, imageWidth, imageWidth, 8);
			}
		}
		if (!goodImage) {
			Arrays.fill(aint, defColor);
		}
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, updatingTex.spec);
		aaint = genMipmapsSimple(aaint.length-1, width, aaint);
		return aaint;
	}

	public static BufferedImage readImage(ResourceLocation resLoc)
	{
		BufferedImage image = null;
		InputStream istr = null;
		try {
			istr = resManager.getResource(resLoc).getInputStream();
			image = ImageIO.read(istr);
		} catch (IOException e) {    		
		}
		if (istr != null) {
			try {
				istr.close();
			} catch (IOException e) {
			}
			istr = null;
		}
		return image;
	}

	public static int[][] genMipmapsSimple(int maxLevel, int width, int[][]data)
	{
		int level;
		for (level=1; level<=maxLevel; ++level)
		{
			if (data[level]==null)
			{
				int cw = width>>level;
				int pw = cw*2;
				int[] aintp = data[level-1]; 
				int[] aintc = data[level] = new int[cw*cw];
				int x,y;
				for (y=0; y<cw; ++y) {
					for (x=0; x<cw; ++x) {
						int ppos = y*2*pw+x*2;
						aintc[y*cw+x] = blend4Simple(aintp[ppos],aintp[ppos+1],aintp[ppos+pw],aintp[ppos+pw+1]);
					}
				}
			}
		}
		return data;
	}
    
    public static void uploadTexSub(int[][] data, int width, int height, int xoffset, int yoffset, boolean linear, boolean clamp)
    {
    	TextureUtil.uploadTextureMipmap(data, width, height, xoffset, yoffset, linear, clamp);
    }
	
	public static int blend4Alpha(int c0, int c1, int c2, int c3)
	{
		int a0 = (c0 >>> 24) & 255;
		int a1 = (c1 >>> 24) & 255;
		int a2 = (c2 >>> 24) & 255;
		int a3 = (c3 >>> 24) & 255;
		int as = a0+a1+a2+a3;
		int an = (as+2)/4;
		//int an = Math.min(Math.min(Math.min(a0,a1),a2),a3);
		int dv;
		if (as != 0) {
			dv = as;
		} else {
			dv=4; a3=a2=a1=a0=1; 
		}
		int frac = (dv+1)/2;
		//return  (((Math.min(Math.min(Math.min(a0,a1),a2),a3)/ 4) << 24) | 
		int color = (an << 24) | 
				(((((c0>>>16)&255)*a0 + ((c1>>>16)&255)*a1 + ((c2>>>16)&255)*a2 + ((c3>>>16)&255)*a3 + frac)/dv) << 16) | 
				(((((c0>>> 8)&255)*a0 + ((c1>>> 8)&255)*a1 + ((c2>>> 8)&255)*a2 + ((c3>>> 8)&255)*a3 + frac)/dv) <<  8) | 
				(((((c0>>> 0)&255)*a0 + ((c1>>> 0)&255)*a1 + ((c2>>> 0)&255)*a2 + ((c3>>> 0)&255)*a3 + frac)/dv) <<  0) ;
		return color;
	}

	public static int blend4Simple(int c0, int c1, int c2, int c3)
	{
		int color =
				(((((c0>>>24)&255) + ((c1>>>24)&255) + ((c2>>>24)&255) + ((c3>>>24)&255) + 2)/4) << 24) | 
				(((((c0>>>16)&255) + ((c1>>>16)&255) + ((c2>>>16)&255) + ((c3>>>16)&255) + 2)/4) << 16) | 
				(((((c0>>> 8)&255) + ((c1>>> 8)&255) + ((c2>>> 8)&255) + ((c3>>> 8)&255) + 2)/4) <<  8) | 
				(((((c0>>> 0)&255) + ((c1>>> 0)&255) + ((c2>>> 0)&255) + ((c3>>> 0)&255) + 2)/4) <<  0) ;
		return color;
	}


	public static void genMipmapAlpha(int[] aint, int offset, int width, int height)
	{
		int minwh = Math.min(width, height);
		int level;
		int w1, w2, h1, h2, o1, o2;
		w1 = w2 = width;
		h1 = h2 = height;
		o1 = o2 = offset;
		// generate mipmap from big to small
		o2 = offset; w2 = width; h2 = height;
		o1 = 0; w1 = 0; h1 = 0;
		for (level=0; w2>1 && h2>1; ++level,w2=w1,h2=h1,o2=o1) {
			o1 = o2 + w2*h2;
			w1 = w2/2; h1 = h2/2;
			for (int y=0; y<h1; ++y)
			{
				int p1 = o1+y*w1;
				int p2 = o2+y*2*w2;
				for (int x=0; x<w1; ++x)
				{
					aint[p1+x] = blend4Alpha(
							aint[p2   +(x*2  )],
							aint[p2   +(x*2+1)],
							aint[p2+w2+(x*2  )],
							aint[p2+w2+(x*2+1)]);
				}
			}
		}
		// fix black pixels from small to big
		while (level>0)
		{
			--level;
			w2 = width>>level;
			h2 = height>>level;
			o2 = o1 - w2*h2;
			int p2 = o2;
			for (int y=0; y<h2; ++y)
			{
				for (int x=0; x<w2; ++x)
				{
					//p2 = o2 + y*w2 + x;
					if (aint[p2] == 0) {
						aint[p2] = aint[o1 +(y/2)*w1 +(x/2)] & 0x00ffffff;
					}						
					++p2;
				}
			}
			o1 = o2;
			w1 = w2;
			h1 = h2;
		}
	}

	public static void genMipmapSimple(int[] aint, int offset, int width, int height)
	{
		int minwh = Math.min(width, height);
		int level;
		int w1, w2, h1, h2, o1, o2;
		w1 = w2 = width;
		h1 = h2 = height;
		o1 = o2 = offset;
		// generate mipmap from big to small
		o2 = offset; w2 = width; h2 = height;
		o1 = 0; w1 = 0; h1 = 0;
		for (level=0; w2>1 && h2>1; ++level,w2=w1,h2=h1,o2=o1) {
			o1 = o2 + w2*h2;
			w1 = w2/2; h1 = h2/2;
			for (int y=0; y<h1; ++y)
			{
				int p1 = o1+y*w1;
				int p2 = o2+y*2*w2;
				for (int x=0; x<w1; ++x)
				{
					aint[p1+x] = blend4Simple(
							aint[p2   +(x*2  )],
							aint[p2   +(x*2+1)],
							aint[p2+w2+(x*2  )],
							aint[p2+w2+(x*2+1)]);
				}
			}
		}
		// fix black pixels from small to big
		while (level>0)
		{
			--level;
			w2 = width>>level;
			h2 = height>>level;
			o2 = o1 - w2*h2;
			int p2 = o2;
			for (int y=0; y<h2; ++y)
			{
				for (int x=0; x<w2; ++x)
				{
					//p2 = o2 + y*w2 + x;
					if (aint[p2] == 0) {
						aint[p2] = aint[o1 +(y/2)*w1 +(x/2)] & 0x00ffffff;
					}						
					++p2;
				}
			}
			o1 = o2;
			w1 = w2;
			h1 = h2;
		}
	}

	
	public static boolean isSemiTransparent(int[] aint, int width, int height)
	{
		int size = width*height;
		// grass side texture ?;
		if (aint[0] >>> 24 == 255 && aint[size-1] == 0)
			return true;
		for (int i=0; i<size; ++i)
		{
			int alpha = aint[i] >>> 24;
			if (alpha!=0 && alpha!=255)
				return true;
		}
		return false;
	}
	
	public static void updateSubImage1(int[] src, int width, int height, int posX, int posY, int page, int color)
	{
		int size = width * height;
		IntBuffer intBuf = getIntBuffer(size);
		int[] aint = getIntArray((size*4+2)/3);
		if (src.length >= size*(page+1))
		{
			System.arraycopy(src, size*page, aint, 0, size);
		}
		else
		{
			Arrays.fill(aint, color);
		}
		//
//		if (page == 0)
			genMipmapAlpha(aint,0,width,height);
//		else
//			genMipmapSimple(aint,0,width,height);
		//
		for (int level=0, offset=0, lw=width, lh=height, px=posX, py=posY; 
				lw>0 && lh>0; 
				++level)
		{
			int lsize = lw*lh;
			intBuf.clear();
			intBuf.put(aint, offset, lsize).position(0).limit(lsize);
			GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, level, px, py, lw, lh, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, intBuf);
			offset += lsize; lw/=2; lh/=2; px/=2; py/=2;
		}
		intBuf.clear();
	}
	
	public static void updateSubTex1(int[] src, int width, int height, int posX, int posY)
	{
		int level;
		int cw;
		int ch;
		int cx;
		int cy;
		for (level = 0, cw=width, ch=height, cx=posX, cy=posY;
				cw>0 && ch>0;
				++level, cw/=2, ch/=2, cx/=2, cy/=2)
		{
			GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, level, cx, cy, 0, 0, cw, ch);
		}
	}

	/* updateSubImage for single Texture with mipmap */
	public static void updateSubImage1(int[][] src, int width, int height, int posX, int posY, int page, int color)
	{
		int size = width * height;
		IntBuffer intBuf = getIntBuffer(size);
		int numLevel = src.length;
		for (int level=0, //offset=0, 
				lw=width, lh=height, px=posX, py=posY; 
				lw>0 && lh>0 && level<numLevel; 
				++level)
		{
			int lsize = lw*lh;
			intBuf.clear();
			intBuf.put(src[level], 0/*offset*/, lsize).position(0).limit(lsize);
			GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, level, px, py, lw, lh, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, intBuf);
			//offset += lsize;
			lw/=2; lh/=2; px/=2; py/=2;
		}
		intBuf.clear();
	}

    public static void setupTextureMipmap(TextureMap tex)
    {
    	/*
    	MultiTexID multiTex = tex.getMultiTexID();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, multiTex.norm);
		GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, multiTex.spec);
		GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, multiTex.base);
		GL30.glGenerateMipmap(GL11.GL_TEXTURE_2D);
		*/
    }
	
	public static void updateDynamicTexture(int texID, int[] src, int width, int height, DynamicTexture tex)
	{
		MultiTexID multiTex = tex.getMultiTexID();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, multiTex.norm);
		updateSubImage1(src, width, height, 0, 0, 1, defNormTexColor);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, multiTex.spec);
		updateSubImage1(src, width, height, 0, 0, 2, defSpecTexColor);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, multiTex.base);
		updateSubImage1(src, width, height, 0, 0, 0, defBaseTexColor);
	}
	
	public static void updateSubImage(int[] src, int width, int height, int posX, int posY, boolean linear, boolean clamp)
	{
		if (updatingTex != null)
		{
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, updatingTex.norm);
			updateSubImage1(src, width, height, posX, posY, 1, defNormTexColor);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, updatingTex.spec);
			updateSubImage1(src, width, height, posX, posY, 2, defSpecTexColor);
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, updatingTex.base);
		}
		updateSubImage1(src, width, height, posX, posY, 0, defBaseTexColor);
	}
	
	// not used
	public static void updateAnimationTextureMap(TextureMap tex, List tasList)
	{
		Iterator<TextureAtlasSprite> iterator; 
        MultiTexID multiTex = tex.getMultiTexID();
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, multiTex.norm);
		for (iterator = tasList.iterator(); iterator.hasNext(); )
        {
            TextureAtlasSprite tas = iterator.next();
            tas.updateAnimation();
        }
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, multiTex.norm);
		for (iterator = tasList.iterator(); iterator.hasNext(); )
        {
            TextureAtlasSprite tas = iterator.next();
            tas.updateAnimation();
        }
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, multiTex.norm);
		for (iterator = tasList.iterator(); iterator.hasNext(); )
        {
            TextureAtlasSprite tas = iterator.next();
            tas.updateAnimation();
        }
	}
    
	public static void setupTexture(MultiTexID multiTex, int[] src, int width, int height, boolean linear, boolean clamp)
	{
		int mmfilter = linear? GL11.GL_LINEAR: GL11.GL_NEAREST;
		int wraptype = clamp? GL11.GL_CLAMP: GL11.GL_REPEAT;
		int size = width * height;
		IntBuffer intBuf = getIntBuffer(size);
		//
		intBuf.clear();
		intBuf.put(src, 0, size).position(0).limit(size);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, multiTex.base);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, intBuf);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, mmfilter);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mmfilter);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wraptype);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wraptype);
		//
		intBuf.put(src, size, size).position(0).limit(size);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, multiTex.norm);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, intBuf);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, mmfilter);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mmfilter);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wraptype);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wraptype);
		//
		intBuf.put(src, size*2, size).position(0).limit(size);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, multiTex.spec);
		GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, width, height, 0, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, intBuf);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, mmfilter);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, mmfilter);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, wraptype);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, wraptype);
		//
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, multiTex.base);
	}

	/* currently not used */
	public static void updateSubImage(MultiTexID multiTex, int[] src, int width, int height, int posX, int posY, boolean linear, boolean clamp)
	{
		int size = width * height;
		IntBuffer intBuf = getIntBuffer(size);
		//
		intBuf.clear();
		intBuf.put(src, 0, size);
		intBuf.position(0).limit(size);
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, multiTex.base);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
		GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, posX, posY, width, height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, intBuf);
		if (src.length == size*3)
		{
			intBuf.clear();
			intBuf.put(src, size, size).position(0);
			intBuf.position(0).limit(size);
		}
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, multiTex.norm);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
		GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, posX, posY, width, height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, intBuf);
		if (src.length == size*3)
		{
			intBuf.clear();
			intBuf.put(src, size*2, size);
			intBuf.position(0).limit(size);
		}
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, multiTex.spec);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
		GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, posX, posY, width, height, GL12.GL_BGRA, GL12.GL_UNSIGNED_INT_8_8_8_8_REV, intBuf);
		GL13.glActiveTexture(GL13.GL_TEXTURE0);
	}

	public static ResourceLocation getNSMapLocation(ResourceLocation location, String mapName)
	{
		String basename = location.getResourcePath();
		String[] basenameParts = basename.split(".png");
		String basenameNoFileType = basenameParts[0];
		return new ResourceLocation(location.getResourceDomain(),basenameNoFileType+"_"+mapName+".png");
	}

    public static void loadNSMap(IResourceManager manager, ResourceLocation location, int width, int height, int[] aint)
    {
    	if (Shaders.configNormalMap)
    		ShadersTex.loadNSMap1(manager, getNSMapLocation(location,"n"), width, height, aint, width*height  , defNormTexColor);
    	if (Shaders.configSpecularMap)
    		ShadersTex.loadNSMap1(manager, getNSMapLocation(location,"s"), width, height, aint, width*height*2, defSpecTexColor);
    }
    
    public static void loadNSMap1(IResourceManager manager, ResourceLocation location, int width, int height, int[] aint, int offset, int defaultColor)
    {
    	boolean good = false;
    	try
    	{
    		IResource res = manager.getResource(location);
    		BufferedImage bufferedimage = ImageIO.read(res.getInputStream());
    		if (bufferedimage.getWidth()==width && bufferedimage.getHeight()==height)
    		{
    			bufferedimage.getRGB(0, 0, width, height, aint, offset, width);
    			good = true;
    		}
    	}
    	catch (IOException ex)
    	{
    	}
    	if (!good)
    	{
    		java.util.Arrays.fill(aint,offset,offset+width*height,defaultColor);
    	}
    }

    /** init and upload from BufferedImage */
    /* Replacement for TextureUtil.func_110989_a call in SimpleTexture.func_110551_a.
       Keep par0...par5 the same as func_110989_a for easy patching. 
       More parameters added for reading N-S-Map. */
	public static int loadSimpleTexture(int textureID,
			BufferedImage bufferedimage, boolean linear, boolean clamp,
			IResourceManager resourceManager, ResourceLocation location, MultiTexID multiTex) 
	{
		int width = bufferedimage.getWidth();
		int height = bufferedimage.getHeight();
		int size = width*height;
		int[] aint = getIntArray(size*3);
		bufferedimage.getRGB(0, 0, width, height, aint, 0, width);
		loadNSMap(resourceManager, location, width, height, aint);
		setupTexture(multiTex, aint, width, height, linear, clamp);
		return textureID;
	}
	
	public static void mergeImage(int[] aint, int dstoff, int srcoff, int size)
	{
		
	}
	
	public static int blendColor(int color1, int color2, int factor1)
	{
		int factor2 = 255-factor1;
		return
		(( (((color1>>>24)&255)*factor1 + ((color2>>>24)&255)*factor2) /255 ) << 24) |  
		(( (((color1>>>16)&255)*factor1 + ((color2>>>16)&255)*factor2) /255 ) << 16) |  
		(( (((color1>>> 8)&255)*factor1 + ((color2>>> 8)&255)*factor2) /255 ) <<  8) |  
		(( (((color1>>> 0)&255)*factor1 + ((color2>>> 0)&255)*factor2) /255 ) <<  0);  
	}

	public static void loadLayeredTexture(LayeredTexture tex, IResourceManager manager, List list)
	{
		int width  = 0;
		int height = 0;
		int size   = 0;
		int[] image = null;
		Iterator<String> iterator;
		for (iterator = list.iterator(); iterator.hasNext(); )
        {
            String s = iterator.next();
            if (s != null)
            {
            	try
            	{
            		ResourceLocation location = new ResourceLocation(s);
	                InputStream inputstream = manager.getResource(location).getInputStream();
	                BufferedImage bufimg = ImageIO.read(inputstream);
	
	                if (size == 0)
	                {
	                	width  = bufimg.getWidth();
	                	height = bufimg.getHeight();
	                	size   = width*height;
	                	image = createAIntImage(size,0x00000000);
	                }
	                int[] aint = getIntArray(size * 3);
	                bufimg.getRGB(0, 0, width, height, aint, 0, width);
	                loadNSMap(manager, location, width, height, aint);
	                // merge
	                for (int i = 0; i < size; ++i)
	                {
	                	int alpha = (aint[i] >>> 24) & 255;
	                	image[size*0+i] = blendColor(aint[size*0+i], image[size*0+i], alpha);  
	                	image[size*1+i] = blendColor(aint[size*1+i], image[size*1+i], alpha);  
	                	image[size*2+i] = blendColor(aint[size*2+i], image[size*2+i], alpha);  
	                }
            	}
            	catch (IOException ex)
                {
                    ex.printStackTrace();
                }   
            }
        }
		// init and upload
		setupTexture(tex.getMultiTexID(), image, width, height, false, false);
	}
	
	/* update block texture filter +/- items texture */
	static void updateTextureMinMagFilter()
	{
		TextureManager texman = Minecraft.getMinecraft().getTextureManager();
		ITextureObject texObj = texman.getTexture(TextureMap.locationBlocksTexture);
		if (texObj != null)
		{
			MultiTexID multiTex = texObj.getMultiTexID();
			// base texture
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, multiTex.base);
	    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, Shaders.texMinFilValue[Shaders.configTexMinFilB]);
	    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, Shaders.texMagFilValue[Shaders.configTexMagFilB]);
			// norm texture
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, multiTex.norm);
	    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, Shaders.texMinFilValue[Shaders.configTexMinFilN]);
	    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, Shaders.texMagFilValue[Shaders.configTexMagFilN]);
			
			// spec texture
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, multiTex.spec);
	    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, Shaders.texMinFilValue[Shaders.configTexMinFilS]);
	    	GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, Shaders.texMagFilValue[Shaders.configTexMagFilS]);
	    	
	    	GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
		}
	}

	
	static IResourceManager resManager = null;
	static ResourceLocation resLocation = null;
	static int imageSize = 0;
	
	public static IResource loadResource(IResourceManager manager, ResourceLocation location) throws IOException
	{
		resManager = manager;
		resLocation = location;
		return manager.getResource(location);
	}
	
	public static int[] loadAtlasSprite(BufferedImage bufferedimage, int startX, int startY, int w, int h, int[] aint, int offset, int scansize) 
	{
		imageSize = w*h;
		bufferedimage.getRGB(startX, startY, w, h, aint, offset, scansize);
		loadNSMap(resManager, resLocation, w, h, aint);
		return aint;
	}

	public static int[] extractFrame(int[] src, int width, int height, int frameIndex)
	{
		int srcSize = imageSize;
		int frameSize = width * height;
		int[] dst = new int[frameSize * 3];
		int srcPos = frameSize * frameIndex;
		int dstPos = 0;
		System.arraycopy(src, srcPos, dst, dstPos, frameSize);
		srcPos += srcSize; dstPos += frameSize;
		System.arraycopy(src, srcPos, dst, dstPos, frameSize);
		srcPos += srcSize; dstPos += frameSize;
		System.arraycopy(src, srcPos, dst, dstPos, frameSize);
		return dst;
	}

	public static void fixTransparentColor(TextureAtlasSprite tas, int[] aint) {
	}
}
