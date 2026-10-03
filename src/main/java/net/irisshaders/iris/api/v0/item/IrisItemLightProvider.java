package net.irisshaders.iris.api.v0.item;

import com.gtnewhorizons.angelica.helpers.ItemBlockLight;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import org.joml.Vector3f;

public interface IrisItemLightProvider {

	Vector3f DEFAULT_LIGHT_COLOR = new Vector3f(1, 1, 1);

	default int getLightEmission(EntityPlayer player, ItemStack stack) {
		if (stack.getItem() instanceof ItemBlock item) {
			return ItemBlockLight.getLightValue(item, stack);
		}

		return 0;
	}

	default Vector3f getLightColor(EntityPlayer player, ItemStack stack) {
		return DEFAULT_LIGHT_COLOR;
	}
}
