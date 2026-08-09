package com.gtnewhorizons.angelica.sdlgpu.resource;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL44;

public final class BufferParams {
    private BufferParams() {}

    public static final int MUTABLE_STORE = -1;

    public static int resolve(int pname, long size, int glUsage, int storageFlags, boolean mapped, int accessFlags, long mapOffset, long mapLength) {
        return switch (pname) {
            case GL15.GL_BUFFER_SIZE -> (int) size;
            case GL15.GL_BUFFER_USAGE -> glUsage;
            case GL44.GL_BUFFER_IMMUTABLE_STORAGE -> storageFlags >= 0 ? GL11.GL_TRUE : GL11.GL_FALSE;
            case GL44.GL_BUFFER_STORAGE_FLAGS -> storageFlags >= 0 ? storageFlags : 0;
            case GL15.GL_BUFFER_MAPPED -> mapped ? GL11.GL_TRUE : GL11.GL_FALSE;
            case GL15.GL_BUFFER_ACCESS -> accessBitsToEnum(mapped ? accessFlags : 0);
            case GL30.GL_BUFFER_ACCESS_FLAGS -> mapped ? accessFlags : 0;
            case GL30.GL_BUFFER_MAP_OFFSET -> mapped ? (int) mapOffset : 0;
            case GL30.GL_BUFFER_MAP_LENGTH -> mapped ? (int) mapLength : 0;
            default -> 0;
        };
    }

    public static boolean isUnknownPname(int pname) {
        return switch (pname) {
            case GL15.GL_BUFFER_SIZE, GL15.GL_BUFFER_USAGE, GL15.GL_BUFFER_MAPPED, GL15.GL_BUFFER_ACCESS,
                 GL30.GL_BUFFER_ACCESS_FLAGS, GL30.GL_BUFFER_MAP_OFFSET, GL30.GL_BUFFER_MAP_LENGTH,
                 GL44.GL_BUFFER_IMMUTABLE_STORAGE, GL44.GL_BUFFER_STORAGE_FLAGS -> false;
            default -> true;
        };
    }

    public static int accessEnumToBits(int access) {
        return switch (access) {
            case GL15.GL_READ_ONLY -> GL30.GL_MAP_READ_BIT;
            case GL15.GL_WRITE_ONLY -> GL30.GL_MAP_WRITE_BIT;
            default -> GL30.GL_MAP_READ_BIT | GL30.GL_MAP_WRITE_BIT;
        };
    }

    public static int accessBitsToEnum(int accessFlags) {
        return switch (accessFlags & (GL30.GL_MAP_READ_BIT | GL30.GL_MAP_WRITE_BIT)) {
            case GL30.GL_MAP_READ_BIT -> GL15.GL_READ_ONLY;
            case GL30.GL_MAP_WRITE_BIT -> GL15.GL_WRITE_ONLY;
            default -> GL15.GL_READ_WRITE;
        };
    }
}
