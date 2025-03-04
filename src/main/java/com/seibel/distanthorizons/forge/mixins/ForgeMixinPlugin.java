package com.seibel.distanthorizons.forge.mixins;

import cpw.mods.fml.common.Loader;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * @author coolGi
 * @author cortex
 */
public class ForgeMixinPlugin implements IMixinConfigPlugin
{
	private boolean firstRun = false;
	private boolean isForgeMixinFile;


	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName)
	{
		if (!this.firstRun) {
			try {
				Class<?> cls = Class.forName("net.neoforged.fml.common.Mod"); // Check if a NeoForge exclusive class exists
				this.isForgeMixinFile = false;
			} catch (ClassNotFoundException e) {
				this.isForgeMixinFile = true;
			}
		}
		if (!this.isForgeMixinFile)
			return false;

		if (mixinClassName.contains(".mods."))
		{ // If the mixin wants to go into a mod then we check if that mod is loaded or not
			return Loader.isModLoaded(
					mixinClassName
							// What these 2 regex's do is get the mod name that we are checking out of the mixinClassName
							// Eg. "com.seibel.distanthorizons.mixins.mods.sodium.MixinSodiumChunkRenderer" turns into "sodium"
							.replaceAll("^.*mods.", "") // Replaces everything before the mods
							.replaceAll("\\..*$", "") // Replaces everything after the mod name
			);
		}

		return true;
	}


	@Override
	public void onLoad(String mixinPackage) { }

	@Override
	public String getRefMapperConfig() { return null; }

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) { }

	@Override
	public List<String> getMixins() { return null; }

    @Override
    public void preApply(String s, org.spongepowered.asm.lib.tree.ClassNode classNode, String s1, IMixinInfo iMixinInfo) {
    }

    @Override
    public void postApply(String s, org.spongepowered.asm.lib.tree.ClassNode classNode, String s1, IMixinInfo iMixinInfo) {
    }
}
