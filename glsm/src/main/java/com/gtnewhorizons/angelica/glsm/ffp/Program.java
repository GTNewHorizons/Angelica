package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.backend.RenderBackend;
import lombok.Getter;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL32;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;
import org.lwjgl.opengl.KHRDebug;

import java.util.concurrent.atomic.AtomicInteger;

import static com.gtnewhorizons.angelica.glsm.backend.BackendManager.RENDER_BACKEND;

/**
 * A compiled and linked FFP emulation program (vertex + fragment + optional geometry shaders). Owns the GL program handle and caches uniform locations.
 */
public class Program {

    private static final AtomicInteger PROGRAM_COUNTER = new AtomicInteger();

    @Getter private final int programId;
    @Getter private final VertexKey vertexKey;
    @Getter private final FragmentKey fragmentKey;

    // Uniform locations (-1 = not present in this variant)
    // Matrices
    public int locModelViewMatrix = -1;
    public int locProjectionMatrix = -1;
    public int locMVPMatrix = -1;
    public int locNormalMatrix = -1;
    public int locTextureMatrix0 = -1;

    // Current state defaults
    public int locCurrentNormal = -1;
    public int locCurrentColor = -1;
    public int locCurrentTexCoord = -1;
    public int locCurrentLightmapCoord = -1;
    public int locLightmapTextureMatrix = -1;

    // Lighting - color material path (raw values)
    public int locLightModelAmbient = -1;
    public int locMaterialEmission = -1;
    public int locMaterialAmbient = -1;
    public int locMaterialDiffuse = -1;
    public int locMaterialSpecular = -1;
    public int locMaterialShininess = -1;
    public int locLight0Ambient = -1;
    public int locLight0Diffuse = -1;
    public int locLight0Specular = -1;
    public int locLight1Ambient = -1;
    public int locLight1Diffuse = -1;
    public int locLight1Specular = -1;

    // Lighting - pre-computed path
    public int locSceneColor = -1;
    public int locLightProd0Ambient = -1;
    public int locLightProd0Diffuse = -1;
    public int locLightProd0Specular = -1;
    public int locLightProd1Ambient = -1;
    public int locLightProd1Diffuse = -1;
    public int locLightProd1Specular = -1;

    // Light positions
    public int locLight0Position = -1;
    public int locLight1Position = -1;

    // Normal scale (for rescale normals)
    public int locNormalScale = -1;

    // TexGen plane uniforms
    public int locTexGenObjPlaneS = -1;
    public int locTexGenObjPlaneT = -1;
    public int locTexGenObjPlaneR = -1;
    public int locTexGenObjPlaneQ = -1;
    public int locTexGenEyePlaneS = -1;
    public int locTexGenEyePlaneT = -1;
    public int locTexGenEyePlaneR = -1;
    public int locTexGenEyePlaneQ = -1;

    // Clip planes
    public int locClipPlanes = -1;

    // Wide line emulation (geometry shader)
    public int locViewportSize = -1;
    public int locLineWidth = -1;

    // Fragment uniforms
    public final int[] locSampler = { -1, -1, -1, -1 };
    public int locAlphaRef = -1;
    public final int[] locTexEnvColor = { -1, -1, -1, -1 };
    public int locFogParams = -1;
    public int locFogColor = -1;

    Program(int programId, VertexKey vertexKey, FragmentKey fragmentKey) {
        this.programId = programId;
        this.vertexKey = vertexKey;
        this.fragmentKey = fragmentKey;
        resolveLocations();
    }

    private void resolveLocations() {
        locModelViewMatrix = loc("u_ModelViewMatrix");
        locProjectionMatrix = loc("u_ProjectionMatrix");
        locMVPMatrix = loc("u_MVPMatrix");
        locNormalMatrix = loc("u_NormalMatrix");
        locTextureMatrix0 = loc("u_TextureMatrix0");

        locCurrentNormal = loc("u_CurrentNormal");
        locCurrentColor = loc("u_CurrentColor");
        locCurrentTexCoord = loc("u_CurrentTexCoord");
        locCurrentLightmapCoord = loc("u_CurrentLightmapCoord");
        locLightmapTextureMatrix = loc("u_LightmapTextureMatrix");

        // Color material path
        locLightModelAmbient = loc("u_LightModelAmbient");
        locMaterialEmission = loc("u_MaterialEmission");
        locMaterialAmbient = loc("u_MaterialAmbient");
        locMaterialDiffuse = loc("u_MaterialDiffuse");
        locMaterialSpecular = loc("u_MaterialSpecular");
        locMaterialShininess = loc("u_MaterialShininess");
        locLight0Ambient = loc("u_Light0Ambient");
        locLight0Diffuse = loc("u_Light0Diffuse");
        locLight0Specular = loc("u_Light0Specular");
        locLight1Ambient = loc("u_Light1Ambient");
        locLight1Diffuse = loc("u_Light1Diffuse");
        locLight1Specular = loc("u_Light1Specular");

        // Pre-computed path
        locSceneColor = loc("u_SceneColor");
        locLightProd0Ambient = loc("u_LightProd0Ambient");
        locLightProd0Diffuse = loc("u_LightProd0Diffuse");
        locLightProd0Specular = loc("u_LightProd0Specular");
        locLightProd1Ambient = loc("u_LightProd1Ambient");
        locLightProd1Diffuse = loc("u_LightProd1Diffuse");
        locLightProd1Specular = loc("u_LightProd1Specular");

        // Light positions
        locLight0Position = loc("u_Light0Position");
        locLight1Position = loc("u_Light1Position");

        locNormalScale = loc("u_NormalScale");

        // TexGen
        locTexGenObjPlaneS = loc("u_TexGenObjPlaneS");
        locTexGenObjPlaneT = loc("u_TexGenObjPlaneT");
        locTexGenObjPlaneR = loc("u_TexGenObjPlaneR");
        locTexGenObjPlaneQ = loc("u_TexGenObjPlaneQ");
        locTexGenEyePlaneS = loc("u_TexGenEyePlaneS");
        locTexGenEyePlaneT = loc("u_TexGenEyePlaneT");
        locTexGenEyePlaneR = loc("u_TexGenEyePlaneR");
        locTexGenEyePlaneQ = loc("u_TexGenEyePlaneQ");

        // Clip planes
        locClipPlanes = loc("u_ClipPlane[0]");

        // Wide line emulation
        locViewportSize = loc("u_ViewportSize");
        locLineWidth = loc("u_LineWidth");

        // Fragment
        for (int i = 0; i < 4; i++) {
            locSampler[i] = loc("u_Sampler" + i);
        }
        locAlphaRef = loc("u_AlphaRef");
        for (int i = 0; i < 4; i++) {
            locTexEnvColor[i] = loc("u_TexEnvColor" + i);
        }
        locFogParams = loc("u_FogParams");
        locFogColor = loc("u_FogColor");
    }

    private int loc(String name) {
        return RENDER_BACKEND.getUniformLocation(programId, name);
    }

    public void destroy() {
        RENDER_BACKEND.deleteProgram(programId);
    }

    /**
     * Compile vertex + fragment + optional geometry shaders, link into a program, and return the FFPProgram.
     */
    static Program create(VertexKey vk, FragmentKey fk, String vertSrc, String fragSrc, String geomSrc) {
        final RenderBackend backend = RENDER_BACKEND;
        final int id = PROGRAM_COUNTER.getAndIncrement();
        final String vkHex = Long.toHexString(vk.pack());
        final boolean hasGeom = geomSrc != null;

        int shaderCount = 0;
        final int[] shaders = new int[5]; // vertex, fragment, geometry, tess control, tess evaluation
        int program = 0;
        try {
            shaders[shaderCount++] = compileShader(GL20.GL_VERTEX_SHADER, vertSrc, "ffp_v_" + vkHex);
            shaders[shaderCount++] = compileShader(GL20.GL_FRAGMENT_SHADER, fragSrc, "ffp_f_" + id);
            if (geomSrc != null) {
                shaders[shaderCount++] = compileShader(GL32.GL_GEOMETRY_SHADER, geomSrc, "ffp_g_" + id);
            }

            program = backend.createProgram();
            for (int i = 0; i < shaderCount; i++) backend.attachShader(program, shaders[i]);
            backend.linkProgram(program);

            final String log = backend.getProgramInfoLog(program, backend.getProgrami(program, GL20.GL_INFO_LOG_LENGTH));
            if (!log.isEmpty()) {
                GLStateManager.LOGGER.warn("FFP program link log (vk=0x{}, fk={}): {}", vkHex, fk, log);
            }

            if (backend.getProgrami(program, GL20.GL_LINK_STATUS) != GL11.GL_TRUE) {
                throw new RuntimeException("FFP shader link failed (vk=0x" + vkHex + ", fk=" + fk + "): " + log);
            }

            final String debugName = "FFP(v=0x" + vkHex + ",f=" + id + (hasGeom ? ",g" : "") + ")";
            GLDebug.nameObject(KHRDebug.GL_PROGRAM, program, debugName);

            // Detach and delete individual shaders — they're linked into the program
            for (int i = 0; i < shaderCount; i++) {
                backend.detachShader(program, shaders[i]);
                backend.deleteShader(shaders[i]);
            }
            shaderCount = 0;

            final Program ffpProgram = new Program(program, vk, fk);

            final int previousProgram = GLStateManager.getActiveProgram();
            backend.useProgram(program);
            for (int i = 0; i < 4; i++) {
                if (ffpProgram.locSampler[i] != -1) backend.uniform1i(ffpProgram.locSampler[i], i);
            }
            backend.useProgram(previousProgram);

            return ffpProgram;
        } catch (RuntimeException e) {
            if (program != 0) backend.deleteProgram(program);
            for (int i = 0; i < shaderCount; i++) backend.deleteShader(shaders[i]);
            throw e;
        }
    }

    private static int compileShader(int type, String src, String name) {
        final RenderBackend backend = RENDER_BACKEND;
        final int shader = backend.createShader(type);
        backend.shaderSource(shader, src);
        backend.compileShader(shader);

        final String typeName = switch (type) {
            case GL20.GL_VERTEX_SHADER -> "vertex";
            case GL20.GL_FRAGMENT_SHADER -> "fragment";
            case GL32.GL_GEOMETRY_SHADER -> "geometry";
            case GL40.GL_TESS_CONTROL_SHADER -> "tess_control";
            case GL40.GL_TESS_EVALUATION_SHADER -> "tess_evaluation";
            case GL43.GL_COMPUTE_SHADER -> "compute";
            default -> "unknown(0x" + Integer.toHexString(type) + ")";
        };
        GLDebug.nameObject(KHRDebug.GL_SHADER, shader, name + "(" + typeName + ")");

        final String log = backend.getShaderInfoLog(shader, backend.getShaderi(shader, GL20.GL_INFO_LOG_LENGTH));
        if (!log.isEmpty()) {
            GLStateManager.LOGGER.warn("FFP {} shader compilation log for {}: {}", typeName, name, log);
        }

        final int result = backend.getShaderi(shader, GL20.GL_COMPILE_STATUS);
        if (result != GL11.GL_TRUE) {
            GLStateManager.LOGGER.error("FFP {} shader source:\n{}", typeName, src);
            backend.deleteShader(shader);
            throw new RuntimeException("FFP " + typeName + " shader compilation failed for " + name + ": " + log);
        }

        return shader;
    }
}
