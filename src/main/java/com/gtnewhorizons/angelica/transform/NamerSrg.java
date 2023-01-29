package com.gtnewhorizons.angelica.transform;

import static com.gtnewhorizons.angelica.transform.Names.*;

public class NamerSrg extends Namer {

    public void setNames() {
        setNamesSrg();
    }

    public void setNamesSrg() {
        block_ = c("net/minecraft/block/Block");
        blockFlowerPot_ = c("net/minecraft/block/BlockFlowerPot");
        minecraft_ = c("net/minecraft/client/Minecraft");
        guiButton_ = c("net/minecraft/client/gui/GuiButton");
        guiOptions_ = c("net/minecraft/client/gui/GuiOptions");
        guiScreen_ = c("net/minecraft/client/gui/GuiScreen");
        modelRenderer_ = c("net/minecraft/client/model/ModelRenderer");
        worldClient_ = c("net/minecraft/client/multiplayer/WorldClient");
        effectRenderer_ = c("net/minecraft/client/particle/EffectRenderer");
        entityRenderer_ = c("net/minecraft/client/renderer/EntityRenderer");
        glAllocation_ = c("net/minecraft/client/renderer/GLAllocation");
        itemRenderer_ = c("net/minecraft/client/renderer/ItemRenderer");
        openGlHelper_ = c("net/minecraft/client/renderer/OpenGlHelper");
        renderBlocks_ = c("net/minecraft/client/renderer/RenderBlocks");
        renderGlobal_ = c("net/minecraft/client/renderer/RenderGlobal");
        tessellator_ = c("net/minecraft/client/renderer/Tessellator");
        worldRenderer_ = c("net/minecraft/client/renderer/WorldRenderer");
        frustrum_ = c("net/minecraft/client/renderer/culling/Frustrum");
        iCamera_ = c("net/minecraft/client/renderer/culling/ICamera");
        render_ = c("net/minecraft/client/renderer/entity/Render");
        renderDragon_ = c("net/minecraft/client/renderer/entity/RenderDragon");
        renderEnderman_ = c("net/minecraft/client/renderer/entity/RenderEnderman");
        renderSpider_ = c("net/minecraft/client/renderer/entity/RenderSpider");
        renderManager_ = c("net/minecraft/client/renderer/entity/RenderManager");
        rendererLivingE_ = c("net/minecraft/client/renderer/entity/RendererLivingEntity");
        abstractTexture_ = c("net/minecraft/client/renderer/texture/AbstractTexture");
        dynamicTexture_ = c("net/minecraft/client/renderer/texture/DynamicTexture");
        iTextureObject_ = c("net/minecraft/client/renderer/texture/ITextureObject");
        layeredTexture_ = c("net/minecraft/client/renderer/texture/LayeredTexture");
        simpleTexture_ = c("net/minecraft/client/renderer/texture/SimpleTexture");
        stitcher_ = c("net/minecraft/client/renderer/texture/Stitcher");
        textureAtlasSpri_ = c("net/minecraft/client/renderer/texture/TextureAtlasSprite");
        textureClock_ = c("net/minecraft/client/renderer/texture/TextureClock");
        textureCompass_ = c("net/minecraft/client/renderer/texture/TextureCompass");
        textureManager_ = c("net/minecraft/client/renderer/texture/TextureManager");
        textureMap_ = c("net/minecraft/client/renderer/texture/TextureMap");
        textureUtil_ = c("net/minecraft/client/renderer/texture/TextureUtil");
        textureDownload_ = c("net/minecraft/client/renderer/ThreadDownloadImageData");
        iResource_ = c("net/minecraft/client/resources/IResource");
        iResourceManager_ = c("net/minecraft/client/resources/IResourceManager");
        gameSettings_ = c("net/minecraft/client/settings/GameSettings");
        tessVertexState_ = c("net/minecraft/client/shader/TesselatorVertexState");
        entity_ = c("net/minecraft/entity/Entity");
        entityLivingBase_ = c("net/minecraft/entity/EntityLivingBase");
        entityDragon_ = c("net/minecraft/entity/boss/EntityDragon");
        entityEnderman_ = c("net/minecraft/entity/monster/EntityEnderman");
        entitySpider_ = c("net/minecraft/entity/monster/EntitySpider");
        entityPlayer_ = c("net/minecraft/entity/player/EntityPlayer");
        item_ = c("net/minecraft/item/Item");
        itemBlock_ = c("net/minecraft/item/ItemBlock");
        itemStack_ = c("net/minecraft/item/ItemStack");
        movingObjectPos_ = c("net/minecraft/util/MovingObjectPosition");
        resourceLocation_ = c("net/minecraft/util/ResourceLocation");
        vec3_ = c("net/minecraft/util/Vec3");
        iBlockAccess_ = c("net/minecraft/world/IBlockAccess");
        world_ = c("net/minecraft/world/World");

        entityLivingBase_deathTime = f(entityLivingBase_, "field_70725_aQ", "I");
        entityLivingBase_hurtTime = f(entityLivingBase_, "field_70737_aN", "I");
        entityRenderer_cameraZoom = f(entityRenderer_, "field_78503_V", "D");
        entityRenderer_mc = f(entityRenderer_, "field_78531_r", minecraft_.desc);
        gameSettings_renderDistance = f(gameSettings_, "field_151451_c", "I");
        guiButton_id = f(guiButton_, "field_146127_k", "I");
        guiScreen_buttonList = f(guiScreen_, "field_146292_n", "Ljava/util/List;");
        guiScreen_width = f(guiScreen_, "field_146294_l", "I");
        guiScreen_height = f(guiScreen_, "field_146295_m", "I");
        guiScreen_mc = f(guiScreen_, "field_146297_k", minecraft_.desc);
        guiOptions_options = f(guiOptions_, "field_146443_h", gameSettings_.desc);
        itemBlock_block = f(itemBlock_, "field_150939_a", block_.desc);
        itemRenderer_itemToRender = f(itemRenderer_, "field_78453_b", itemStack_.desc);
        layeredTexture_layeredTextureNames = f(layeredTexture_, "field_110567_b", "Ljava/util/List;");
        minecraft_renderGlobal = f(minecraft_, "field_71438_f", renderGlobal_.desc);
        minecraft_gameSettings = f(minecraft_, "field_71474_y", gameSettings_.desc);
        modelRenderer_displayList = f(modelRenderer_, "field_78811_r", "I");
        modelRenderer_compiled = f(modelRenderer_, "field_78812_q", "Z");
        renderGlobal_glSkyList = f(renderGlobal_, "field_72771_w", "I");
        renderGlobal_worldRenderers = f(renderGlobal_, "field_72765_l", "[" + worldRenderer_.desc);
        rendererLivingE_mainModel = f(rendererLivingE_, "field_77045_g", null);
        rendererLivingE_renderPassModel = f(rendererLivingE_, "field_77046_h", null);
        renderManager_entityRenderMap = f(renderManager_, "field_78729_o", "Ljava/util/Map;");
        renderManager_instance = f(renderManager_, "field_78727_a", renderManager_.desc);
        simpleTexture_textureLocation = f(simpleTexture_, "field_110568_b", resourceLocation_.desc);
        tessellator_floatBuffer = f(tessellator_, "field_147566_d", "Ljava/nio/FloatBuffer;");
        tessellator_shortBuffer = f(tessellator_, "field_147567_e", "Ljava/nio/ShortBuffer;");
        tessellator_intBuffer = f(tessellator_, "field_147568_c", "Ljava/nio/IntBuffer;");
        tessellator_byteBuffer = f(tessellator_, "field_78394_d", "Ljava/nio/ByteBuffer;");
        tessellator_rawBuffer = f(tessellator_, "field_78405_h", "[I");
        tessellator_vertexCount = f(tessellator_, "field_78406_i", "I");
        tessellator_hasNormals = f(tessellator_, "field_78413_q", "Z");
        textureAtlasSpri_width = f(textureAtlasSpri_, "field_130223_c", null);
        textureAtlasSpri_height = f(textureAtlasSpri_, "field_130224_d", null);
        textureAtlasSpri_border = f(textureAtlasSpri_, "field_147966_k", null);
        textureMap_anisotropic = f(textureMap_, "field_147637_k", null);
        textureDownload_textureUploaded = f(textureDownload_, "field_110559_g", "Z");
        vec3_xCoord = f(vec3_, "field_72450_a", "D");

        abstractTexture_deleteGlTexture = m(abstractTexture_, "func_147631_c", "()V");
        block_getAoLight = m(block_, "func_149685_I", "()F");
        block_getBlockFromItem = m(block_, "func_149634_a", "(" + item_.desc + ")" + block_.desc);
        dynamicTexture_updateDynamicTexture = m(dynamicTexture_, "func_110564_a", "()V");
        effectRenderer_renderLitParticles = m(effectRenderer_, "func_78872_b", "(" + entity_.desc + "F)V");
        effectRenderer_renderParticles = m(effectRenderer_, "func_78874_a", "(" + entity_.desc + "F)V");
        entity_getBrightness = m(entity_, "func_70013_c", "(F)F");
        entityRenderer_enableLightmap = m(entityRenderer_, "func_78463_b", "(D)V");
        entityRenderer_updateFogColor = m(entityRenderer_, "func_78466_h", "(F)V");
        entityRenderer_setupFog = m(entityRenderer_, "func_78468_a", "(IF)V");
        entityRenderer_setFogColorBuffer = m(entityRenderer_, "func_78469_a", "(FFFF)Ljava/nio/FloatBuffer;");
        entityRenderer_renderWorld = m(entityRenderer_, "func_78471_a", "(FJ)V");
        entityRenderer_renderRainSnow = m(entityRenderer_, "func_78474_d", "(F)V");
        entityRenderer_renderHand = m(entityRenderer_, "func_78476_b", "(FI)V");
        entityRenderer_setupCameraTransform = m(entityRenderer_, "func_78479_a", "(FI)V");
        entityRenderer_disableLightmap = m(entityRenderer_, "func_78483_a", "(D)V");
        entityRenderer_renderCloudsCheck = m(entityRenderer_, "func_82829_a", "(" + renderGlobal_.desc + "F)V");
        gameSettings_saveOptions = m(gameSettings_, "func_74303_b", "()V");
        gameSettings_shouldRenderClouds = m(gameSettings_, "func_74309_c", "()Z");
        glAllocation_createDirectByteBuffer = m(glAllocation_, "func_74524_c", "(I)Ljava/nio/ByteBuffer;");
        glAllocation_createDirectIntBuffer = m(glAllocation_, "func_74527_f", "(I)Ljava/nio/IntBuffer;");
        glAllocation_deleteDisplayLists = m(glAllocation_, "func_74523_b", "(I)V");
        guiOptions_actionPerformed = m(guiOptions_, "func_146284_a", "(" + guiButton_.desc + ")V");
        guiOptions_initGui = m(guiOptions_, "func_73866_w_", "()V");
        iCamera_setPosition = m(iCamera_, "func_78547_a", "(DDD)V");
        iResourceManager_getResource = m(
                iResourceManager_,
                "func_110536_a",
                "(" + resourceLocation_.desc + ")" + iResource_.desc);
        itemRenderer_renderItem = m(
                itemRenderer_,
                "func_78443_a",
                "(" + entityLivingBase_.desc + itemStack_.desc + "I)V");
        itemRenderer_renderItemInFirstPerson = m(itemRenderer_, "func_78440_a", "(F)V");
        itemRenderer_renderOverlays = m(itemRenderer_, "func_78447_b", "(F)V");
        itemRenderer_updateEquipped = m(itemRenderer_, "func_78441_a", "()V");
        iTextureObject_loadTexture = m(iTextureObject_, "func_110551_a", "(" + iResourceManager_.desc + ")V");
        iTextureObject_getGlTextureId = m(iTextureObject_, "func_110552_b", "()I");
        minecraft_displayGuiScreen = m(minecraft_, "func_147108_a", "(" + guiScreen_.desc + ")V");
        minecraft_startGame = m(minecraft_, "func_71384_a", "()V");
        openGlHelper_setActiveTexture = m(openGlHelper_, "func_77473_a", "(I)V");
        render_renderShadow = m(render_, "func_76975_c", "(" + entity_.desc + "DDDFF)V");
        renderBlocks_renderBlockFluids = m(renderBlocks_, "func_147721_p", "(" + block_.desc + "III)Z");
        renderBlocks_renderStdBlockWithCM = m(renderBlocks_, "func_147736_d", "(" + block_.desc + "IIIFFF)Z");
        renderBlocks_renderBlockSandFalling = m(
                renderBlocks_,
                "func_147749_a",
                "(" + block_.desc + world_.desc + "IIII)V");
        renderBlocks_renderStdBlockWithAO = m(renderBlocks_, "func_147751_a", "(" + block_.desc + "IIIFFF)Z");
        renderBlocks_renderBlockCactusImpl = m(renderBlocks_, "func_147754_e", "(" + block_.desc + "IIIFFF)Z");
        renderBlocks_renderBlockDoor = m(renderBlocks_, "func_147760_u", "(" + block_.desc + "III)Z");
        renderBlocks_renderBlockBed = m(renderBlocks_, "func_147773_v", "(" + block_.desc + "III)Z");
        renderBlocks_renderBlockByRenderType = m(renderBlocks_, "func_147805_b", "(" + block_.desc + "III)Z");
        renderBlocks_renderBlockFlowerPot = m(renderBlocks_, "func_147752_a", "(" + blockFlowerPot_.desc + "III)Z");
        renderBlocks_renderStdBlockWithAOP = m(renderBlocks_, "func_147808_b", "(" + block_.desc + "IIIFFF)Z");
        renderBlocks_renderPistonExtension = m(renderBlocks_, "func_147809_c", "(" + block_.desc + "IIIZ)Z");
        renderDragon_shouldRenderPass = m(renderDragon_, "func_77032_a", "(" + entityDragon_.desc + "IF)I");
        renderEnderman_shouldRenderPass = m(renderEnderman_, "func_77032_a", "(" + entityEnderman_.desc + "IF)I");
        renderSpider_shouldRenderPass = m(renderSpider_, "func_77032_a", "(" + entitySpider_.desc + "IF)I");
        rendererLivingE_doRender = m(rendererLivingE_, "func_76986_a", "(" + entityLivingBase_.desc + "DDDFF)V");
        rendererLivingE_renderEquippedItems = m(rendererLivingE_, "func_77029_c", "(" + entityLivingBase_.desc + "F)V");
        rendererLivingE_getColorMultiplier = m(rendererLivingE_, "func_77030_a", "(" + entityLivingBase_.desc + "FF)I");
        rendererLivingE_renderLabel = m(
                rendererLivingE_,
                "func_96449_a",
                "(" + entityLivingBase_.desc + "DDDLjava/lang/String;FD)V");
        renderGlobal_renderEntities = m(
                renderGlobal_,
                "func_147589_a",
                "(" + entityLivingBase_.desc + iCamera_.desc + "F)V");
        renderGlobal_renderSky = m(renderGlobal_, "func_72714_a", "(F)V");
        renderGlobal_drawBlockDamageTexture = m(
                renderGlobal_,
                "func_72717_a",
                "(" + tessellator_.desc + entityPlayer_.desc + "F)V");
        renderGlobal_renderClouds = m(renderGlobal_, "func_72718_b", "(F)V");
        renderGlobal_sortAndRender = m(renderGlobal_, "func_72719_a", "(" + entityLivingBase_.desc + "ID)I");
        renderGlobal_clipRenderersByFrustum = m(renderGlobal_, "func_72729_a", "(" + iCamera_.desc + "F)V");
        renderGlobal_drawSelectionBox = m(
                renderGlobal_,
                "func_72731_b",
                "(" + entityPlayer_.desc + movingObjectPos_.desc + "IF)V");
        renderGlobal_renderAllRenderLists = m(renderGlobal_, "func_72733_a", "(ID)V");
        stitcher_getCurrentWidth = m(stitcher_, "func_110935_a", "()I");
        tessellator_sortQuad = m(tessellator_, "func_147564_a", "(FFF)" + tessVertexState_.desc);
        tessellator_addTranslation = m(tessellator_, "func_78372_c", "(FFF)V");
        tessellator_setNormal = m(tessellator_, "func_78375_b", "(FFF)V");
        tessellator_addVertex = m(tessellator_, "func_78377_a", "(DDD)V");
        tessellator_reset = m(tessellator_, "func_78379_d", "()V");
        tessellator_draw = m(tessellator_, "func_78381_a", "()I");
        textureAtlasSpri_loadSprite = m(textureAtlasSpri_, "func_130100_a", "(" + iResource_.desc + ")V");
        textureAtlasSpri_getFrameTextureData = m(textureAtlasSpri_, "func_147965_a", "(I)[[I");
        textureAtlasSpri_getIconName = m(textureAtlasSpri_, "func_94215_i", "()Ljava/lang/String;");
        textureAtlasSpri_updateAnimation = m(textureAtlasSpri_, "func_94219_l", "()V");
        textureCompass_updateCompass = m(textureCompass_, "func_94241_a", "(" + world_.desc + "DDDZZ)V");
        textureManager_onResourceManagerReload = m(
                textureManager_,
                "func_110549_a",
                "(" + iResourceManager_.desc + ")V");
        textureManager_bindTexture = m(textureManager_, "func_110577_a", "(" + resourceLocation_.desc + ")V");
        textureMap_loadTextureAtlas = m(textureMap_, "func_110571_b", "(" + iResourceManager_.desc + ")V");
        textureMap_getIconResLoc = m(
                textureMap_,
                "func_147634_a",
                "(" + resourceLocation_.desc + "I)" + resourceLocation_.desc);
        textureMap_updateAnimations = m(textureMap_, "func_94248_c", "()V");
        textureUtil_uploadTexture = m(textureUtil_, "func_110988_a", "(I[III)V");
        textureUtil_uploadTextureImageAllocate = m(
                textureUtil_,
                "func_110989_a",
                "(ILjava/awt/image/BufferedImage;ZZ)I");
        textureUtil_allocateTexture = m(textureUtil_, "func_110991_a", "(III)V");
        textureUtil_allocateTextureMipmapAniso = m(textureUtil_, "func_147946_a", "(IIIIF)V");
        textureUtil_uploadTexSub = m(textureUtil_, "func_147955_a", "([[IIIIIZZ)V");
        textureUtil_bindTexture = m(textureUtil_, "func_94277_a", "(I)V");
        world_getCelestialAngle = m(world_, "func_72826_c", "(F)F");
        world_getRainStrength = m(world_, "func_72867_j", "(F)F");
        modelRenderer_render = m(modelRenderer_, "func_78785_a", "(F)V");
        modelRenderer_renderWithRotation = m(modelRenderer_, "func_78791_b", "(F)V");

        guiOptions_buttonList = f(guiOptions_, guiScreen_buttonList);
        guiOptions_width = f(guiOptions_, guiScreen_width);
        guiOptions_height = f(guiOptions_, guiScreen_height);
        guiOptions_mc = f(guiOptions_, guiScreen_mc);
        frustrum_setPosition = m(frustrum_, iCamera_setPosition);
        worldClient_getCelestialAngle = m(worldClient_, world_getCelestialAngle);
        worldClient_getRainStrength = m(worldClient_, world_getRainStrength);
    }
}
