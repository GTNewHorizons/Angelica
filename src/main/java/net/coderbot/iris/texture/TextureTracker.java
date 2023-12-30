package net.coderbot.iris.texture;

import com.gtnewhorizons.angelica.glsm.GLStateManager;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.coderbot.iris.Iris;
import net.coderbot.iris.gl.IrisRenderSystem;
import net.coderbot.iris.gl.state.StateUpdateNotifiers;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.jetbrains.annotations.Nullable;

public class TextureTracker {
	public static final TextureTracker INSTANCE = new TextureTracker();

	private static Runnable bindTextureListener;

	static {
		StateUpdateNotifiers.bindTextureNotifier = listener -> bindTextureListener = listener;
	}

	private final Int2ObjectMap<AbstractTexture> textures = new Int2ObjectOpenHashMap<>();

	private boolean lockBindCallback;

	private TextureTracker() {
	}

	public void trackTexture(int id, AbstractTexture texture) {
		textures.put(id, texture);
	}

	@Nullable
	public AbstractTexture getTexture(int id) {
		return textures.get(id);
	}

	public void onBindTexture(int id) {
		if (lockBindCallback) {
			return;
		}
		if (GLStateManager.getActiveTextureUnit() == 0) {
			lockBindCallback = true;
			if (bindTextureListener != null) {
				bindTextureListener.run();
			}
			WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
			if (pipeline != null) {
				pipeline.onBindTexture(id);
			}
			// Reset texture state
			IrisRenderSystem.bindTextureToUnit(0, id);
			lockBindCallback = false;
		}
	}

	public void onDeleteTexture(int id) {
		textures.remove(id);
	}
}
