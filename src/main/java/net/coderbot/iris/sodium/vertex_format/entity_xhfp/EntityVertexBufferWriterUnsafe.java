package net.coderbot.iris.sodium.vertex_format.entity_xhfp;

import com.gtnewhorizons.angelica.compat.lwjgl.CompatMemoryUtil;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferView;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferWriterUnsafe;
import me.jellysquid.mods.sodium.client.model.vertex.formats.glyph.GlyphVertexSink;
import me.jellysquid.mods.sodium.client.model.vertex.formats.quad.QuadVertexSink;
import me.jellysquid.mods.sodium.client.util.Norm3b;
import net.coderbot.iris.vertices.IrisVertexFormats;
import net.coderbot.iris.vertices.NormalHelper;
import org.joml.Vector3f;

public class EntityVertexBufferWriterUnsafe extends VertexBufferWriterUnsafe implements QuadVertexSink, GlyphVertexSink {
	private static final int STRIDE = IrisVertexFormats.ENTITY.getVertexSize();

	private final QuadViewEntity.QuadViewEntityUnsafe quad = new QuadViewEntity.QuadViewEntityUnsafe();
	private final Vector3f saveNormal = new Vector3f();

	private int vertexCount;
	private float uSum;
	private float vSum;

	public EntityVertexBufferWriterUnsafe(VertexBufferView backingBuffer) {
		super(backingBuffer, ExtendedQuadVertexType.INSTANCE);
	}

	@Override
	public void writeQuad(float x, float y, float z, int color, float u, float v, int light, int overlay, int normal) {
		long i = this.writePointer;

		vertexCount++;
		uSum += u;
		vSum += v;

		CompatMemoryUtil.memPutFloat(i, x);
		CompatMemoryUtil.memPutFloat(i + 4, y);
		CompatMemoryUtil.memPutFloat(i + 8, z);
		CompatMemoryUtil.memPutInt(i + 12, color);
		CompatMemoryUtil.memPutFloat(i + 16, u);
		CompatMemoryUtil.memPutFloat(i + 20, v);
		CompatMemoryUtil.memPutInt(i + 24, overlay);
		CompatMemoryUtil.memPutInt(i + 28, light);

		if (vertexCount == 4) {
			this.endQuad(normal);
		}

		this.advance();
	}

	@Override
	public void writeGlyph(float x, float y, float z, int color, float u, float v, int light) {
		writeQuad(x, y, z, color, u, v, light, 0, 0);
	}

	private void endQuad(int normal) {
		this.vertexCount = 0;

		long i = this.writePointer;

		uSum *= 0.25;
		vSum *= 0.25;

		quad.setup(writePointer, STRIDE);

		float normalX, normalY, normalZ;

		if (normal == 0) {
			NormalHelper.computeFaceNormal(saveNormal, quad);
			normalX = saveNormal.x;
			normalY = saveNormal.y;
			normalZ = saveNormal.z;
			normal = NormalHelper.packNormal(saveNormal, 0.0F);
		} else {
			normalX = Norm3b.unpackX(normal);
			normalY = Norm3b.unpackY(normal);
			normalZ = Norm3b.unpackZ(normal);
		}

		int tangent = NormalHelper.computeTangent(normalX, normalY, normalZ, quad);

		for (long vertex = 0; vertex < 4; vertex++) {
			CompatMemoryUtil.memPutFloat(i + 36 - STRIDE * vertex, uSum);
			CompatMemoryUtil.memPutFloat(i + 40 - STRIDE * vertex, vSum);
			CompatMemoryUtil.memPutInt(i + 32 - STRIDE * vertex, normal);
			CompatMemoryUtil.memPutInt(i + 44 - STRIDE * vertex, tangent);
		}

		uSum = 0;
		vSum = 0;
	}
}
