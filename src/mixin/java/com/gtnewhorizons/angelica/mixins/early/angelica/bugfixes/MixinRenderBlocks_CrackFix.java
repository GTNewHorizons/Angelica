/*
 * This file is part of FalseTweaks.
 *
 * Copyright (C) 2022-2025 FalsePattern
 * All Rights Reserved
 *
 * Modifications by Angelica in accordance with LGPL v3.0
 * 
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * FalseTweaks is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, only version 3 of the License.
 *
 * FalseTweaks is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with FalseTweaks. If not, see <https://www.gnu.org/licenses/>.
 */

package com.gtnewhorizons.angelica.mixins.early.angelica.bugfixes;

import com.gtnewhorizons.angelica.config.AngelicaConfig;
import com.gtnewhorizons.angelica.loading.AngelicaTweaker;
import me.jellysquid.mods.sodium.client.render.chunk.ChunkRenderManager;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.util.IIcon;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.common.util.ForgeDirection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.Objects;
@Mixin(RenderBlocks.class)
public class MixinRenderBlocks_CrackFix {
	@Unique
	private static String[] angelica$currentCrackFixBlacklistArr;
	@Unique
	private static Class<?>[] angelica$currentCrackFixBlacklistClasses;
	@Shadow
	public double renderMinX;
	@Shadow
	public double renderMinY;
	@Shadow
	public double renderMinZ;
	@Shadow
	public double renderMaxX;
	@Shadow
	public double renderMaxY;
	@Shadow
	public double renderMaxZ;
	@Unique
	private double[] angelica$bounds;
	@Unique
	private boolean angelica$disableCrackFix;
	
	@Redirect(method = "renderFaceXNeg",
			at = @At(value = "FIELD",
					target = "Lnet/minecraft/client/renderer/RenderBlocks;renderMinX:D",
					ordinal = 0),
			require = 1)
	private double xNegBounds(RenderBlocks instance) {
		angelica$preBounds(ForgeDirection.WEST);
		return instance.renderMinX;
	}
	
	@Redirect(method = "renderFaceXPos",
			at = @At(value = "FIELD",
					target = "Lnet/minecraft/client/renderer/RenderBlocks;renderMaxX:D",
					ordinal = 0),
			require = 1)
	private double xPosBounds(RenderBlocks instance) {
		angelica$preBounds(ForgeDirection.EAST);
		return instance.renderMaxX;
	}
	@SuppressWarnings("SuspiciousNameCombination")
	@Redirect(method = "renderFaceYNeg",
			at = @At(value = "FIELD",
					target = "Lnet/minecraft/client/renderer/RenderBlocks;renderMinX:D",
					ordinal = 5),
			require = 1)
	private double yNegBounds(RenderBlocks instance) {
		angelica$preBounds(ForgeDirection.DOWN);
		return instance.renderMinX;
	}
	@SuppressWarnings("SuspiciousNameCombination")
	@Redirect(method = "renderFaceYPos",
			at = @At(value = "FIELD",
					target = "Lnet/minecraft/client/renderer/RenderBlocks;renderMinX:D",
					ordinal = 5),
			require = 1)
	private double yPosBounds(RenderBlocks instance) {
		angelica$preBounds(ForgeDirection.UP);
		return instance.renderMinX;
	}
	
	@Redirect(method = "renderFaceZNeg",
			at = @At(value = "FIELD",
					target = "Lnet/minecraft/client/renderer/RenderBlocks;renderMinX:D",
					ordinal = 6),
			require = 1)
	private double zNegBounds(RenderBlocks instance) {
		angelica$preBounds(ForgeDirection.NORTH);
		return instance.renderMinX;
	}
	@Redirect(method = "renderFaceZPos",
			at = @At(value = "FIELD",
					target = "Lnet/minecraft/client/renderer/RenderBlocks;renderMinX:D",
					ordinal = 5),
			require = 1)
	private double zPosBounds(RenderBlocks instance) {
		angelica$preBounds(ForgeDirection.SOUTH);
		return instance.renderMinX;
	}
	@Unique
	private void angelica$preBounds(ForgeDirection skipDir) {
		if (angelica$crackFixOff()) {
			return;
		}
		if (angelica$bounds == null) {
			angelica$bounds = new double[6];
		}
		angelica$bounds[0] = renderMinX;
		angelica$bounds[1] = renderMinY;
		angelica$bounds[2] = renderMinZ;
		angelica$bounds[3] = renderMaxX;
		angelica$bounds[4] = renderMaxY;
		angelica$bounds[5] = renderMaxZ;
		
		if (ForgeHooksClient.getWorldRenderPass() != 0) {
			return;
		}
		
		if (renderMinX != 0 || renderMinY != 0 || renderMinZ != 0 || renderMaxX != 1 || renderMaxY != 1 || renderMaxZ != 1) {
			return;
		}
		double EPSILON = AngelicaConfig.blockCrackFixEpsilon;
		renderMinX -= EPSILON;
		renderMinY -= EPSILON;
		renderMinZ -= EPSILON;
		renderMaxX += EPSILON;
		renderMaxY += EPSILON;
		renderMaxZ += EPSILON;
		switch (skipDir) {
			case WEST: renderMinX = angelica$bounds[0]; break;
			case DOWN: renderMinY = angelica$bounds[1]; break;
			case NORTH: renderMinZ = angelica$bounds[2]; break;
			case EAST: renderMaxX = angelica$bounds[3]; break;
			case UP: renderMaxY = angelica$bounds[4]; break;
			case SOUTH: renderMaxZ = angelica$bounds[5]; break;
		}
	}
	@Inject(method = {"renderFaceXNeg", "renderFaceXPos", "renderFaceYNeg", "renderFaceYPos", "renderFaceZNeg", "renderFaceZPos"},
			at = @At(value = "RETURN"),
			require = 6)
	private void postBounds(Block p_147798_1_, double p_147798_2_, double p_147798_4_, double p_147798_6_, IIcon p_147798_8_, CallbackInfo ci) {
		if (angelica$crackFixOff() || angelica$bounds == null) {
			return;
		}
		renderMinX = angelica$bounds[0];
		renderMinY = angelica$bounds[1];
		renderMinZ = angelica$bounds[2];
		renderMaxX = angelica$bounds[3];
		renderMaxY = angelica$bounds[4];
		renderMaxZ = angelica$bounds[5];
	}
	@Inject(method = "renderBlockByRenderType",
			at = @At("HEAD"),
			require = 1)
	private void exclusion(Block block, int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
		angelica$disableCrackFix = angelica$isBlacklisted(block.getClass());
	}
	
	@Inject(method = "renderBlockByRenderType",
			at = @At("RETURN"),
			require = 1)
	private void endExclusion(Block p_147805_1_, int p_147805_2_, int p_147805_3_, int p_147805_4_, CallbackInfoReturnable<Boolean> cir) {
		angelica$disableCrackFix = false;
	}
	
	@Unique
	private static boolean angelica$isBlacklisted(Class<?> clazz) {
		Class<?>[] blacklist = angelica$getCrackFixBlacklist();
		if (blacklist == null) {
			return false;
		}
		for (Class<?> element : blacklist) {
			if (element.isAssignableFrom(clazz)) {
				return true;
			}
		}
		return false;
	}
	
	@Unique
	private static Class<?>[] angelica$getCrackFixBlacklist() {
		if (angelica$currentCrackFixBlacklistArr != AngelicaConfig.blockCrackFixBlacklist) {
			angelica$currentCrackFixBlacklistArr = AngelicaConfig.blockCrackFixBlacklist;
			angelica$currentCrackFixBlacklistClasses = Arrays.stream(angelica$currentCrackFixBlacklistArr).map((name) -> {
				try {
					return Class.forName(name);
				} catch (ClassNotFoundException e) {
					AngelicaTweaker.LOGGER.info("Could not find class " + name + " for crack fix blacklist!");
					return null;
				}
			}).filter(Objects::nonNull).toArray(Class<?>[]::new);
		}
		return angelica$currentCrackFixBlacklistClasses;
	}
	@Unique
	private boolean angelica$crackFixOff() {
		return !AngelicaConfig.fixBlockCrack || angelica$disableCrackFix;
	}
}
