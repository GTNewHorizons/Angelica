package com.gtnewhorizons.angelica.sdlgpu.shader.dxbc;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.JNI;
import org.lwjgl.system.Library;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;
import org.lwjgl.system.SharedLibrary;
import org.lwjgl.system.libffi.FFICIF;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.charset.StandardCharsets;

import static org.lwjgl.system.APIUtil.apiGetFunctionAddress;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.system.MemoryUtil.NULL;
import static org.lwjgl.system.MemoryUtil.memAddress;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memCopy;
import static org.lwjgl.system.MemoryUtil.memGetAddress;
import static org.lwjgl.system.libffi.LibFFI.FFI_DEFAULT_ABI;
import static org.lwjgl.system.libffi.LibFFI.FFI_OK;
import static org.lwjgl.system.libffi.LibFFI.ffi_call;
import static org.lwjgl.system.libffi.LibFFI.ffi_prep_cif;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_pointer;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_sint32;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_uint32;
import static org.lwjgl.system.libffi.LibFFI.ffi_type_uint64;

public final class D3DCompiler {

    private static final SharedLibrary D3DCOMPILER = Library.loadNative(D3DCompiler.class, "com.gtnewhorizons.angelica.sdlgpu", null, "d3dcompiler_47", "d3dcompiler");

    private static final long D3D_COMPILE_ADDR = apiGetFunctionAddress(D3DCOMPILER, "D3DCompile");

    public static final int D3DCOMPILE_ENABLE_STRICTNESS = 0x800;
    public static final int D3DCOMPILE_OPTIMIZATION_LEVEL3 = 0xC000;

    private static final FFICIF CIF;
    private static final PointerBuffer ATYPES;
    static {
        ATYPES = MemoryUtil.memAllocPointer(11);
        ATYPES.put(0, ffi_type_pointer.address())  // LPCVOID pSrcData
              .put(1, ffi_type_uint64.address())   // SIZE_T  SrcDataSize
              .put(2, ffi_type_pointer.address())  // LPCSTR  pSourceName
              .put(3, ffi_type_pointer.address())  // const D3D_SHADER_MACRO* pDefines
              .put(4, ffi_type_pointer.address())  // ID3DInclude* pInclude
              .put(5, ffi_type_pointer.address())  // LPCSTR  pEntrypoint
              .put(6, ffi_type_pointer.address())  // LPCSTR  pTarget
              .put(7, ffi_type_uint32.address())   // UINT    Flags1
              .put(8, ffi_type_uint32.address())   // UINT    Flags2
              .put(9, ffi_type_pointer.address())  // ID3DBlob** ppCode
              .put(10, ffi_type_pointer.address());// ID3DBlob** ppErrorMsgs
        CIF = FFICIF.malloc();
        final int status = ffi_prep_cif(CIF, FFI_DEFAULT_ABI, ffi_type_sint32, ATYPES);
        if (status != FFI_OK) {
            throw new IllegalStateException("ffi_prep_cif failed for D3DCompile: " + status);
        }
    }

    private static final long VTBL_RELEASE = 2L * Pointer.POINTER_SIZE;
    private static final long VTBL_GET_BUFFER_POINTER = 3L * Pointer.POINTER_SIZE;
    private static final long VTBL_GET_BUFFER_SIZE = 4L * Pointer.POINTER_SIZE;

    private D3DCompiler() {}

    public static ByteBuffer compile(String hlsl, String entry, String target, int flags1, int flags2) {
        try (MemoryStack stack = stackPush()) {
            final ByteBuffer src = stack.UTF8(hlsl, false);
            return compile(memAddress(src), src.remaining(), entry, target, flags1, flags2);
        }
    }

    public static ByteBuffer compile(long srcAddr, int srcLen, String entry, String target, int flags1, int flags2) {
        try (MemoryStack stack = stackPush()) {
            final ByteBuffer entryBuf = stack.ASCII(entry);
            final ByteBuffer targetBuf = stack.ASCII(target);
            final PointerBuffer ppCode = stack.callocPointer(1);
            final PointerBuffer ppErr = stack.callocPointer(1);

            final PointerBuffer slotSrcData     = stack.pointers(srcAddr);
            final LongBuffer    slotSrcDataSize = stack.longs(srcLen);
            final PointerBuffer slotSourceName  = stack.pointers(NULL);
            final PointerBuffer slotDefines     = stack.pointers(NULL);
            final PointerBuffer slotInclude     = stack.pointers(NULL);
            final PointerBuffer slotEntrypoint  = stack.pointers(memAddress(entryBuf));
            final PointerBuffer slotTarget      = stack.pointers(memAddress(targetBuf));
            final IntBuffer     slotFlags1      = stack.ints(flags1);
            final IntBuffer     slotFlags2      = stack.ints(flags2);
            final PointerBuffer slotPpCode      = stack.pointers(memAddress(ppCode));
            final PointerBuffer slotPpErr       = stack.pointers(memAddress(ppErr));

            final PointerBuffer avalues = stack.mallocPointer(11);
            avalues.put(0, memAddress(slotSrcData));
            avalues.put(1, memAddress(slotSrcDataSize));
            avalues.put(2, memAddress(slotSourceName));
            avalues.put(3, memAddress(slotDefines));
            avalues.put(4, memAddress(slotInclude));
            avalues.put(5, memAddress(slotEntrypoint));
            avalues.put(6, memAddress(slotTarget));
            avalues.put(7, memAddress(slotFlags1));
            avalues.put(8, memAddress(slotFlags2));
            avalues.put(9, memAddress(slotPpCode));
            avalues.put(10, memAddress(slotPpErr));

            final ByteBuffer rvalue = stack.malloc(4);
            ffi_call(CIF, D3D_COMPILE_ADDR, rvalue, avalues);
            final int hresult = rvalue.getInt(0);

            final long codeBlob = ppCode.get(0);
            final long errBlob = ppErr.get(0);

            if (hresult != 0) {
                final String msg = errBlob != NULL ? readBlobAsAscii(errBlob) : "(no error blob)";
                if (errBlob != NULL) releaseBlob(errBlob);
                if (codeBlob != NULL) releaseBlob(codeBlob);
                throw new RuntimeException("D3DCompile failed (HRESULT=0x" + Integer.toHexString(hresult) + "): " + msg);
            }

            try {
                if (codeBlob == NULL) {
                    throw new RuntimeException("D3DCompile succeeded but produced no code blob");
                }
                return copyBlobBytes(codeBlob);
            } finally {
                if (codeBlob != NULL) releaseBlob(codeBlob);
                if (errBlob != NULL) releaseBlob(errBlob);
            }
        }
    }

    private static ByteBuffer copyBlobBytes(long blob) {
        final long vtbl = memGetAddress(blob);
        final long getPtr = memGetAddress(vtbl + VTBL_GET_BUFFER_POINTER);
        final long getSize = memGetAddress(vtbl + VTBL_GET_BUFFER_SIZE);
        final long dataPtr = JNI.invokePP(blob, getPtr);
        final long dataLen = JNI.invokePP(blob, getSize);
        final ByteBuffer out = memAlloc((int) dataLen);
        memCopy(dataPtr, memAddress(out), dataLen);
        return out;
    }

    private static String readBlobAsAscii(long blob) {
        final long vtbl = memGetAddress(blob);
        final long getPtr = memGetAddress(vtbl + VTBL_GET_BUFFER_POINTER);
        final long getSize = memGetAddress(vtbl + VTBL_GET_BUFFER_SIZE);
        final long dataPtr = JNI.invokePP(blob, getPtr);
        final long dataLen = JNI.invokePP(blob, getSize);
        if (dataPtr == NULL || dataLen <= 0) return "(empty)";
        final ByteBuffer view = MemoryUtil.memByteBuffer(dataPtr, (int) dataLen);
        int end = view.remaining();
        while (end > 0) {
            final byte b = view.get(end - 1);
            if (b != 0 && b != '\n' && b != '\r' && b != ' ') break;
            end--;
        }
        final byte[] bytes = new byte[end];
        view.duplicate().get(bytes, 0, end);
        return new String(bytes, StandardCharsets.US_ASCII);
    }

    private static void releaseBlob(long blob) {
        final long vtbl = memGetAddress(blob);
        final long releaseAddr = memGetAddress(vtbl + VTBL_RELEASE);
        JNI.invokePP(blob, releaseAddr);
    }
}
