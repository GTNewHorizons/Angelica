package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.hooks.GLSMHooks;
import com.gtnewhorizons.angelica.glsm.hooks.PerFrameUniformBlock;
import com.gtnewhorizons.angelica.glsm.hooks.PerFrameUniformBlock.Member;
import com.gtnewhorizons.angelica.glsm.shader.GlslVulkanPreprocess;
import com.gtnewhorizons.angelica.glsm.shader.ShaderCacheIO;
import com.gtnewhorizons.angelica.glsm.shader.ShaderDiskCache;
import com.gtnewhorizons.angelica.glsm.testutil.Reflect;
import com.gtnewhorizons.angelica.glsm.shader.UniformType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL43;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageDiskCacheTest {

    private static final String RAW = """
        #version 460 core
        in vec3 a_PosId;
        void main() {
            gl_Position = vec4(a_PosId, 1.0);
        }
        """;

    private static final String PFB_FRAG = """
        #version 460 core
        uniform float frameTimeCounter;
        layout(location = 0) out vec4 fragColor;
        void main() {
            fragColor = vec4(frameTimeCounter);
        }
        """;

    private static final String BROKEN = """
        #version 460 core
        void main() { undeclared_var = 1.0; }
        """;

    private static final byte[] POISON_SPIRV = {1, 2, 3, 4, 5, 6, 7, 8};

    private static final PerFrameUniformBlock BLOCK_A = new PerFrameUniformBlock(List.of(new Member("frameTimeCounter", UniformType.FLOAT)));

    private static final PerFrameUniformBlock BLOCK_B = new PerFrameUniformBlock(List.of(
        new Member("frameTimeCounter", UniformType.FLOAT),
        new Member("rainStrength", UniformType.FLOAT)));

    @BeforeEach
    @AfterEach
    void cleanup() {
        Reflect.setStatic(ShaderDiskCache.class, "root", null);
        ShaderManager.clearPrewarmCache();
        GlslVulkanPreprocess.clearCache();
        GLSMHooks.perFrameUniformBlock = null;
        GLSMHooks.perPassUniformBlock = null;
    }

    private static byte[] poison(String marker) {
        final ShaderManager.StageReflection reflection = new ShaderManager.StageReflection(
            ShaderManager.ResourceCounts.EMPTY, List.of(marker), List.of(), List.of(), 0, List.of(), List.of(), List.of(), List.of(),
            -1, 0, 0, 0, 0, ShaderManager.BlockReflection.emptyBlocks());
        return ShaderStageSerializer.encode(POISON_SPIRV, reflection, ShaderManager.GraphicsBindingMap.EMPTY, Set.of("poison_bool"), "poison_source");
    }

    private static ShaderManager.ShaderObject object(ShaderManager sm, int id) {
        final Map<Integer, ShaderManager.ShaderObject> objects = Reflect.get(sm, "shaderObjects");
        return objects.get(id);
    }

    @Test
    void prewarmHitReturnsTheStoredEntry(@TempDir Path dir) {
        ShaderDiskCache.configure(dir, "t");
        ShaderManager.prewarmSpirv(RAW, GL20.GL_VERTEX_SHADER);
        final ShaderDiskCache.Key key = ShaderManager.prewarmKey(RAW, GL20.GL_VERTEX_SHADER, null, null);
        assertNotNull(ShaderDiskCache.get(key), "a successful prewarm must be stored");

        ShaderManager.clearPrewarmCache();
        ShaderDiskCache.put(key, poison("poison_sampler"));
        ShaderManager.prewarmSpirv(RAW, GL20.GL_VERTEX_SHADER);

        final ShaderManager sm = new ShaderManager(null);
        final int shader = sm.createShader(GL20.GL_VERTEX_SHADER);
        sm.shaderSource(shader, RAW);
        assertEquals("poison_source", sm.getShaderSource(shader));
        sm.compileShader(shader);

        final ShaderManager.ShaderObject obj = object(sm, shader);
        assertTrue(obj.compiled);
        assertEquals(List.of("poison_sampler"), obj.reflection.samplerNames());
        assertEquals(Set.of("poison_bool"), obj.boolUniforms);
        assertArrayEquals(POISON_SPIRV, ShaderCacheIO.toHeap(obj.spirv));
        sm.deleteShader(shader);
    }

    @Test
    void prewarmHitDoesNotShareTheSpirvBufferBetweenShaders(@TempDir Path dir) {
        ShaderDiskCache.configure(dir, "t");
        ShaderManager.prewarmSpirv(RAW, GL20.GL_VERTEX_SHADER);
        ShaderManager.clearPrewarmCache();
        ShaderManager.prewarmSpirv(RAW, GL20.GL_VERTEX_SHADER);

        final ShaderManager sm = new ShaderManager(null);
        final int a = sm.createShader(GL20.GL_VERTEX_SHADER);
        final int b = sm.createShader(GL20.GL_VERTEX_SHADER);
        sm.shaderSource(a, RAW);
        sm.shaderSource(b, RAW);
        sm.compileShader(a);
        sm.compileShader(b);

        final ShaderManager.ShaderObject objA = object(sm, a);
        final ShaderManager.ShaderObject objB = object(sm, b);
        assertTrue(objA.compiled && objB.compiled);
        assertTrue(objA.spirv != objB.spirv);
        assertArrayEquals(ShaderCacheIO.toHeap(objA.spirv), ShaderCacheIO.toHeap(objB.spirv));
        sm.deleteShader(a);
        sm.deleteShader(b);
    }

    @Test
    void shaderSourceFallbackHitReturnsTheStoredEntry(@TempDir Path dir) {
        ShaderDiskCache.configure(dir, "t");
        final ShaderManager first = new ShaderManager(null);
        final int a = first.createShader(GL20.GL_VERTEX_SHADER);
        first.shaderSource(a, RAW);
        first.compileShader(a);
        assertTrue(object(first, a).compiled);
        final String transformed = first.getShaderSource(a);
        final ShaderDiskCache.Key key = ShaderManager.stageKey(transformed, GL20.GL_VERTEX_SHADER);
        assertNotNull(ShaderDiskCache.get(key), "a successful stage compile must be stored");
        assertFalse(Files.exists(dir.resolve("spirv")), "the stage entry already holds the SPIR-V");
        first.deleteShader(a);

        ShaderManager.clearPrewarmCache();
        ShaderDiskCache.put(key, poison("poison_sampler"));

        final ShaderManager sm = new ShaderManager(null);
        final int b = sm.createShader(GL20.GL_VERTEX_SHADER);
        sm.shaderSource(b, RAW);
        assertEquals(transformed, sm.getShaderSource(b), "the transformed source must be available without compiling");
        sm.compileShader(b);

        final ShaderManager.ShaderObject obj = object(sm, b);
        assertTrue(obj.compiled);
        assertEquals(List.of("poison_sampler"), obj.reflection.samplerNames());
        assertArrayEquals(POISON_SPIRV, ShaderCacheIO.toHeap(obj.spirv));
        sm.deleteShader(b);
    }

    @Test
    void failedCompileStoresNothing(@TempDir Path dir) {
        ShaderDiskCache.configure(dir, "t");
        ShaderManager.prewarmSpirv(BROKEN, GL20.GL_VERTEX_SHADER);
        assertNull(ShaderDiskCache.get(ShaderManager.prewarmKey(BROKEN, GL20.GL_VERTEX_SHADER, null, null)));

        final ShaderManager sm = new ShaderManager(null);
        final int shader = sm.createShader(GL20.GL_VERTEX_SHADER);
        sm.shaderSource(shader, BROKEN);
        sm.compileShader(shader);
        assertFalse(object(sm, shader).compiled);
        assertNull(ShaderDiskCache.get(ShaderManager.stageKey(sm.getShaderSource(shader), GL20.GL_VERTEX_SHADER)));
        sm.deleteShader(shader);
    }

    @Test
    void corruptEntryFallsBackToCompilingAndIsReplaced(@TempDir Path dir) {
        ShaderDiskCache.configure(dir, "t");
        final ShaderDiskCache.Key key = ShaderManager.prewarmKey(RAW, GL20.GL_VERTEX_SHADER, null, null);
        ShaderDiskCache.put(key, new byte[] {9, 9, 9});
        ShaderManager.prewarmSpirv(RAW, GL20.GL_VERTEX_SHADER);
        assertNotNull(ShaderStageSerializer.decode(ShaderDiskCache.get(key)));
    }

    @Test
    void computePrewarmRoundTripsThroughTheDisk(@TempDir Path dir) {
        ShaderDiskCache.configure(dir, "t");
        final String compute = """
            #version 460 core
            layout(local_size_x = 1) in;
            layout(std430, binding = 0) buffer B { uint data[]; } b;
            void main() { b.data[0] = 1u; }
            """;
        ShaderManager.prewarmSpirv(compute, GL43.GL_COMPUTE_SHADER);
        final ShaderDiskCache.Key key = ShaderManager.prewarmKey(compute, GL43.GL_COMPUTE_SHADER, null, null);
        assertNotNull(ShaderDiskCache.get(key));

        ShaderManager.clearPrewarmCache();
        ShaderManager.prewarmSpirv(compute, GL43.GL_COMPUTE_SHADER);
        final ShaderManager sm = new ShaderManager(null);
        final int shader = sm.createShader(GL43.GL_COMPUTE_SHADER);
        sm.shaderSource(shader, compute);
        sm.compileShader(shader);
        final ShaderManager.ShaderObject obj = object(sm, shader);
        assertTrue(obj.compiled);
        assertTrue(obj.reflection == ShaderManager.StageReflection.EMPTY, "a compute hit must keep the shared EMPTY reflection so link reflects the remapped copy");
        sm.deleteShader(shader);
    }

    @Test
    void prewarmEntryIsNotReusedAcrossPerFrameBlocks() {
        GLSMHooks.perFrameUniformBlock = BLOCK_A;
        ShaderManager.prewarmSpirv(PFB_FRAG, GL20.GL_FRAGMENT_SHADER);

        GLSMHooks.perFrameUniformBlock = BLOCK_B;
        final ShaderManager sm = new ShaderManager(null);
        final int viaTransform = sm.createShader(GL20.GL_FRAGMENT_SHADER);
        sm.shaderSource(viaTransform, PFB_FRAG);
        assertTrue(sm.getShaderSource(viaTransform).contains("rainStrength"), "the first prewarm's block must not leak into a different block");

        ShaderManager.prewarmSpirv(PFB_FRAG, GL20.GL_FRAGMENT_SHADER);
        final int viaPrewarm = sm.createShader(GL20.GL_FRAGMENT_SHADER);
        sm.shaderSource(viaPrewarm, PFB_FRAG);
        assertTrue(sm.getShaderSource(viaPrewarm).contains("rainStrength"));

        GLSMHooks.perFrameUniformBlock = BLOCK_A;
        final int back = sm.createShader(GL20.GL_FRAGMENT_SHADER);
        sm.shaderSource(back, PFB_FRAG);
        assertFalse(sm.getShaderSource(back).contains("rainStrength"));
        sm.shutdown();
    }

    private static byte[] poisonTransform() {
        return ShaderStageSerializer.encodeTransform("poison_transform", Set.of("poison_bool"));
    }

    @Test
    void transformDiskHitReturnsTheStoredEntry(@TempDir Path dir) {
        ShaderDiskCache.configure(dir, "t");
        final ShaderManager first = new ShaderManager(null);
        final int a = first.createShader(GL20.GL_VERTEX_SHADER);
        first.shaderSource(a, RAW);
        final String transformed = first.getShaderSource(a);
        final ShaderDiskCache.Key key = ShaderManager.transformDiskKey(RAW, GL20.GL_VERTEX_SHADER, null, null);
        final ShaderManager.PrewarmTransformResult stored = ShaderStageSerializer.decodeTransform(ShaderDiskCache.get(key));
        assertNotNull(stored, "a successful transform must be stored");
        assertEquals(transformed, stored.source());
        first.compileShader(a);
        first.deleteShader(a);

        ShaderManager.clearPrewarmCache();
        ShaderDiskCache.put(key, poisonTransform());

        final ShaderManager sm = new ShaderManager(null);
        final int b = sm.createShader(GL20.GL_VERTEX_SHADER);
        sm.shaderSource(b, RAW);
        assertEquals("poison_transform", sm.getShaderSource(b));
        assertEquals(Set.of("poison_bool"), object(sm, b).boolUniforms);
        sm.compileShader(b);
        sm.deleteShader(b);
    }

    @Test
    void corruptTransformEntryFallsBackToTransformingAndIsReplaced(@TempDir Path dir) {
        ShaderDiskCache.configure(dir, "t");
        final ShaderDiskCache.Key key = ShaderManager.transformDiskKey(RAW, GL20.GL_VERTEX_SHADER, null, null);
        ShaderDiskCache.put(key, new byte[] {9, 9, 9});

        final ShaderManager sm = new ShaderManager(null);
        final int shader = sm.createShader(GL20.GL_VERTEX_SHADER);
        sm.shaderSource(shader, RAW);
        assertEquals(ShaderManager.applyPrewarmTransformsFull(RAW, GL20.GL_VERTEX_SHADER, null, null).source(), sm.getShaderSource(shader));
        assertNotNull(ShaderStageSerializer.decodeTransform(ShaderDiskCache.get(key)));
        sm.compileShader(shader);
        sm.deleteShader(shader);
    }

    @Test
    void transformKeyChangesWithEveryInput() {
        final String base = ShaderManager.transformDiskKey(RAW, GL20.GL_VERTEX_SHADER, BLOCK_A, null).hex();

        assertEquals(base, ShaderManager.transformDiskKey(RAW, GL20.GL_VERTEX_SHADER, new PerFrameUniformBlock(List.of(new Member("frameTimeCounter", UniformType.FLOAT))), null).hex());
        assertNotEquals(base, ShaderManager.transformDiskKey(RAW + " ", GL20.GL_VERTEX_SHADER, BLOCK_A, null).hex(), "source");
        assertNotEquals(base, ShaderManager.transformDiskKey(RAW, GL20.GL_FRAGMENT_SHADER, BLOCK_A, null).hex(), "type");
        assertNotEquals(base, ShaderManager.transformDiskKey(RAW, GL20.GL_VERTEX_SHADER, BLOCK_B, null).hex(), "per-frame block");
        assertNotEquals(base, ShaderManager.transformDiskKey(RAW, GL20.GL_VERTEX_SHADER, null, null).hex(), "no per-frame block");
        assertNotEquals(base, ShaderManager.transformDiskKey(RAW, GL20.GL_VERTEX_SHADER, BLOCK_A, BLOCK_B).hex(), "per-pass block");
        assertNotEquals(base, ShaderManager.transformDiskKey(RAW, GL20.GL_VERTEX_SHADER, null, BLOCK_A).hex(), "slots are not interchangeable");
        assertNotEquals(base, ShaderManager.prewarmKey(RAW, GL20.GL_VERTEX_SHADER, null, null).hex(), "layer and toolchain ids");
    }

    @Test
    void stageKeyIgnoresUniformBlocks() {
        GLSMHooks.perFrameUniformBlock = BLOCK_A;
        final String base = ShaderManager.stageKey(RAW, GL20.GL_VERTEX_SHADER).hex();
        GLSMHooks.perFrameUniformBlock = BLOCK_B;
        GLSMHooks.perPassUniformBlock = BLOCK_A;
        assertEquals(base, ShaderManager.stageKey(RAW, GL20.GL_VERTEX_SHADER).hex());
        assertNotEquals(base, ShaderManager.stageKey(RAW + " ", GL20.GL_VERTEX_SHADER).hex());
        assertNotEquals(base, ShaderManager.stageKey(RAW, GL20.GL_FRAGMENT_SHADER).hex());
    }

    @Test
    void prewarmKeyChangesWithEveryInput() {
        final String base = ShaderManager.prewarmKey(RAW, GL20.GL_VERTEX_SHADER, BLOCK_A, null).hex();

        assertEquals(base, ShaderManager.prewarmKey(RAW, GL20.GL_VERTEX_SHADER, BLOCK_A, null).hex());
        assertEquals(base, ShaderManager.prewarmKey(RAW, GL20.GL_VERTEX_SHADER, new PerFrameUniformBlock(List.of(new Member("frameTimeCounter", UniformType.FLOAT))), null).hex(), "equal blocks built separately must agree");

        assertNotEquals(base, ShaderManager.stageKey(RAW, GL20.GL_VERTEX_SHADER).hex());
        assertNotEquals(base, ShaderManager.prewarmKey(RAW + " ", GL20.GL_VERTEX_SHADER, BLOCK_A, null).hex());
        assertNotEquals(base, ShaderManager.prewarmKey(RAW, GL20.GL_FRAGMENT_SHADER, BLOCK_A, null).hex());

        assertNotEquals(base, ShaderManager.prewarmKey(RAW, GL20.GL_VERTEX_SHADER, BLOCK_B, null).hex(), "an added per-frame member");
        assertNotEquals(base, ShaderManager.prewarmKey(RAW, GL20.GL_VERTEX_SHADER, new PerFrameUniformBlock(List.of(new Member("frameTimeCounter", UniformType.INT))), null).hex(), "a changed member type");
        assertNotEquals(base, ShaderManager.prewarmKey(RAW, GL20.GL_VERTEX_SHADER, new PerFrameUniformBlock(List.of(new Member("rainStrength", UniformType.FLOAT))), null).hex(), "a renamed member");
        assertNotEquals(base, ShaderManager.prewarmKey(RAW, GL20.GL_VERTEX_SHADER, null, null).hex(), "no per-frame block");
        assertNotEquals(base, ShaderManager.prewarmKey(RAW, GL20.GL_VERTEX_SHADER, BLOCK_A, BLOCK_B).hex(), "a per-pass block");
    }
}
