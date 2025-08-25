package net.coderbot.iris.sodium.vertex_format.entity_xhfp;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.*;

import net.coderbot.iris.vertices.IrisQuadView;

import java.nio.ByteBuffer;

public abstract class QuadViewEntity implements IrisQuadView {
	long writePointer;
	int stride;

	@Override
	public float x(int index) {
		return getFloat(writePointer - stride * (3L - index));
	}

	@Override
	public float y(int index) {
		return getFloat(writePointer + 4 - stride * (3L - index));
	}

	@Override
	public float z(int index) {
		return getFloat(writePointer + 8 - stride * (3L - index));
	}

	@Override
	public float u(int index) {
		return getFloat(writePointer + 16 - stride * (3L - index));
	}

	@Override
	public float v(int index) {
		return getFloat(writePointer + 20 - stride * (3L - index));
	}

	abstract float getFloat(long writePointer);

	public static class QuadViewEntityUnsafe extends QuadViewEntity {
		public void setup(long writePointer, int stride) {
			this.writePointer = writePointer;
			this.stride = stride;
		}

		@Override
		float getFloat(long writePointer) {
			return memGetFloat(writePointer);
		}
	}

	public static class QuadViewEntityNio extends QuadViewEntity {
		private ByteBuffer buffer;

		public void setup(ByteBuffer buffer, int writePointer, int stride) {
			this.buffer = buffer;
			this.writePointer = writePointer;
			this.stride = stride;
		}

		@Override
		float getFloat(long writePointer) {
			return buffer.getFloat((int) writePointer);
		}
	}
}
