package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizons.angelica.glsm.recording.commands.DisplayListCommand;
import com.gtnewhorizons.angelica.glsm.recording.commands.IndexedDrawBatchBuilder;
import com.gtnewhorizons.angelica.glsm.recording.commands.IndexedDrawCapture;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Records GL commands to a CommandBuffer during display list compilation.
 */
public final class CommandRecorder {
    private final CommandBuffer buffer;
    private final IndexedDrawBatchBuilder indexedDraws = new IndexedDrawBatchBuilder();

    public CommandRecorder() {
        this.buffer = new CommandBuffer();
    }

    public CommandBuffer getBuffer() {
        return buffer;
    }

    public IndexedDrawBatchBuilder getIndexedDraws() {
        return indexedDraws;
    }

    public void recordIndexedDrawCapture(IndexedDrawCapture capture) {
        indexedDraws.add(capture);
        recordComplexCommand(capture.placeholder);
    }

    public void free() {
        for (IndexedDrawCapture c : indexedDraws.getCaptures()) {
            c.freeBuffers();
        }
    }

    // ==================== Recording Methods ====================

    public void recordEnable(int cap) {
        buffer.writeEnable(cap);
    }

    public void recordDisable(int cap) {
        buffer.writeDisable(cap);
    }

    public void recordClear(int mask) {
        buffer.writeClear(mask);
    }

    public void recordClearColor(float r, float g, float b, float a) {
        buffer.writeClearColor(r, g, b, a);
    }

    public void recordClearDepth(double depth) {
        buffer.writeClearDepth(depth);
    }

    public void recordBlendColor(float r, float g, float b, float a) {
        buffer.writeBlendColor(r, g, b, a);
    }

    public void recordClearStencil(int s) {
        buffer.writeClearStencil(s);
    }

    public void recordColor(float r, float g, float b, float a) {
        buffer.writeColor(r, g, b, a);
    }

    public void recordColorMask(boolean r, boolean g, boolean b, boolean a) {
        buffer.writeColorMask(r, g, b, a);
    }

    public void recordDepthMask(boolean flag) {
        buffer.writeDepthMask(flag);
    }

    public void recordFrontFace(int mode) {
        buffer.writeFrontFace(mode);
    }

    public void recordDepthFunc(int func) {
        buffer.writeDepthFunc(func);
    }

    public void recordBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        buffer.writeBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
    }

    public void recordAlphaFunc(int func, float ref) {
        buffer.writeAlphaFunc(func, ref);
    }

    public void recordCullFace(int mode) {
        buffer.writeCullFace(mode);
    }

    public void recordShadeModel(int mode) {
        buffer.writeShadeModel(mode);
    }

    public void recordBindTexture(int target, int texture) {
        buffer.writeBindTexture(target, texture);
    }

    public void recordTexParameteri(int target, int pname, int param) {
        buffer.writeTexParameteri(target, pname, param);
    }

    public void recordTexParameterf(int target, int pname, float param) {
        buffer.writeTexParameterf(target, pname, param);
    }

    public void recordMatrixMode(int mode) {
        buffer.writeMatrixMode(mode);
    }

    public void recordLoadIdentity() {
        buffer.writeLoadIdentity();
    }

    public void recordPushMatrix() {
        buffer.writePushMatrix();
    }

    public void recordPopMatrix() {
        buffer.writePopMatrix();
    }

    public void recordMultMatrix(Matrix4f matrix) {
        buffer.writeMultMatrix(matrix);
    }

    public void recordLoadMatrix(Matrix4f matrix) {
        buffer.writeLoadMatrix(matrix);
    }

    public void recordOrtho(double left, double right, double bottom, double top, double zNear, double zFar) {
        buffer.writeOrtho(left, right, bottom, top, zNear, zFar);
    }

    public void recordFrustum(double left, double right, double bottom, double top, double zNear, double zFar) {
        buffer.writeFrustum(left, right, bottom, top, zNear, zFar);
    }

    public void recordViewport(int x, int y, int width, int height) {
        buffer.writeViewport(x, y, width, height);
    }

    public void recordScissor(int x, int y, int width, int height) {
        buffer.writeScissor(x, y, width, height);
    }

    public void recordPointSize(float size) {
        buffer.writePointSize(size);
    }

    public void recordLineWidth(float width) {
        buffer.writeLineWidth(width);
    }

    public void recordLineStipple(int factor, int pattern) {
        buffer.writeLineStipple(factor, pattern);
    }

    public void recordPolygonOffset(float factor, float units) {
        buffer.writePolygonOffset(factor, units);
    }

    public void recordPolygonMode(int face, int mode) {
        buffer.writePolygonMode(face, mode);
    }

    public void recordColorMaterial(int face, int mode) {
        buffer.writeColorMaterial(face, mode);
    }

    public void recordLogicOp(int opcode) {
        buffer.writeLogicOp(opcode);
    }

    public void recordActiveTexture(int texture) {
        buffer.writeActiveTexture(texture);
    }

    public void recordUseProgram(int program) {
        buffer.writeUseProgram(program);
    }

    public void recordPushAttrib(int mask) {
        buffer.writePushAttrib(mask);
    }

    public void recordPopAttrib() {
        buffer.writePopAttrib();
    }

    public void recordFogf(int pname, float param) {
        buffer.writeFogf(pname, param);
    }

    public void recordFogi(int pname, int param) {
        buffer.writeFogi(pname, param);
    }

    public void recordHint(int target, int mode) {
        buffer.writeHint(target, mode);
    }

    public void recordFog(int pname, FloatBuffer params) {
        buffer.writeFog(pname, params);
    }

    public void recordLightf(int light, int pname, float param) {
        buffer.writeLightf(light, pname, param);
    }

    public void recordLighti(int light, int pname, int param) {
        buffer.writeLighti(light, pname, param);
    }

    public void recordLight(int light, int pname, FloatBuffer params) {
        buffer.writeLight(light, pname, params);
    }

    public void recordLightModelf(int pname, float param) {
        buffer.writeLightModelf(pname, param);
    }

    public void recordLightModeli(int pname, int param) {
        buffer.writeLightModeli(pname, param);
    }

    public void recordLightModel(int pname, FloatBuffer params) {
        buffer.writeLightModel(pname, params);
    }

    public void recordMaterialf(int face, int pname, float val) {
        buffer.writeMaterialf(face, pname, val);
    }

    public void recordMaterial(int face, int pname, FloatBuffer params) {
        buffer.writeMaterial(face, pname, params);
    }

    public void recordClipPlane(int plane, double a, double b, double c, double d) {
        buffer.writeClipPlane(plane, a, b, c, d);
    }

    public void recordStencilFunc(int func, int ref, int mask) {
        buffer.writeStencilFunc(func, ref, mask);
    }

    public void recordStencilFuncSeparate(int face, int func, int ref, int mask) {
        buffer.writeStencilFuncSeparate(face, func, ref, mask);
    }

    public void recordStencilOp(int fail, int zfail, int zpass) {
        buffer.writeStencilOp(fail, zfail, zpass);
    }

    public void recordStencilOpSeparate(int face, int sfail, int dpfail, int dppass) {
        buffer.writeStencilOpSeparate(face, sfail, dpfail, dppass);
    }

    public void recordStencilMask(int mask) {
        buffer.writeStencilMask(mask);
    }

    public void recordStencilMaskSeparate(int face, int mask) {
        buffer.writeStencilMaskSeparate(face, mask);
    }

    public void recordCallList(int listId) {
        buffer.writeCallList(listId);
    }

    public void recordDrawBuffer(int mode) {
        buffer.writeDrawBuffer(mode);
    }

    public void recordDrawBuffers(int count, int buf) {
        buffer.writeDrawBuffers(count, buf);
    }

    public void recordDrawBuffers(int count, IntBuffer bufs) {
        buffer.writeDrawBuffers(count, bufs);
    }

    public void recordBindVBO(int vbo) {
        buffer.writeBindVBO(vbo);
    }

    public void recordBindVAO(int vao) {
        buffer.writeBindVAO(vao);
    }

    public void recordComplexCommand(DisplayListCommand cmd) {
        buffer.writeComplexRef(cmd);
    }
}
