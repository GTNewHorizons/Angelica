package net.coderbot.iris.gl.buffer;

import com.gtnewhorizons.angelica.glsm.RenderSystem;
import org.embeddedt.embeddium.impl.gl.debug.GLDebug;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL43;

public class ShaderStorageBuffer {
	protected final int index;
	protected final ShaderStorageInfo info;
	protected int id;

	public ShaderStorageBuffer(int index, ShaderStorageInfo info) {
		this.id = RenderSystem.createBuffers();
		GLDebug.nameObject(GL43.GL_BUFFER, id, "SSBO " + index);
		this.index = index;
		this.info = info;
	}

	public final int getIndex() {
		return index;
	}

	public final long getSize() {
		return info.size();
	}

	protected void destroy() {
		RenderSystem.bindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, index, 0);
		RenderSystem.deleteBuffers(id);
	}

	public void bind() {
		RenderSystem.bindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, index, id);
	}

	public void resizeIfRelative(int width, int height) {
		if (!info.relative()) return;

		RenderSystem.deleteBuffers(id);
		int newId = GL15.glGenBuffers();
		GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, newId);

		// Calculation time
		int newWidth = (int) (width * info.scaleX());
		int newHeight = (int) (height * info.scaleY());
		int finalSize = (newHeight * newWidth) * info.size();
		RenderSystem.bufferStorage(GL43.GL_SHADER_STORAGE_BUFFER, finalSize, 0);
		RenderSystem.clearBufferSubData(GL43.GL_SHADER_STORAGE_BUFFER, GL30.GL_R8, 0, finalSize, GL11.GL_RED, GL11.GL_BYTE, new int[]{0});
		RenderSystem.bindBufferBase(GL43.GL_SHADER_STORAGE_BUFFER, index, newId);
		id = newId;
	}

	public int getId() {
		return id;
	}
}
