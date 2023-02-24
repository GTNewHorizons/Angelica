package com.gtnewhorizons.angelica.transform;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.launchwrapper.IClassTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

public class AClassTransformer implements IClassTransformer {

    Names names;
    /** map of class transformer */
    protected Map<String, IClassTransformer> ctMap;

    public void put(Names.Clas clas, IClassTransformer ct) {
        ctMap.put(clas.clas.replace('/', '.'), ct);
    }

    // constructor
    public AClassTransformer() {
        names = new Names();
        InitNames.init();
        ctMap = new HashMap();
        put(Names.block_, new ACTBlock());
        put(Names.itemBlock_, new ACTItemBlock());
        put(Names.modelRenderer_, new ACTModelRenderer());
        put(Names.openGlHelper_, new ACTOpenGlHelper());
        put(Names.tessellator_, new ACTTessellator());
        put(Names.renderBlocks_, new ACTRenderBlocks());
        put(Names.renderGlobal_, new ACTRenderGlobal());
        put(Names.entityRenderer_, new ACTEntityRenderer());
        put(Names.render_, new ACTRender());
        put(Names.renderManager_, new ACTRenderManager());
        put(Names.rendererLivingE_, new ACTRendererLivingEntity());
        put(Names.renderDragon_, new ACTRenderSpider());
        put(Names.renderEnderman_, new ACTRenderSpider());
        put(Names.renderSpider_, new ACTRenderSpider());
        put(Names.itemRenderer_, new ACTItemRenderer());
        put(Names.textureDownload_, new ACTTextureDownload());
        put(Names.abstractTexture_, new ACTTextureAbstract());
        put(Names.iTextureObject_, new ACTTextureObject());
        put(Names.simpleTexture_, new ACTTextureSimple());
        put(Names.layeredTexture_, new ACTTextureLayered());
        put(Names.dynamicTexture_, new ACTTextureDynamic());
        put(Names.textureMap_, new ACTTextureMap());
        put(Names.textureAtlasSpri_, new ACTTextureAtlasSprite());
        put(Names.textureClock_, new ACTTextureClock());
        put(Names.textureCompass_, new ACTTextureCompass());
        put(Names.textureManager_, new ACTTextureManager());
        ctMap.put("mrtjp.projectred.illumination.RenderHalo$", new ACTPrjRedIlluRenderHalo());
        ctMap.put("net.smart.render.ModelRotationRenderer", new ACTSmartMoveModelRotationRenderer());
    }

    @Override
    public byte[] transform(String par1, String par2, byte[] par3) {
        byte[] bytecode = par3;
        // if (par2.startsWith("mrtjp")) ALog.info("**** [%s]", par2);
        IClassTransformer ct = ctMap.get(par2);
        if (ct != null) {
            bytecode = ct.transform(par1, par2, bytecode);
            // HACK: Fix stackframes
            ClassNode node = new ClassNode();
            ClassReader reader = new ClassReader(bytecode);
            reader.accept(node, ClassReader.SKIP_FRAMES);
            ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
            node.accept(writer);
            bytecode = writer.toByteArray();
            // END HACK
            int len1 = par3.length; // arg2!=null?arg2.length:0;
            int len2 = bytecode.length; // bytecode!=null?bytecode.length:0;
            ALog.fine(" %d (%+d)", len2, len2 - len1);
        }
        return bytecode;
    }
}
