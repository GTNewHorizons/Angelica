package net.coderbot.iris.gl.framebuffer;

import net.coderbot.iris.rendertarget.ParityFlipState;
import org.jetbrains.annotations.Nullable;

public class ParityFramebuffer extends GlFramebuffer {

	private final ParityFlipState parity;
	@Nullable
	private GlFramebuffer odd;

	public ParityFramebuffer(ParityFlipState parity) {
		this.parity = parity;
	}

	public void setOdd(GlFramebuffer odd) {
		this.odd = odd;
	}

	@Nullable
	public GlFramebuffer getOdd() {
		return odd;
	}

	private boolean useOdd() {
		return odd != null && parity.isOdd();
	}

	@Override
	public void bind() {
		if (useOdd()) {
			odd.bind();
		} else {
			super.bind();
		}
	}

	@Override
	public void bindAsReadBuffer() {
		if (useOdd()) {
			odd.bindAsReadBuffer();
		} else {
			super.bindAsReadBuffer();
		}
	}

	@Override
	public void bindAsDrawBuffer() {
		if (useOdd()) {
			odd.bindAsDrawBuffer();
		} else {
			super.bindAsDrawBuffer();
		}
	}

	@Override
	public int getId() {
		return useOdd() ? odd.getId() : super.getId();
	}

	@Override
	public int getColorAttachment(int index) {
		return useOdd() ? odd.getColorAttachment(index) : super.getColorAttachment(index);
	}

	@Override
	public void addDepthAttachment(int texture) {
		super.addDepthAttachment(texture);
		if (odd != null) {
			odd.addDepthAttachment(texture);
		}
	}

	@Override
	public void addDepthAttachmentBypass(int texture) {
		super.addDepthAttachmentBypass(texture);
		if (odd != null) {
			odd.addDepthAttachmentBypass(texture);
		}
	}

	@Override
	protected void destroyInternal() {
		super.destroyInternal();
		if (odd != null) {
			odd.destroy();
			odd = null;
		}
	}
}
