package com.gtnewhorizons.angelica.glsm.recording;

import com.gtnewhorizons.angelica.glsm.recording.commands.DisplayListCommand;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Records GL commands to a CommandBuffer during display list compilation.
 */
public class CommandRecorder {
    private final CommandBuffer buffer;
    private int commandCount;

    public CommandRecorder() {
        this.buffer = new CommandBuffer();
        this.commandCount = 0;
    }

    public CommandBuffer getBuffer() {
        return buffer;
    }

    public int getCommandCount() {
        return commandCount;
    }

    public void free() {
        buffer.free();
    }

    // ==================== Recording Methods ====================

    public void recordEnable(int cap) {
        buffer.writeEnable(cap);
        commandCount++;
    }

    public void recordDisable(int cap) {
        buffer.writeDisable(cap);
        commandCount++;
    }

    public void recordClear(int mask) {
        buffer.writeClear(mask);
        commandCount++;
    }

    public void recordClearColor(float r, float g, float b, float a) {
        buffer.writeClearColor(r, g, b, a);
        commandCount++;
    }

    public void recordClearDepth(double depth) {
        buffer.writeClearDepth(depth);
        commandCount++;
    }

    public void recordBlendColor(float r, float g, float b, float a) {
        buffer.writeBlendColor(r, g, b, a);
        commandCount++;
    }

    public void recordClearStencil(int s) {
        buffer.writeClearStencil(s);
        commandCount++;
    }

    public void recordColor(float r, float g, float b, float a) {
        buffer.writeColor(r, g, b, a);
        commandCount++;
    }

    public void recordColorMask(boolean r, boolean g, boolean b, boolean a) {
        buffer.writeColorMask(r, g, b, a);
        commandCount++;
    }

    public void recordDepthMask(boolean flag) {
        buffer.writeDepthMask(flag);
        commandCount++;
    }

    public void recordFrontFace(int mode) {
        buffer.writeFrontFace(mode);
        commandCount++;
    }

    public void recordDepthFunc(int func) {
        buffer.writeDepthFunc(func);
        commandCount++;
    }

    public void recordBlendFunc(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        buffer.writeBlendFunc(srcRgb, dstRgb, srcAlpha, dstAlpha);
        commandCount++;
    }

    public void recordAlphaFunc(int func, float ref) {
        buffer.writeAlphaFunc(func, ref);
        commandCount++;
    }

    public void recordCullFace(int mode) {
        buffer.writeCullFace(mode);
        commandCount++;
    }

    public void recordShadeModel(int mode) {
        buffer.writeShadeModel(mode);
        commandCount++;
    }

    public void recordBindTexture(int target, int texture) {
        buffer.writeBindTexture(target, texture);
        commandCount++;
    }

    public void recordTexParameteri(int target, int pname, int param) {
        buffer.writeTexParameteri(target, pname, param);
        commandCount++;
    }

    public void recordTexParameterf(int target, int pname, float param) {
        buffer.writeTexParameterf(target, pname, param);
        commandCount++;
    }

    public void recordMatrixMode(int mode) {
        buffer.writeMatrixMode(mode);
        commandCount++;
    }

    public void recordLoadIdentity(int matrixMode) {
        buffer.writeLoadIdentity(matrixMode);
        commandCount++;
    }

    public void recordPushMatrix(int matrixMode) {
        buffer.writePushMatrix(matrixMode);
        commandCount++;
    }

    public void recordPopMatrix(int matrixMode) {
        buffer.writePopMatrix(matrixMode);
        commandCount++;
    }

    public void recordTranslate(int matrixMode, double x, double y, double z) {
        buffer.writeTranslate(matrixMode, x, y, z);
        commandCount++;
    }

    public void recordRotate(int matrixMode, double angle, double x, double y, double z) {
        buffer.writeRotate(matrixMode, angle, x, y, z);
        commandCount++;
    }

    public void recordScale(int matrixMode, double x, double y, double z) {
        buffer.writeScale(matrixMode, x, y, z);
        commandCount++;
    }

    public void recordMultMatrix(int matrixMode, Matrix4f matrix) {
        buffer.writeMultMatrix(matrixMode, matrix);
        commandCount++;
    }

    public void recordLoadMatrix(int matrixMode, Matrix4f matrix) {
        buffer.writeLoadMatrix(matrixMode, matrix);
        commandCount++;
    }

    public void recordOrtho(double left, double right, double bottom, double top, double zNear, double zFar) {
        buffer.writeOrtho(left, right, bottom, top, zNear, zFar);
        commandCount++;
    }

    public void recordFrustum(double left, double right, double bottom, double top, double zNear, double zFar) {
        buffer.writeFrustum(left, right, bottom, top, zNear, zFar);
        commandCount++;
    }

    public void recordViewport(int x, int y, int width, int height) {
        buffer.writeViewport(x, y, width, height);
        commandCount++;
    }

    public void recordPointSize(float size) {
        buffer.writePointSize(size);
        commandCount++;
    }

    public void recordLineWidth(float width) {
        buffer.writeLineWidth(width);
        commandCount++;
    }

    public void recordLineStipple(int factor, int pattern) {
        buffer.writeLineStipple(factor, pattern);
        commandCount++;
    }

    public void recordPolygonOffset(float factor, float units) {
        buffer.writePolygonOffset(factor, units);
        commandCount++;
    }

    public void recordPolygonMode(int face, int mode) {
        buffer.writePolygonMode(face, mode);
        commandCount++;
    }

    public void recordColorMaterial(int face, int mode) {
        buffer.writeColorMaterial(face, mode);
        commandCount++;
    }

    public void recordLogicOp(int opcode) {
        buffer.writeLogicOp(opcode);
        commandCount++;
    }

    public void recordActiveTexture(int texture) {
        buffer.writeActiveTexture(texture);
        commandCount++;
    }

    public void recordUseProgram(int program) {
        buffer.writeUseProgram(program);
        commandCount++;
    }

    public void recordPushAttrib(int mask) {
        buffer.writePushAttrib(mask);
        commandCount++;
    }

    public void recordPopAttrib() {
        buffer.writePopAttrib();
        commandCount++;
    }

    public void recordFogf(int pname, float param) {
        buffer.writeFogf(pname, param);
        commandCount++;
    }

    public void recordFogi(int pname, int param) {
        buffer.writeFogi(pname, param);
        commandCount++;
    }

    public void recordHint(int target, int mode) {
        buffer.writeHint(target, mode);
        commandCount++;
    }

    public void recordFog(int pname, FloatBuffer params) {
        buffer.writeFog(pname, params);
        commandCount++;
    }

    public void recordLightf(int light, int pname, float param) {
        buffer.writeLightf(light, pname, param);
        commandCount++;
    }

    public void recordLighti(int light, int pname, int param) {
        buffer.writeLighti(light, pname, param);
        commandCount++;
    }

    public void recordLight(int light, int pname, FloatBuffer params) {
        buffer.writeLight(light, pname, params);
        commandCount++;
    }

    public void recordLightModelf(int pname, float param) {
        buffer.writeLightModelf(pname, param);
        commandCount++;
    }

    public void recordLightModeli(int pname, int param) {
        buffer.writeLightModeli(pname, param);
        commandCount++;
    }

    public void recordLightModel(int pname, FloatBuffer params) {
        buffer.writeLightModel(pname, params);
        commandCount++;
    }

    public void recordMaterialf(int face, int pname, float val) {
        buffer.writeMaterialf(face, pname, val);
        commandCount++;
    }

    public void recordMaterial(int face, int pname, FloatBuffer params) {
        buffer.writeMaterial(face, pname, params);
        commandCount++;
    }

    public void recordStencilFunc(int func, int ref, int mask) {
        buffer.writeStencilFunc(func, ref, mask);
        commandCount++;
    }

    public void recordStencilFuncSeparate(int face, int func, int ref, int mask) {
        buffer.writeStencilFuncSeparate(face, func, ref, mask);
        commandCount++;
    }

    public void recordStencilOp(int fail, int zfail, int zpass) {
        buffer.writeStencilOp(fail, zfail, zpass);
        commandCount++;
    }

    public void recordStencilOpSeparate(int face, int sfail, int dpfail, int dppass) {
        buffer.writeStencilOpSeparate(face, sfail, dpfail, dppass);
        commandCount++;
    }

    public void recordStencilMask(int mask) {
        buffer.writeStencilMask(mask);
        commandCount++;
    }

    public void recordStencilMaskSeparate(int face, int mask) {
        buffer.writeStencilMaskSeparate(face, mask);
        commandCount++;
    }

    public void recordCallList(int listId) {
        buffer.writeCallList(listId);
        commandCount++;
    }

    public void recordDrawBuffer(int mode) {
        buffer.writeDrawBuffer(mode);
        commandCount++;
    }

    public void recordDrawBuffers(int count, int buf) {
        buffer.writeDrawBuffers(count, buf);
        commandCount++;
    }

    public void recordDrawBuffers(int count, IntBuffer bufs) {
        buffer.writeDrawBuffers(count, bufs);
        commandCount++;
    }

    public void recordComplexCommand(DisplayListCommand cmd) {
        buffer.writeComplexRef(cmd);
        commandCount++;
    }
}
