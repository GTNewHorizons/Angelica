package net.coderbot.iris.pipeline.transform;

import org.junit.jupiter.api.Test;
import org.taumc.glsl.ShaderParser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ShaderTransformerTest {

    @Test
    void transformIsDeterministicUnderConcurrency() throws ExecutionException, InterruptedException {
        TransformPatcher.clearCache();
        ShaderTransformer.clearCache();

        final String vertex = "#version 120\nvoid main(){gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;}";
        final String fragment = "#version 120\nvoid main(){gl_FragColor = vec4(1.0);}";

        ExecutorService exec = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Map<PatchShaderType, String>>> tasks = new ArrayList<>();
            for (int i = 0; i < 64; i++) {
                tasks.add(() -> TransformPatcher.patchComposite(vertex, null, fragment));
            }

            List<Future<Map<PatchShaderType, String>>> futures = exec.invokeAll(tasks);
            Map<PatchShaderType, String> first = futures.get(0).get();
            assertNotNull(first);

            for (Future<Map<PatchShaderType, String>> f : futures) {
                Map<PatchShaderType, String> r = f.get();
                assertEquals(first, r);
            }
        } finally {
            exec.shutdownNow();
        }
    }

    @Test
    void formattedShaderIsParseable() {
        TransformPatcher.clearCache();
        ShaderTransformer.clearCache();

        final String vertex = "#version 120\nvoid main(){gl_Position = gl_ModelViewProjectionMatrix * gl_Vertex;}";
        final String fragment = "#version 120\nvoid main(){gl_FragColor = vec4(1.0);}";

        Map<PatchShaderType, String> transformed = TransformPatcher.patchComposite(vertex, null, fragment);
        assertNotNull(transformed);

        String v = transformed.get(PatchShaderType.VERTEX);
        String f = transformed.get(PatchShaderType.FRAGMENT);
        assertNotNull(v);
        assertNotNull(f);

        ShaderParser.parseShader(v);
        ShaderParser.parseShader(f);
    }
}
