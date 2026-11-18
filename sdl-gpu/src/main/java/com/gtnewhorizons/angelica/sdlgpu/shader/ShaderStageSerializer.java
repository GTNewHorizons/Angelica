package com.gtnewhorizons.angelica.sdlgpu.shader;

import com.gtnewhorizons.angelica.glsm.shader.ShaderCacheIO;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager.BlockReflection;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager.FsInput;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager.GraphicsBindingMap;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager.PrewarmTransformResult;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager.ResourceCounts;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager.StageReflection;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager.UboMember;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager.VsInput;
import com.gtnewhorizons.angelica.sdlgpu.shader.ShaderManager.VsOutput;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.gtnewhorizons.angelica.glsm.shader.ShaderCacheIO.readBytes;
import static com.gtnewhorizons.angelica.glsm.shader.ShaderCacheIO.readCount;
import static com.gtnewhorizons.angelica.glsm.shader.ShaderCacheIO.readInts;
import static com.gtnewhorizons.angelica.glsm.shader.ShaderCacheIO.readStr;
import static com.gtnewhorizons.angelica.glsm.shader.ShaderCacheIO.readStrSet;
import static com.gtnewhorizons.angelica.glsm.shader.ShaderCacheIO.writeBytes;
import static com.gtnewhorizons.angelica.glsm.shader.ShaderCacheIO.writeInts;
import static com.gtnewhorizons.angelica.glsm.shader.ShaderCacheIO.writeSortedStrs;
import static com.gtnewhorizons.angelica.glsm.shader.ShaderCacheIO.writeStr;

final class ShaderStageSerializer {

    record Stage(byte[] spirv, StageReflection reflection, GraphicsBindingMap bindingMap, Set<String> boolUniforms, String source) {}

    private interface Reader<T> {
        T read(ByteBuffer in);
    }

    private interface Writer<T> {
        void write(DataOutputStream out, T value) throws IOException;
    }

    private ShaderStageSerializer() {}

    static byte[] encode(byte[] spirv, StageReflection reflection, GraphicsBindingMap bindingMap, Set<String> boolUniforms, String source) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream(spirv.length + (source == null ? 0 : source.length()) + 4096);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            writeBytes(out, spirv);
            writeReflection(out, reflection);
            writeInts(out, bindingMap.roStorageTextureGlSlots());
            writeInts(out, bindingMap.rwStorageTextureGlSlots());
            writeInts(out, bindingMap.roSsboGlSlots());
            writeSortedStrs(out, boolUniforms);
            writeStr(out, source);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    static Stage decode(byte[] data) {
        try {
            final ByteBuffer in = ByteBuffer.wrap(data);
            final byte[] spirv = readBytes(in);
            final StageReflection reflection = readReflection(in);
            final GraphicsBindingMap bindingMap = new GraphicsBindingMap(readInts(in), readInts(in), readInts(in));
            final Set<String> bools = readStrSet(in);
            final String source = readStr(in);
            return new Stage(spirv, reflection, bindingMap, bools, source);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static byte[] encodeTransform(String source, Set<String> boolUniforms) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream(source.length() + 256);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            writeStr(out, source);
            writeSortedStrs(out, boolUniforms);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    static PrewarmTransformResult decodeTransform(byte[] data) {
        try {
            final ByteBuffer in = ByteBuffer.wrap(data);
            final String source = readStr(in);
            final Set<String> bools = readStrSet(in);
            return new PrewarmTransformResult(source, bools);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void writeReflection(DataOutputStream out, StageReflection r) throws IOException {
        out.writeInt(r.counts().numSamplers());
        out.writeInt(r.counts().numUBOs());
        out.writeInt(r.counts().numStorageBuffers());
        out.writeInt(r.counts().numStorageTextures());
        writeStrList(out, r.samplerNames());
        writeStrList(out, r.unusedSamplerNames());
        writeStrList(out, r.extraUniformNames());
        writeStrList(out, r.storageImageNames());
        out.writeInt(r.uboSize());
        writeList(out, r.uboMembers(), ShaderStageSerializer::writeMember);
        writeList(out, r.vsInputs(), (o, v) -> {
            writeStr(o, v.name());
            o.writeInt(v.binaryOffset());
            o.writeInt(v.originalLocation());
            o.writeInt(v.vecSize());
            o.writeInt(v.baseType());
        });
        writeList(out, r.vsOutputs(), (o, v) -> {
            writeStr(o, v.name());
            o.writeInt(v.originalLocation());
        });
        writeList(out, r.fsInputs(), (o, v) -> {
            writeStr(o, v.name());
            o.writeInt(v.binaryOffset());
            o.writeInt(v.originalLocation());
        });
        out.writeInt(r.maxOutputLocation());
        out.writeInt(r.numReadonlyStorageBuffers());
        out.writeInt(r.numReadwriteStorageBuffers());
        out.writeInt(r.numReadonlyStorageTextures());
        out.writeInt(r.numReadwriteStorageTextures());
        out.writeInt(r.blocks().length);
        for (BlockReflection b : r.blocks()) {
            out.writeInt(b.size());
            out.writeInt(b.binding());
            writeList(out, b.members(), ShaderStageSerializer::writeMember);
            out.writeBoolean(b.readOnly());
        }
    }

    private static StageReflection readReflection(ByteBuffer in) {
        final ResourceCounts counts = new ResourceCounts(in.getInt(), in.getInt(), in.getInt(), in.getInt());
        final List<String> samplerNames = readStrList(in);
        final List<String> unusedSamplerNames = readStrList(in);
        final List<String> extraNames = readStrList(in);
        final List<String> storageImageNames = readStrList(in);
        final int uboSize = in.getInt();
        final List<UboMember> uboMembers = readList(in, ShaderStageSerializer::readMember);
        final List<VsInput> vsInputs = readList(in, i -> new VsInput(readStr(i), i.getInt(), i.getInt(), i.getInt(), i.getInt()));
        final List<VsOutput> vsOutputs = readList(in, i -> new VsOutput(readStr(i), i.getInt()));
        final List<FsInput> fsInputs = readList(in, i -> new FsInput(readStr(i), i.getInt(), i.getInt()));
        final int maxOutputLocation = in.getInt();
        final int roBuf = in.getInt();
        final int rwBuf = in.getInt();
        final int roTex = in.getInt();
        final int rwTex = in.getInt();
        final int blockCount = readCount(in);
        final BlockReflection[] blocks = new BlockReflection[blockCount];
        for (int b = 0; b < blockCount; b++) {
            blocks[b] = new BlockReflection(in.getInt(), in.getInt(), readList(in, ShaderStageSerializer::readMember), in.get() != 0);
        }
        return new StageReflection(counts, samplerNames, unusedSamplerNames, extraNames, storageImageNames, uboSize, uboMembers, vsInputs, vsOutputs, fsInputs,
            maxOutputLocation, roBuf, rwBuf, roTex, rwTex, blocks);
    }

    private static void writeMember(DataOutputStream out, UboMember m) throws IOException {
        writeStr(out, m.name());
        out.writeInt(m.offset());
        out.writeInt(m.size());
        out.writeInt(m.arrayStride());
        out.writeInt(m.vectorSize());
        out.writeInt(m.columns());
        out.writeInt(m.baseType());
        out.writeInt(m.arrayLen());
    }

    private static UboMember readMember(ByteBuffer in) {
        return new UboMember(readStr(in), in.getInt(), in.getInt(), in.getInt(), in.getInt(), in.getInt(), in.getInt(), in.getInt());
    }

    private static <T> void writeList(DataOutputStream out, List<T> list, Writer<T> writer) throws IOException {
        out.writeInt(list.size());
        for (T t : list) writer.write(out, t);
    }

    private static <T> List<T> readList(ByteBuffer in, Reader<T> reader) {
        final int n = readCount(in);
        final List<T> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(reader.read(in));
        return out;
    }

    private static void writeStrList(DataOutputStream out, List<String> list) throws IOException {
        writeList(out, list, ShaderCacheIO::writeStr);
    }

    private static List<String> readStrList(ByteBuffer in) {
        return readList(in, ShaderCacheIO::readStr);
    }
}
