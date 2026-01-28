package net.coderbot.iris.pipeline;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import net.coderbot.iris.gl.framebuffer.GlFramebuffer;
import net.minecraft.client.Minecraft;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

import java.util.Objects;
import java.util.function.IntSupplier;

public class ClearPass {
	private final Vector4f color;
	private final IntSupplier viewportX;
	private final IntSupplier viewportY;
	private final GlFramebuffer framebuffer;
	private final int clearFlags;

	public ClearPass(Vector4f color, IntSupplier viewportX, IntSupplier viewportY, GlFramebuffer framebuffer, int clearFlags) {
		this.color = color;
		this.viewportX = viewportX;
		this.viewportY = viewportY;
		this.framebuffer = framebuffer;
		this.clearFlags = clearFlags;
	}

	public void execute(Vector4f defaultClearColor) {
		GLStateManager.glViewport(0, 0, viewportX.getAsInt(), viewportY.getAsInt());
		framebuffer.bind();

		Vector4f color = Objects.requireNonNull(defaultClearColor);

		if (this.color != null) {
			color = this.color;
		}

		GL11.glClearColor(color.x, color.y, color.z, color.w);
        GL11.glClear(clearFlags);
        if (Minecraft.isRunningOnMac) {
            GL11.glGetError();
        }
	}

	public GlFramebuffer getFramebuffer() {
		return framebuffer;
	}
}
