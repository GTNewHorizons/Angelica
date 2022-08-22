package shadersmodcore.transform;

public class Names {
	
	public static class Name {
		String clas;
		String name;
		String desc;
		public Name(String clas, String name, String desc) {
			this.clas = clas;
			this.name = name;
			this.desc = desc;
		}
		public Name set(String clas, String name, String desc) {
			this.clas = clas;
			this.name = name;
			this.desc = desc;
			return this;
		}
		public boolean equals(String clas, String name, String desc) {
			return this.clas.equals(clas) && this.name.equals(name) && this.desc.equals(desc);
		}
	}
	
	public static class Type extends Name {
		public Type(String desc) {
			super("","",desc);
		}
		public Type(String name, String desc) {
			super(name,name,desc);
		}
	}
	public static class Clas extends Type {
		public Clas(String name) {
			super(name,"L"+name+";");
		}
		public boolean equals(String clas) {
			return this.clas.equals(clas);
		}
	}
	public static class Fiel extends Name {
		public Fiel(Clas clas, String name, String desc) {
			super(clas.clas,name,desc);
		}
		public boolean equals(String clas, String name) {
			return this.clas.equals(clas) && this.name.equals(name);
		}
	}
	public static class Meth extends Name {
		public Meth(Clas clas, String name, String desc) {
			super(clas.clas,name,desc);
		}
		public boolean equalsNameDesc(String name, String desc) {
			return this.name.equals(name) && this.desc.equals(desc);
		}
	}

	static Clas block_;
	static Clas blockFlowerPot_;
	static Clas minecraft_;
	static Clas guiButton_;
	static Clas guiOptions_;
	static Clas guiScreen_;
	static Clas modelRenderer_;
	static Clas worldClient_;
	static Clas effectRenderer_;
	static Clas entityRenderer_;
	static Clas glAllocation_;
	static Clas itemRenderer_;
	static Clas openGlHelper_;
	static Clas renderBlocks_;
	static Clas renderGlobal_;
	static Clas tessellator_;
	static Clas worldRenderer_;
	static Clas frustrum_;
	static Clas iCamera_;
	static Clas render_;
	static Clas renderDragon_;
	static Clas renderEnderman_;
	static Clas renderSpider_;
	static Clas renderManager_;
	static Clas rendererLivingE_;
	static Clas abstractTexture_;
	static Clas dynamicTexture_;
	static Clas iTextureObject_;
	static Clas layeredTexture_;
	static Clas simpleTexture_;
	static Clas stitcher_;
	static Clas textureAtlasSpri_;
	static Clas textureClock_;
	static Clas textureCompass_;
	static Clas textureManager_;
	static Clas textureMap_;
	static Clas textureUtil_;
	static Clas textureDownload_;
	static Clas iResource_;
	static Clas iResourceManager_;
	static Clas gameSettings_;
	static Clas tessVertexState_;
	static Clas entity_;
	static Clas entityLivingBase_;
	static Clas entityDragon_;
	static Clas entityEnderman_;
	static Clas entitySpider_;
	static Clas entityPlayer_;
	static Clas item_;
	static Clas itemBlock_;
	static Clas itemStack_;
	static Clas movingObjectPos_;
	static Clas resourceLocation_;
	static Clas vec3_;
	static Clas iBlockAccess_;
	static Clas world_;

	static Fiel entityLivingBase_deathTime;
	static Fiel entityLivingBase_hurtTime;
	static Fiel entityRenderer_cameraZoom;
	static Fiel entityRenderer_mc;
	static Fiel gameSettings_renderDistance;
	static Fiel guiButton_id;
	static Fiel guiScreen_buttonList;
	static Fiel guiScreen_width;
	static Fiel guiScreen_height;
	static Fiel guiScreen_mc;
	static Fiel guiOptions_options;
	static Fiel itemBlock_block;
	static Fiel itemRenderer_itemToRender;
	static Fiel layeredTexture_layeredTextureNames;
	static Fiel minecraft_renderGlobal;
	static Fiel minecraft_gameSettings;
	static Fiel modelRenderer_displayList;
	static Fiel modelRenderer_compiled;
	static Fiel renderGlobal_glSkyList;
	static Fiel renderGlobal_worldRenderers;
	static Fiel rendererLivingE_mainModel;
	static Fiel rendererLivingE_renderPassModel;
	static Fiel renderManager_entityRenderMap;
	static Fiel renderManager_instance;
	static Fiel simpleTexture_textureLocation;
	static Fiel tessellator_floatBuffer;
	static Fiel tessellator_shortBuffer;
	static Fiel tessellator_intBuffer;
	static Fiel tessellator_byteBuffer;
	static Fiel tessellator_rawBuffer;
	static Fiel tessellator_vertexCount;
	static Fiel tessellator_hasNormals;
	static Fiel textureAtlasSpri_width;
	static Fiel textureAtlasSpri_height;
	static Fiel textureAtlasSpri_border;
	static Fiel textureMap_anisotropic;
	static Fiel textureDownload_textureUploaded;
	static Fiel vec3_xCoord;

	static Fiel guiOptions_buttonList;
	static Fiel guiOptions_width;
	static Fiel guiOptions_height;
	static Fiel guiOptions_mc;

	static Meth abstractTexture_deleteGlTexture;
	static Meth block_getAoLight;
	static Meth block_getBlockFromItem;
	static Meth dynamicTexture_updateDynamicTexture;
	static Meth effectRenderer_renderLitParticles;
	static Meth effectRenderer_renderParticles;
	static Meth entity_getBrightness;
	static Meth entityRenderer_enableLightmap;
	static Meth entityRenderer_updateFogColor;
	static Meth entityRenderer_setupFog;
	static Meth entityRenderer_setFogColorBuffer;
	static Meth entityRenderer_renderWorld;
	static Meth entityRenderer_renderRainSnow;
	static Meth entityRenderer_renderHand;
	static Meth entityRenderer_setupCameraTransform;
	static Meth entityRenderer_disableLightmap;
	static Meth entityRenderer_renderCloudsCheck;
	static Meth gameSettings_saveOptions;
	static Meth gameSettings_shouldRenderClouds;
	static Meth glAllocation_createDirectByteBuffer;
	static Meth glAllocation_createDirectIntBuffer;
	static Meth glAllocation_deleteDisplayLists;
	static Meth guiOptions_actionPerformed;
	static Meth guiOptions_initGui;
	static Meth iCamera_setPosition;
	static Meth iResourceManager_getResource;
	static Meth itemRenderer_renderItem;
	static Meth itemRenderer_renderItemInFirstPerson;
	static Meth itemRenderer_renderOverlays;
	static Meth itemRenderer_updateEquipped;
	static Meth iTextureObject_loadTexture;
	static Meth iTextureObject_getGlTextureId;
	static Meth minecraft_displayGuiScreen;
	static Meth minecraft_startGame;
	static Meth openGlHelper_setActiveTexture;
	static Meth render_renderShadow;
	static Meth renderBlocks_renderBlockFluids;
	static Meth renderBlocks_renderStdBlockWithCM;
	static Meth renderBlocks_renderBlockSandFalling;
	static Meth renderBlocks_renderStdBlockWithAO;
	static Meth renderBlocks_renderBlockCactusImpl;
	static Meth renderBlocks_renderBlockDoor;
	static Meth renderBlocks_renderBlockBed;
	static Meth renderBlocks_renderBlockByRenderType;
	static Meth renderBlocks_renderBlockFlowerPot;
	static Meth renderBlocks_renderStdBlockWithAOP;
	static Meth renderBlocks_renderPistonExtension;
	static Meth renderDragon_shouldRenderPass;
	static Meth renderEnderman_shouldRenderPass;
	static Meth renderSpider_shouldRenderPass;
	static Meth rendererLivingE_doRender;
	static Meth rendererLivingE_renderEquippedItems;
	static Meth rendererLivingE_getColorMultiplier;
	static Meth rendererLivingE_renderLabel;
	static Meth renderGlobal_renderEntities;
	static Meth renderGlobal_renderSky;
	static Meth renderGlobal_drawBlockDamageTexture;
	static Meth renderGlobal_renderClouds;
	static Meth renderGlobal_sortAndRender;
	static Meth renderGlobal_clipRenderersByFrustum;
	static Meth renderGlobal_drawSelectionBox;
	static Meth renderGlobal_renderAllRenderLists;
	static Meth stitcher_getCurrentWidth;
	static Meth tessellator_sortQuad;
	static Meth tessellator_addTranslation;
	static Meth tessellator_setNormal;
	static Meth tessellator_addVertex;
	static Meth tessellator_reset;
	static Meth tessellator_draw;
	static Meth textureAtlasSpri_loadSprite;
	static Meth textureAtlasSpri_getFrameTextureData;
	static Meth textureAtlasSpri_getIconName;
	static Meth textureAtlasSpri_updateAnimation;
	static Meth textureCompass_updateCompass;
	static Meth textureManager_onResourceManagerReload;
	static Meth textureManager_bindTexture;
	static Meth textureMap_loadTextureAtlas;
	static Meth textureMap_getIconResLoc;
	static Meth textureMap_updateAnimations;
	static Meth textureUtil_uploadTexture;
	static Meth textureUtil_uploadTextureImageAllocate;
	static Meth textureUtil_allocateTexture;
	static Meth textureUtil_allocateTextureMipmapAniso;
	static Meth textureUtil_uploadTexSub;
	static Meth textureUtil_bindTexture;
	static Meth world_getCelestialAngle;
	static Meth world_getRainStrength;
	static Meth modelRenderer_render;
	static Meth modelRenderer_renderWithRotation;

	static Meth frustrum_setPosition;
	static Meth worldClient_getCelestialAngle;
	static Meth worldClient_getRainStrength;

	public static boolean equals(String clas1, String name1, String desc1, String clas2, String name2, String desc2) {
		return clas1.equals(clas2) && name1.equals(name2) && desc1.equals(desc2);
	}
}
