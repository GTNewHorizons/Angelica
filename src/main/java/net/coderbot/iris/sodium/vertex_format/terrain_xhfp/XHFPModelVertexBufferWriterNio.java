package net.coderbot.iris.sodium.vertex_format.terrain_xhfp;

import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferView;
import me.jellysquid.mods.sodium.client.model.vertex.buffer.VertexBufferWriterNio;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexSink;
import me.jellysquid.mods.sodium.client.render.chunk.format.ModelVertexUtil;
import net.coderbot.iris.sodium.block_context.BlockContextHolder;
import net.coderbot.iris.sodium.block_context.ContextAwareVertexWriter;
import net.coderbot.iris.sodium.vertex_format.IrisModelVertexFormats;
import net.coderbot.iris.vertices.ExtendedDataHelper;
import net.coderbot.iris.vertices.NormalHelper;
import org.joml.Vector3f;

import java.nio.ByteBuffer;

import static net.coderbot.iris.sodium.vertex_format.terrain_xhfp.XHFPModelVertexType.STRIDE;

public class XHFPModelVertexBufferWriterNio extends VertexBufferWriterNio implements ModelVertexSink, ContextAwareVertexWriter {
	private final QuadViewTerrain.QuadViewTerrainNio quad = new QuadViewTerrain.QuadViewTerrainNio();
	private final Vector3f normal = new Vector3f();

	private BlockContextHolder contextHolder;

	private int vertexCount;
	private float uSum;
	private float vSum;

	public XHFPModelVertexBufferWriterNio(VertexBufferView backingBuffer) {
		super(backingBuffer, IrisModelVertexFormats.MODEL_VERTEX_XHFP);
	}

	@Override
	public void writeQuad(float x, float y, float z, int color, float u, float v, int light) {
		uSum += u;
		vSum += v;

		this.writeQuadInternal(
				ModelVertexUtil.denormalizeVertexPositionFloatAsShort(x),
				ModelVertexUtil.denormalizeVertexPositionFloatAsShort(y),
				ModelVertexUtil.denormalizeVertexPositionFloatAsShort(z),
				color,
				ModelVertexUtil.denormalizeVertexTextureFloatAsShort(u),
				ModelVertexUtil.denormalizeVertexTextureFloatAsShort(v),
				light,
				contextHolder.blockId,
				contextHolder.renderType,
				ExtendedDataHelper.computeMidBlock(x, y, z, contextHolder.localPosX, contextHolder.localPosY, contextHolder.localPosZ)
		);
	}

	private void writeQuadInternal(short x, short y, short z, int color, short u, short v, int light, short materialId,
								   short renderType, int packedMidBlock) {
		int i = this.writeOffset;

		vertexCount++;
		// NB: uSum and vSum must already be incremented outside of this function.

		ByteBuffer buffer = this.byteBuffer;
		buffer.putShort(i, x);
		buffer.putShort(i + 2, y);
		buffer.putShort(i + 4, z);
		buffer.putInt(i + 8, color);
		buffer.putShort(i + 12, u);
		buffer.putShort(i + 14, v);
		buffer.putShort(i + 16, (short) (light & 0xFFFF));
		buffer.putShort(i + 18, (short) (light >> 16 & 0xFFFF));
		// NB: We don't set midTexCoord, normal, and tangent here, they will be filled in later.
		// block ID: We only set the first 2 values, any legacy shaders using z or w will get filled in based on the GLSL spec
		// https://www.khronos.org/opengl/wiki/Vertex_Specification#Vertex_format
		// TODO: can we pack this into one short?
		buffer.putShort(i + 36, materialId);
		buffer.putShort(i + 38, renderType);
		buffer.putInt(i + 40, packedMidBlock);

		if (vertexCount == 4) {
			vertexCount = 0;

			// FIXME
			// The following logic is incorrect because OpenGL denormalizes shorts by dividing by 65535. The atlas is
			// based on power-of-two values and so a normalization factor that is not a power of two causes the values
			// used in the shader to be off by enough to cause visual errors. These are most noticeable on 1.18 with POM
			// on block edges.
			//
			// The only reliable way that this can be fixed is to apply the same shader transformations to midTexCoord
			// as Sodium does to the regular texture coordinates - dividing them by the correct power-of-two value inside
			// of the shader instead of letting OpenGL value normalization do the division. However, this requires
			// fragile patching that is not yet possible.
			//
			// As a temporary solution, the normalized shorts have been replaced with regular floats, but this takes up
			// an extra 4 bytes per vertex.

			// NB: Be careful with the math here! A previous bug was caused by midU going negative as a short, which
			// was sign-extended into midTexCoord, causing midV to have garbage (likely NaN data). If you're touching
			// this code, be aware of that, and don't introduce those kinds of bugs!
			//
			// Also note that OpenGL takes shorts in the range of [0, 65535] and transforms them linearly to [0.0, 1.0],
			// so multiply by 65535, not 65536.
			//
			// TODO: Does this introduce precision issues? Do we need to fall back to floats here? This might break
			// with high resolution texture packs.
//			int midU = (int)(65535.0F * Math.min(uSum * 0.25f, 1.0f)) & 0xFFFF;
//			int midV = (int)(65535.0F * Math.min(vSum * 0.25f, 1.0f)) & 0xFFFF;
//			int midTexCoord = (midV << 16) | midU;

			uSum *= 0.25f;
			vSum *= 0.25f;

			buffer.putFloat(i + 20, uSum);
			buffer.putFloat(i + 20 - STRIDE, uSum);
			buffer.putFloat(i + 20 - STRIDE * 2, uSum);
			buffer.putFloat(i + 20 - STRIDE * 3, uSum);

			buffer.putFloat(i + 24, vSum);
			buffer.putFloat(i + 24 - STRIDE, vSum);
			buffer.putFloat(i + 24 - STRIDE * 2, vSum);
			buffer.putFloat(i + 24 - STRIDE * 3, vSum);

			uSum = 0;
			vSum = 0;

			// normal computation
			// Implementation based on the algorithm found here:
			// https://github.com/IrisShaders/ShaderDoc/blob/master/vertex-format-extensions.md#surface-normal-vector

			quad.setup(buffer, i, STRIDE);
			NormalHelper.computeFaceNormal(normal, quad);
			int packedNormal = NormalHelper.packNormal(normal, 0.0f);

			buffer.putInt(i + 32, packedNormal);
			buffer.putInt(i + 32 - STRIDE, packedNormal);
			buffer.putInt(i + 32 - STRIDE * 2, packedNormal);
			buffer.putInt(i + 32 - STRIDE * 3, packedNormal);

			int tangent = NormalHelper.computeTangent(normal.x, normal.y, normal.z, quad);

			buffer.putInt(i + 28, tangent);
			buffer.putInt(i + 28 - STRIDE, tangent);
			buffer.putInt(i + 28 - STRIDE * 2, tangent);
			buffer.putInt(i + 28 - STRIDE * 3, tangent);
		}

		this.advance();
	}

	@Override
	public void iris$setContextHolder(BlockContextHolder holder) {
		this.contextHolder = holder;
	}
}
