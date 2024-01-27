package net.coderbot.iris.gl.uniform;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import net.minecraft.util.Vec3;

import java.util.function.Supplier;

public class VanillaVector3Uniform extends Uniform {
	private final Vec3 cachedValue;
	private final Supplier<Vec3> value;

	VanillaVector3Uniform(int location, Supplier<Vec3> value) {
		super(location);

		this.cachedValue = Vec3.createVectorHelper(0, 0, 0);
		this.value = value;
	}

	@Override
	public void update() {
        Vec3 newValue = value.get();

		if (!newValue.equals(cachedValue)) {
            cachedValue.xCoord = newValue.xCoord;
            cachedValue.yCoord = newValue.yCoord;
            cachedValue.zCoord = newValue.zCoord;
			RenderSystem.uniform3f(location, (float)cachedValue.xCoord, (float)cachedValue.yCoord, (float)cachedValue.zCoord);
		}
	}
}
