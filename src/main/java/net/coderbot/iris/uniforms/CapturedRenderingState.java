package net.coderbot.iris.uniforms;

import lombok.Getter;
import lombok.Setter;
import net.coderbot.iris.gl.state.ValueUpdateNotifier;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector4d;
import org.joml.Vector4i;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import java.nio.FloatBuffer;

public class CapturedRenderingState {
	public static final CapturedRenderingState INSTANCE = new CapturedRenderingState();

	private static final Vector3d ZERO_VECTOR_3d = new Vector3d();

    private final FloatBuffer modelViewBuffer = BufferUtils.createFloatBuffer(16);
	private Matrix4f gbufferModelView;
    private final FloatBuffer shadowModelViewBuffer = BufferUtils.createFloatBuffer(16);
    private Matrix4f shadowModelView;
    private final FloatBuffer projectionBuffer = BufferUtils.createFloatBuffer(16);
    private Matrix4f gbufferProjection;
    private final FloatBuffer shadowProjectionBuffer = BufferUtils.createFloatBuffer(16);
    private Matrix4f shadowProjection;
	private Vector3d fogColor;
    private Vector4d clearColor;

    @Getter
    private Vector3d cameraPosition = new Vector3d();

    @Getter
    @Setter
    private boolean blendEnabled;
    private Vector4i blendFunc;
	private float tickDelta;
	private int currentRenderedBlockEntity;
	private Runnable blockEntityIdListener = null;

	private int currentRenderedEntity = -1;
	private Runnable entityIdListener = null;

	private CapturedRenderingState() {
	}

	public Matrix4f getGbufferModelView() {
		return gbufferModelView;
	}

	public Matrix4f getGbufferProjection() {
		return gbufferProjection;
	}

	public Vector3d getFogColor() {
		if (Minecraft.getMinecraft().theWorld == null || fogColor == null) {
			return ZERO_VECTOR_3d;
		}

		return fogColor;
	}

	public void setFogColor(float red, float green, float blue) {
		fogColor = new Vector3d(red, green, blue);
	}

    public void setClearColor(float red, float green, float blue, float alpha) {
        clearColor = new Vector4d(red, green, blue, alpha);
    }

	public void setTickDelta(float tickDelta) {
		this.tickDelta = tickDelta;
	}

	public float getTickDelta() {
		return tickDelta;
	}

	public void setCurrentBlockEntity(int entity) {
		this.currentRenderedBlockEntity = entity;

		if (this.blockEntityIdListener != null) {
			this.blockEntityIdListener.run();
		}
	}

	public int getCurrentRenderedBlockEntity() {
		return currentRenderedBlockEntity;
	}

	public void setCurrentEntity(int entity) {
		this.currentRenderedEntity = entity;

		if (this.entityIdListener != null) {
			this.entityIdListener.run();
		}
	}

	public ValueUpdateNotifier getEntityIdNotifier() {
		return listener -> this.entityIdListener = listener;
	}

	public ValueUpdateNotifier getBlockEntityIdNotifier() {
		return listener -> this.blockEntityIdListener = listener;
	}

	public int getCurrentRenderedEntity() {
		return currentRenderedEntity;
	}

    public void setBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        blendFunc = new Vector4i(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    public Vector4i getBlendFunc() {
        return blendFunc;
    }

    public void setCamera(float tickDelta) {
        final Minecraft mc = Minecraft.getMinecraft();
        final EntityLivingBase viewEntity = mc.renderViewEntity;

        final double x = viewEntity.lastTickPosX + (viewEntity.posX - viewEntity.lastTickPosX) * tickDelta;
        final double y = viewEntity.lastTickPosY + (viewEntity.posY - viewEntity.lastTickPosY) * tickDelta;
        final double z = viewEntity.lastTickPosZ + (viewEntity.posZ - viewEntity.lastTickPosZ) * tickDelta;
        cameraPosition = new Vector3d(x, y, z);

        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, (FloatBuffer) projectionBuffer.position(0));
        gbufferProjection = new Matrix4f((FloatBuffer)projectionBuffer.position(0));

        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, (FloatBuffer) modelViewBuffer.position(0));
        gbufferModelView = new Matrix4f((FloatBuffer)modelViewBuffer.position(0));
     }

     public void setCameraShadow(float tickDelta) {
        final Minecraft mc = Minecraft.getMinecraft();
        setCamera(tickDelta);

        GL11.glGetFloat(GL11.GL_PROJECTION_MATRIX, (FloatBuffer) shadowProjectionBuffer.position(0));
        shadowProjection = new Matrix4f((FloatBuffer)shadowProjectionBuffer.position(0));

        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, (FloatBuffer) shadowModelViewBuffer.position(0));
        shadowModelView = new Matrix4f((FloatBuffer)shadowModelViewBuffer.position(0));


     }
}
