package com.gtnewhorizons.angelica.transform;

import static com.gtnewhorizons.angelica.transform.Names.*;

public class Namer1_7_10 extends Namer {

    public void setNames() {
        setNames1_7_10();
    }

    public void setNames1_7_10() {
        block_ = c("aji");
        blockFlowerPot_ = c("ald");
        minecraft_ = c("bao");
        guiButton_ = c("bcb");
        guiOptions_ = c("bdm");
        guiScreen_ = c("bdw");
        modelRenderer_ = c("bix");
        worldClient_ = c("bjf");
        effectRenderer_ = c("bkn");
        entityRenderer_ = c("blt");
        glAllocation_ = c("ban");
        itemRenderer_ = c("bly");
        openGlHelper_ = c("buu");
        renderBlocks_ = c("blm");
        renderGlobal_ = c("bma");
        tessellator_ = c("bmh");
        worldRenderer_ = c("blo");
        frustrum_ = c("bmx");
        iCamera_ = c("bmv");
        render_ = c("bno");
        renderDragon_ = c("bnl");
        renderEnderman_ = c("bnm");
        renderSpider_ = c("bov");
        renderManager_ = c("bnn");
        rendererLivingE_ = c("boh");
        abstractTexture_ = c("bpp");
        dynamicTexture_ = c("bpq");
        iTextureObject_ = c("bqh");
        layeredTexture_ = c("bpt");
        simpleTexture_ = c("bpu");
        stitcher_ = c("bpv");
        textureAtlasSpri_ = c("bqd");
        textureClock_ = c("bql");
        textureCompass_ = c("bqm");
        textureManager_ = c("bqf");
        textureMap_ = c("bpz");
        textureUtil_ = c("bqi");
        textureDownload_ = c("bpr");
        iResource_ = c("bqw");
        iResourceManager_ = c("bqy");
        gameSettings_ = c("bbj");
        tessVertexState_ = c("bmi");
        entity_ = c("sa");
        entityLivingBase_ = c("sv");
        entityDragon_ = c("xa");
        entityEnderman_ = c("ya");
        entitySpider_ = c("yn");
        entityPlayer_ = c("yz");
        item_ = c("adb");
        itemBlock_ = c("abh");
        itemStack_ = c("add");
        movingObjectPos_ = c("azu");
        resourceLocation_ = c("bqx");
        vec3_ = c("azw");
        iBlockAccess_ = c("ahl");
        world_ = c("ahb");

        entityLivingBase_deathTime = f(entityLivingBase_, "aA", "I");
        entityLivingBase_hurtTime = f(entityLivingBase_, "ax", "I");
        entityRenderer_cameraZoom = f(entityRenderer_, "af", "D");
        entityRenderer_mc = f(entityRenderer_, "t", minecraft_.desc);
        gameSettings_renderDistance = f(gameSettings_, "c", "I");
        guiButton_id = f(guiButton_, "k", "I");
        guiScreen_buttonList = f(guiScreen_, "n", "Ljava/util/List;");
        guiScreen_width = f(guiScreen_, "l", "I");
        guiScreen_height = f(guiScreen_, "m", "I");
        guiScreen_mc = f(guiScreen_, "k", minecraft_.desc);
        guiOptions_options = f(guiOptions_, "h", gameSettings_.desc);
        itemBlock_block = f(itemBlock_, "a", block_.desc);
        itemRenderer_itemToRender = f(itemRenderer_, "e", itemStack_.desc);
        layeredTexture_layeredTextureNames = f(layeredTexture_, "b", "Ljava/util/List;");
        minecraft_renderGlobal = f(minecraft_, "g", renderGlobal_.desc);
        minecraft_gameSettings = f(minecraft_, "u", gameSettings_.desc);
        modelRenderer_displayList = f(modelRenderer_, "u", "I");
        modelRenderer_compiled = f(modelRenderer_, "t", "Z");
        renderGlobal_glSkyList = f(renderGlobal_, "G", "I");
        renderGlobal_worldRenderers = f(renderGlobal_, "v", "[" + worldRenderer_.desc);
        rendererLivingE_mainModel = f(rendererLivingE_, "i", null);
        rendererLivingE_renderPassModel = f(rendererLivingE_, "j", null);
        renderManager_entityRenderMap = f(renderManager_, "q", "Ljava/util/Map;");
        renderManager_instance = f(renderManager_, "a", renderManager_.desc);
        simpleTexture_textureLocation = f(simpleTexture_, "b", resourceLocation_.desc);
        tessellator_floatBuffer = f(tessellator_, "d", "Ljava/nio/FloatBuffer;");
        tessellator_shortBuffer = f(tessellator_, "e", "Ljava/nio/ShortBuffer;");
        tessellator_intBuffer = f(tessellator_, "c", "Ljava/nio/IntBuffer;");
        tessellator_byteBuffer = f(tessellator_, "b", "Ljava/nio/ByteBuffer;");
        tessellator_rawBuffer = f(tessellator_, "f", "[I");
        tessellator_vertexCount = f(tessellator_, "g", "I");
        tessellator_hasNormals = f(tessellator_, "o", "Z");
        textureAtlasSpri_width = f(textureAtlasSpri_, "e", null);
        textureAtlasSpri_height = f(textureAtlasSpri_, "f", null);
        textureAtlasSpri_border = f(textureAtlasSpri_, "k", null);
        textureMap_anisotropic = f(textureMap_, "k", null);
        textureDownload_textureUploaded = f(textureDownload_, "j", "Z");
        vec3_xCoord = f(vec3_, "a", "D");

        abstractTexture_deleteGlTexture = m(abstractTexture_, "c", "()V");
        block_getAoLight = m(block_, "I", "()F");
        block_getBlockFromItem = m(block_, "a", "(" + item_.desc + ")" + block_.desc);
        dynamicTexture_updateDynamicTexture = m(dynamicTexture_, "a", "()V");
        effectRenderer_renderLitParticles = m(effectRenderer_, "b", "(" + entity_.desc + "F)V");
        effectRenderer_renderParticles = m(effectRenderer_, "a", "(" + entity_.desc + "F)V");
        entity_getBrightness = m(entity_, "d", "(F)F");
        entityRenderer_enableLightmap = m(entityRenderer_, "b", "(D)V");
        entityRenderer_updateFogColor = m(entityRenderer_, "j", "(F)V");
        entityRenderer_setupFog = m(entityRenderer_, "a", "(IF)V");
        entityRenderer_setFogColorBuffer = m(entityRenderer_, "a", "(FFFF)Ljava/nio/FloatBuffer;");
        entityRenderer_renderWorld = m(entityRenderer_, "a", "(FJ)V");
        entityRenderer_renderRainSnow = m(entityRenderer_, "e", "(F)V");
        entityRenderer_renderHand = m(entityRenderer_, "b", "(FI)V");
        entityRenderer_setupCameraTransform = m(entityRenderer_, "a", "(FI)V");
        entityRenderer_disableLightmap = m(entityRenderer_, "a", "(D)V");
        entityRenderer_renderCloudsCheck = m(entityRenderer_, "a", "(" + renderGlobal_.desc + "F)V");
        gameSettings_saveOptions = m(gameSettings_, "b", "()V");
        gameSettings_shouldRenderClouds = m(gameSettings_, "d", "()Z");
        glAllocation_createDirectByteBuffer = m(glAllocation_, "c", "(I)Ljava/nio/ByteBuffer;");
        glAllocation_createDirectIntBuffer = m(glAllocation_, "f", "(I)Ljava/nio/IntBuffer;");
        glAllocation_deleteDisplayLists = m(glAllocation_, "b", "(I)V");
        guiOptions_actionPerformed = m(guiOptions_, "a", "(" + guiButton_.desc + ")V");
        guiOptions_initGui = m(guiOptions_, "b", "()V");
        iCamera_setPosition = m(iCamera_, "a", "(DDD)V");
        iResourceManager_getResource = m(iResourceManager_, "a", "(" + resourceLocation_.desc + ")" + iResource_.desc);
        itemRenderer_renderItem = m(itemRenderer_, "a", "(" + entityLivingBase_.desc + itemStack_.desc + "I)V");
        itemRenderer_renderItemInFirstPerson = m(itemRenderer_, "a", "(F)V");
        itemRenderer_renderOverlays = m(itemRenderer_, "b", "(F)V");
        itemRenderer_updateEquipped = m(itemRenderer_, "a", "()V");
        iTextureObject_loadTexture = m(iTextureObject_, "a", "(" + iResourceManager_.desc + ")V");
        iTextureObject_getGlTextureId = m(iTextureObject_, "b", "()I");
        minecraft_displayGuiScreen = m(minecraft_, "a", "(" + guiScreen_.desc + ")V");
        openGlHelper_setActiveTexture = m(openGlHelper_, "j", "(I)V");
        render_renderShadow = m(render_, "c", "(" + entity_.desc + "DDDFF)V");
        renderBlocks_renderBlockFluids = m(renderBlocks_, "p", "(" + block_.desc + "III)Z");
        renderBlocks_renderStdBlockWithCM = m(renderBlocks_, "d", "(" + block_.desc + "IIIFFF)Z");
        renderBlocks_renderBlockSandFalling = m(renderBlocks_, "a", "(" + block_.desc + world_.desc + "IIII)V");
        renderBlocks_renderStdBlockWithAO = m(renderBlocks_, "a", "(" + block_.desc + "IIIFFF)Z");
        renderBlocks_renderBlockCactusImpl = m(renderBlocks_, "e", "(" + block_.desc + "IIIFFF)Z");
        renderBlocks_renderBlockDoor = m(renderBlocks_, "u", "(" + block_.desc + "III)Z");
        renderBlocks_renderBlockBed = m(renderBlocks_, "v", "(" + block_.desc + "III)Z");
        renderBlocks_renderBlockByRenderType = m(renderBlocks_, "b", "(" + block_.desc + "III)Z");
        renderBlocks_renderBlockFlowerPot = m(renderBlocks_, "a", "(" + blockFlowerPot_.desc + "III)Z");
        renderBlocks_renderStdBlockWithAOP = m(renderBlocks_, "b", "(" + block_.desc + "IIIFFF)Z");
        renderBlocks_renderPistonExtension = m(renderBlocks_, "c", "(" + block_.desc + "IIIZ)Z");
        renderDragon_shouldRenderPass = m(renderDragon_, "a", "(" + entityDragon_.desc + "IF)I");
        renderEnderman_shouldRenderPass = m(renderEnderman_, "a", "(" + entityEnderman_.desc + "IF)I");
        renderSpider_shouldRenderPass = m(renderSpider_, "a", "(" + entitySpider_.desc + "IF)I");
        rendererLivingE_doRender = m(rendererLivingE_, "a", "(" + entityLivingBase_.desc + "DDDFF)V");
        rendererLivingE_renderEquippedItems = m(rendererLivingE_, "c", "(" + entityLivingBase_.desc + "F)V");
        rendererLivingE_getColorMultiplier = m(rendererLivingE_, "a", "(" + entityLivingBase_.desc + "FF)I");
        rendererLivingE_renderLabel = m(
                rendererLivingE_,
                "a",
                "(" + entityLivingBase_.desc + "DDDLjava/lang/String;FD)V");
        renderGlobal_renderEntities = m(renderGlobal_, "a", "(" + entityLivingBase_.desc + iCamera_.desc + "F)V");
        renderGlobal_renderSky = m(renderGlobal_, "a", "(F)V");
        renderGlobal_drawBlockDamageTexture = m(
                renderGlobal_,
                "a",
                "(" + tessellator_.desc + entityPlayer_.desc + "F)V");
        renderGlobal_renderClouds = m(renderGlobal_, "b", "(F)V");
        renderGlobal_sortAndRender = m(renderGlobal_, "a", "(" + entityLivingBase_.desc + "ID)I");
        renderGlobal_clipRenderersByFrustum = m(renderGlobal_, "a", "(" + iCamera_.desc + "F)V");
        renderGlobal_drawSelectionBox = m(
                renderGlobal_,
                "a",
                "(" + entityPlayer_.desc + movingObjectPos_.desc + "IF)V");
        renderGlobal_renderAllRenderLists = m(renderGlobal_, "a", "(ID)V");
        stitcher_getCurrentWidth = m(stitcher_, "a", "()I");
        tessellator_sortQuad = m(tessellator_, "a", "(FFF)" + tessVertexState_.desc);
        tessellator_addTranslation = m(tessellator_, "d", "(FFF)V");
        tessellator_setNormal = m(tessellator_, "c", "(FFF)V");
        tessellator_addVertex = m(tessellator_, "a", "(DDD)V");
        tessellator_reset = m(tessellator_, "d", "()V");
        tessellator_draw = m(tessellator_, "a", "()I");
        textureAtlasSpri_loadSprite = m(textureAtlasSpri_, "-", "(" + iResource_.desc + ")V");
        textureAtlasSpri_getFrameTextureData = m(textureAtlasSpri_, "a", "(I)[[I");
        textureAtlasSpri_getIconName = m(textureAtlasSpri_, "g", "()Ljava/lang/String;");
        textureAtlasSpri_updateAnimation = m(textureAtlasSpri_, "j", "()V");
        textureCompass_updateCompass = m(textureCompass_, "a", "(" + world_.desc + "DDDZZ)V");
        textureManager_onResourceManagerReload = m(textureManager_, "a", "(" + iResourceManager_.desc + ")V");
        textureManager_bindTexture = m(textureManager_, "a", "(" + resourceLocation_.desc + ")V");
        textureMap_loadTextureAtlas = m(textureMap_, "b", "(" + iResourceManager_.desc + ")V");
        textureMap_getIconResLoc = m(textureMap_, "a", "(" + resourceLocation_.desc + "I)" + resourceLocation_.desc);
        textureMap_updateAnimations = m(textureMap_, "d", "()V");
        textureUtil_uploadTexture = m(textureUtil_, "a", "(I[III)V");
        textureUtil_uploadTextureImageAllocate = m(textureUtil_, "a", "(ILjava/awt/image/BufferedImage;ZZ)I");
        textureUtil_allocateTexture = m(textureUtil_, "a", "(III)V");
        textureUtil_allocateTextureMipmapAniso = m(textureUtil_, "a", "(IIIIF)V");
        textureUtil_uploadTexSub = m(textureUtil_, "a", "([[IIIIIZZ)V");
        textureUtil_bindTexture = m(textureUtil_, "b", "(I)V");
        world_getCelestialAngle = m(world_, "c", "(F)F");
        world_getRainStrength = m(world_, "j", "(F)F");
        modelRenderer_render = m(modelRenderer_, "a", "(F)V");
        modelRenderer_renderWithRotation = m(modelRenderer_, "b", "(F)V");

        guiOptions_buttonList = f(guiOptions_, guiScreen_buttonList);
        guiOptions_width = f(guiOptions_, guiScreen_width);
        guiOptions_height = f(guiOptions_, guiScreen_height);
        guiOptions_mc = f(guiOptions_, guiScreen_mc);
        frustrum_setPosition = m(frustrum_, iCamera_setPosition);
        worldClient_getCelestialAngle = m(worldClient_, world_getCelestialAngle);
        worldClient_getRainStrength = m(worldClient_, world_getRainStrength);
    }
}
