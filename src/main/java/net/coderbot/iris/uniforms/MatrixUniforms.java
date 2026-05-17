package net.coderbot.iris.uniforms;

import com.gtnewhorizons.angelica.rendering.RenderingState;
import com.gtnewhorizons.angelica.stereo.StereoState;
import net.coderbot.iris.compat.dh.DHCompat;
import net.coderbot.iris.gl.uniform.UniformHolder;
import net.coderbot.iris.pipeline.ShadowRenderer;
import net.coderbot.iris.shaderpack.PackDirectives;
import net.coderbot.iris.shadow.ShadowMatrices;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.function.Supplier;

import static net.coderbot.iris.gl.uniform.UniformUpdateFrequency.PER_FRAME;

public final class MatrixUniforms {
	private MatrixUniforms() {
	}

	public static void addMatrixUniforms(UniformHolder uniforms, PackDirectives directives) {
		addMatrix(uniforms, "ModelView", RenderingState.INSTANCE::getModelViewMatrix);
		// TODO: In some cases, gbufferProjectionInverse takes on a value much different than OptiFine...
		// We need to audit Mojang's linear algebra.
		addMatrix(uniforms, "Projection", RenderingState.INSTANCE::getProjectionMatrix);
		addDHMatrix(uniforms, "Projection", DHCompat::getProjection);
		addShadowMatrix(uniforms, "ModelView", () -> ShadowRenderer.createShadowModelView(directives.getSunPathRotation(), directives.getShadowDirectives().getIntervalSize()).peek().getModel());
		addShadowMatrix(uniforms, "Projection", () -> ShadowMatrices.createOrthoMatrix(directives.getShadowDirectives().getDistance(), directives.getShadowDirectives().getNearPlane() < 0 ? -DHCompat.getRenderDistance() : directives.getShadowDirectives().getNearPlane(),
			directives.getShadowDirectives().getFarPlane() < 0 ? DHCompat.getRenderDistance() : directives.getShadowDirectives().getFarPlane()));
	}

	private static void addMatrix(UniformHolder uniforms, String name, Supplier<Matrix4fc> supplier) {
		uniforms
			.uniformMatrix(PER_FRAME, "gbuffer" + name, supplier)
			.uniformMatrix(PER_FRAME, "gbuffer" + name + "Inverse", new Inverted(supplier))
			.uniformMatrix(PER_FRAME, "gbufferPrevious" + name, new Previous(supplier));
	}

	private static void addDHMatrix(UniformHolder uniforms, String name, Supplier<Matrix4fc> supplier) {
		uniforms
			.uniformMatrix(PER_FRAME, "dh" + name, supplier)
			.uniformMatrix(PER_FRAME, "dh" + name + "Inverse", new Inverted(supplier))
			.uniformMatrix(PER_FRAME, "dhPrevious" + name, new Previous(supplier));
	}


	private static void addShadowMatrix(UniformHolder uniforms, String name, Supplier<Matrix4fc> supplier) {
		uniforms
				.uniformMatrix(PER_FRAME, "shadow" + name, supplier)
				.uniformMatrix(PER_FRAME, "shadow" + name + "Inverse", new Inverted(supplier));
	}

	private static class Inverted implements Supplier<Matrix4fc> {
		private final Supplier<Matrix4fc> parent;

		Inverted(Supplier<Matrix4fc> parent) {
			this.parent = parent;
		}

		@Override
		public Matrix4f get() {
			// PERF: Don't copy + allocate this matrix every time?
            final Matrix4f copy = new Matrix4f(parent.get());

			copy.invert();

			return copy;
		}
	}


	private static class Previous implements Supplier<Matrix4fc> {
		private final Supplier<Matrix4fc> parent;
		// Per-eye storage: LEFT/MONO uses slot 0, RIGHT uses slot 1. With one shared slot the LEFT
		// eye would read RIGHT eye N-1's matrix (wrong IPD offset, stale by a frame) and RIGHT eye
		// would read LEFT eye N's matrix (wrong IPD, same frame) — surfacing as eye-asymmetric
		// artifacts in any shader that reprojects against gbufferPrevious*.
		private final Matrix4f[] previousPerEye;

		Previous(Supplier<Matrix4fc> parent) {
			this.parent = parent;
			this.previousPerEye = new Matrix4f[] { new Matrix4f(), new Matrix4f() };
		}

		@Override
		public Matrix4f get() {
			// PERF: Don't copy + allocate these matrices every time?
			final int eye = StereoState.INSTANCE.currentEyeIndex();
			final Matrix4f copy = new Matrix4f(parent.get());
            final Matrix4f prev = new Matrix4f(this.previousPerEye[eye]);

			this.previousPerEye[eye] = copy;

			return prev;
		}
	}
}
