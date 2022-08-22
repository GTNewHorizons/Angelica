package shadersmodcore.transform;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.Remapper;

public class SMCClassTransformer implements IClassTransformer {

	Names names;
	/** map of class transformer */	
	protected Map<String,IClassTransformer> ctMap;

	public void put(Names.Clas clas, IClassTransformer ct)
	{
		ctMap.put(clas.clas.replace('/', '.'), ct);
	}
	
	// constructor
	public SMCClassTransformer()
	{
		names = new Names();
		InitNames.init();
		ctMap = new HashMap();
		put(Names.block_           , new SMCCTBlock());
		put(Names.itemBlock_       , new SMCCTItemBlock());
		put(Names.minecraft_       , new SMCCTMinecraft());
		put(Names.guiOptions_      , new SMCCTGuiOptions());
		put(Names.modelRenderer_   , new SMCCTModelRenderer());
		put(Names.openGlHelper_    , new SMCCTOpenGlHelper());
		put(Names.tessellator_     , new SMCCTTessellator());
		put(Names.renderBlocks_    , new SMCCTRenderBlocks());
		put(Names.renderGlobal_    , new SMCCTRenderGlobal());
		put(Names.entityRenderer_  , new SMCCTEntityRenderer());
		put(Names.render_          , new SMCCTRender());
		put(Names.renderManager_   , new SMCCTRenderManager());
		put(Names.rendererLivingE_ , new SMCCTRendererLivingEntity());
		put(Names.renderDragon_    , new SMCCTRenderSpider());
		put(Names.renderEnderman_  , new SMCCTRenderSpider());
		put(Names.renderSpider_    , new SMCCTRenderSpider());
		put(Names.itemRenderer_    , new SMCCTItemRenderer());
		put(Names.textureDownload_ , new SMCCTTextureDownload());
		put(Names.abstractTexture_ , new SMCCTTextureAbstract());
		put(Names.iTextureObject_  , new SMCCTTextureObject());
		put(Names.simpleTexture_   , new SMCCTTextureSimple());
		put(Names.layeredTexture_  , new SMCCTTextureLayered());
		put(Names.dynamicTexture_  , new SMCCTTextureDynamic());
		put(Names.textureMap_      , new SMCCTTextureMap());
		put(Names.textureAtlasSpri_, new SMCCTTextureAtlasSprite());
		put(Names.textureClock_    , new SMCCTTextureClock());
		put(Names.textureCompass_  , new SMCCTTextureCompass());
		put(Names.textureManager_  , new SMCCTTextureManager());
		ctMap.put("mrtjp.projectred.illumination.RenderHalo$", new SMCCTPrjRedIlluRenderHalo());
		ctMap.put("net.smart.render.ModelRotationRenderer", new SMCCTSmartMoveModelRotationRenderer());
	}

	@Override
	public byte[] transform(String par1, String par2, byte[] par3) 
	{
		byte[] bytecode = par3;
		//if (par2.startsWith("mrtjp")) SMCLog.info("**** [%s]", par2);
		IClassTransformer ct = ctMap.get(par2);
		if (ct != null)
		{
			bytecode = ct.transform(par1, par2, bytecode);
			int len1 = par3.length; //arg2!=null?arg2.length:0;
			int len2 = bytecode.length; //bytecode!=null?bytecode.length:0;
			SMCLog.fine(" %d (%+d)", len2, len2-len1);
		}
		return bytecode;
	}

}