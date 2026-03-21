package net.coderbot.iris.gl.framebuffer;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryStack.*;

import com.gtnewhorizon.gtnhlib.bytebuf.MemoryStack;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import com.gtnewhorizons.angelica.glsm.texture.TextureInfoCache;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import java.nio.IntBuffer;
import net.coderbot.iris.gl.GlResource;
import net.coderbot.iris.gl.texture.DepthBufferFormat;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

public class GlFramebuffer extends GlResource {
	private final Int2IntMap attachments;
	private final int maxDrawBuffers;
	private final int maxColorAttachments;
	private boolean hasDepthAttachment;

	public GlFramebuffer() {
		super(RenderSystem.createFramebuffer());

		this.attachments = new Int2IntArrayMap();
		this.maxDrawBuffers = GLStateManager.glGetInteger(GL20.GL_MAX_DRAW_BUFFERS);
		this.maxColorAttachments = GLStateManager.glGetInteger(GL30.GL_MAX_COLOR_ATTACHMENTS);
		this.hasDepthAttachment = false;
	}

	public void addDepthAttachment(int texture) {
		final int internalFormat = TextureInfoCache.INSTANCE.getInfo(texture).getInternalFormat();
        final DepthBufferFormat depthBufferFormat = DepthBufferFormat.fromGlEnumOrDefault(internalFormat);

        final int fb = getGlId();

		if (depthBufferFormat.isCombinedStencil()) {
			RenderSystem.framebufferTexture2D(fb, GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_STENCIL_ATTACHMENT, GL11.GL_TEXTURE_2D, texture, 0);
		} else {
			RenderSystem.framebufferTexture2D(fb, GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, texture, 0);
		}

		this.hasDepthAttachment = true;
	}

	public void addDepthAttachmentBypass(int texture) {
        final int fb = getGlId();

		RenderSystem.framebufferTexture2D(fb, GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, texture, 0);

		this.hasDepthAttachment = true;
	}

	public void addColorAttachment(int index, int texture) {
        final int fb = getGlId();

		RenderSystem.framebufferTexture2D(fb, GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0 + index, GL11.GL_TEXTURE_2D, texture, 0);
		attachments.put(index, texture);
	}

	public void noDrawBuffers() {
        try (MemoryStack stack = stackPush()) {
            final IntBuffer buffer = stack.mallocInt(1);
            buffer.put(GL11.GL_NONE);
            RenderSystem.drawBuffers(getGlId(), buffer);
        }
	}

	public void drawBuffers(int[] buffers) {
        try (MemoryStack stack = stackPush()) {
            final IntBuffer glBuffers = stack.mallocInt(buffers.length);
            int index = 0;

            if (buffers.length > maxDrawBuffers) {
                throw new IllegalArgumentException("Cannot write to more than " + maxDrawBuffers + " draw buffers on this GPU");
            }
            for (int buffer : buffers) {
                if (buffer >= maxColorAttachments) {
                    throw new IllegalArgumentException("Only " + maxColorAttachments + " color attachments are supported on this GPU, but an attempt was made to write to a color attachment with index " + buffer);
                }
                glBuffers.put(index++, GL30.GL_COLOR_ATTACHMENT0 + buffer);
            }
            RenderSystem.drawBuffers(getGlId(), glBuffers);
        }
	}

	public void readBuffer(int buffer) {
		RenderSystem.readBuffer(getGlId(), GL30.GL_COLOR_ATTACHMENT0 + buffer);
	}

	public int getColorAttachment(int index) {
		return attachments.get(index);
	}

	public boolean hasDepthAttachment() {
		return hasDepthAttachment;
	}

	public void bind() {
		GLStateManager.glBindFramebuffer(GL30.GL_FRAMEBUFFER, getGlId());
	}

	public void bindAsReadBuffer() {
		GLStateManager.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, getGlId());
	}

	public void bindAsDrawBuffer() {
		GLStateManager.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, getGlId());
	}

	@Override
    protected void destroyInternal() {
		GLStateManager.glDeleteFramebuffers(getGlId());
	}

	public boolean isComplete() {
		bind();

        return GLStateManager.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) == GL30.GL_FRAMEBUFFER_COMPLETE;
	}

	public int getId() {
		return getGlId();
	}
}
