package com.gtnewhorizons.angelica.glsm.ffp;

import com.gtnewhorizons.angelica.glsm.GLDebug;
import com.gtnewhorizons.angelica.glsm.GLStateManager;
import com.gtnewhorizons.angelica.glsm.RenderSystem;
import lombok.Getter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.KHRDebug;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A compiled and linked FFP emulation program (vertex + fragment shader pair). Owns the GL program handle and caches uniform locations.
 */
public class Program {

    private static final Logger LOGGER = LogManager.getLogger("FFPProgram");
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
        return GL20.glGetUniformLocation(programId, name);
    }

    public void destroy() {
        GL20.glDeleteProgram(programId);
    }

    /**
     * Compile a shader, link it into a program, and return the FFPProgram.
     */
    static Program create(VertexKey vk, FragmentKey fk, String vertSrc, String fragSrc) {
        final int id = PROGRAM_COUNTER.getAndIncrement();
        final int vs = compileShader(GL20.GL_VERTEX_SHADER, vertSrc, "ffp_v_" + Long.toHexString(vk.pack()));
        final int fs = compileShader(GL20.GL_FRAGMENT_SHADER, fragSrc, "ffp_f_" + id);

        final int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vs);
        GL20.glAttachShader(program, fs);
        GL20.glLinkProgram(program);

        final String log = RenderSystem.getProgramInfoLog(program);
        if (!log.isEmpty()) {
            LOGGER.warn("FFP program link log (vk=0x{}, fk={}): {}", Long.toHexString(vk.pack()), fk, log);
        }

        final int linkStatus = GL20.glGetProgrami(program, GL20.GL_LINK_STATUS);
        if (linkStatus != GL11.GL_TRUE) {
            GL20.glDeleteProgram(program);
            GL20.glDeleteShader(vs);
            GL20.glDeleteShader(fs);
            throw new RuntimeException("FFP shader link failed (vk=0x" + Long.toHexString(vk.pack()) + ", fk=" + fk + "): " + log);
        }

        final String debugName = "FFP(v=0x" + Long.toHexString(vk.pack()) + ",f=" + id + ")";
        GLDebug.nameObject(KHRDebug.GL_PROGRAM, program, debugName);

        // Detach and delete individual shaders — they're linked into the program
        GL20.glDetachShader(program, vs);
        GL20.glDetachShader(program, fs);
        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);

        final Program ffpProgram = new Program(program, vk, fk);

        final int previousProgram = GLStateManager.getActiveProgram();
        GL20.glUseProgram(program);
        for (int i = 0; i < 4; i++) {
            if (ffpProgram.locSampler[i] != -1) GL20.glUniform1i(ffpProgram.locSampler[i], i);
        }
        GL20.glUseProgram(previousProgram);

        return ffpProgram;
    }

    private static int compileShader(int type, String src, String name) {
        final int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, src);
        GL20.glCompileShader(shader);

        final String typeName = (type == GL20.GL_VERTEX_SHADER) ? "vertex" : "fragment";
        GLDebug.nameObject(KHRDebug.GL_SHADER, shader, name + "(" + typeName + ")");

        final String log = RenderSystem.getShaderInfoLog(shader);
        if (!log.isEmpty()) {
            LOGGER.warn("FFP {} shader compilation log for {}: {}", typeName, name, log);
        }

        final int result = GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS);
        if (result != GL11.GL_TRUE) {
            LOGGER.error("FFP {} shader source:\n{}", typeName, src);
            GL20.glDeleteShader(shader);
            throw new RuntimeException("FFP " + typeName + " shader compilation failed for " + name + ": " + log);
        }

        return shader;
    }
}
