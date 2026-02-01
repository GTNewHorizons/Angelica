package com.gtnewhorizons.angelica.glsm.recording;

import static com.gtnewhorizon.gtnhlib.bytebuf.MemoryUtilities.memGetInt;

/**
 * Command opcodes for ByteBuffer-based display list serialization.
 * Each command has a fixed layout described in comments.
 * All values are 4-byte aligned.
 */
public final class GLCommand {
    private GLCommand() {}

    // === Single int commands (8 bytes: opcode + param) ===
    public static final int ENABLE = 1;              // [cmd:4][cap:4]
    public static final int DISABLE = 2;             // [cmd:4][cap:4]
    public static final int CLEAR = 3;               // [cmd:4][mask:4]
    public static final int CLEAR_STENCIL = 4;       // [cmd:4][s:4]
    public static final int CULL_FACE = 5;           // [cmd:4][mode:4]
    public static final int DEPTH_FUNC = 6;          // [cmd:4][func:4]
    public static final int SHADE_MODEL = 7;         // [cmd:4][mode:4]
    public static final int LOGIC_OP = 8;            // [cmd:4][opcode:4]
    public static final int MATRIX_MODE = 9;         // [cmd:4][mode:4]
    public static final int ACTIVE_TEXTURE = 10;     // [cmd:4][texture:4]
    public static final int USE_PROGRAM = 11;        // [cmd:4][program:4]
    public static final int PUSH_ATTRIB = 12;        // [cmd:4][mask:4]
    public static final int POP_ATTRIB = 13;         // [cmd:4][unused:4]
    public static final int LOAD_IDENTITY = 14;      // [cmd:4][matrixMode:4]
    public static final int PUSH_MATRIX = 15;        // [cmd:4][matrixMode:4]
    public static final int POP_MATRIX = 16;         // [cmd:4][matrixMode:4]
    public static final int STENCIL_MASK = 17;       // [cmd:4][mask:4]
    public static final int DEPTH_MASK = 18;         // [cmd:4][flag:4] (0 or 1)
    public static final int FRONT_FACE = 19;         // [cmd:4][mode:4]

    // === Two int commands (12 bytes) ===
    public static final int BIND_TEXTURE = 20;       // [cmd:4][target:4][texture:4]
    public static final int POLYGON_MODE = 21;       // [cmd:4][face:4][mode:4]
    public static final int COLOR_MATERIAL = 22;     // [cmd:4][face:4][mode:4]
    public static final int LINE_STIPPLE = 23;       // [cmd:4][factor:4][pattern:4]
    public static final int STENCIL_MASK_SEPARATE = 24; // [cmd:4][face:4][mask:4]
    public static final int FOGI = 25;               // [cmd:4][pname:4][param:4]
    public static final int HINT = 26;               // [cmd:4][target:4][mode:4]

    // === Three int commands (16 bytes) ===
    public static final int STENCIL_FUNC = 30;       // [cmd:4][func:4][ref:4][mask:4]
    public static final int STENCIL_OP = 31;         // [cmd:4][fail:4][zfail:4][zpass:4]
    public static final int TEX_PARAMETERI = 32;     // [cmd:4][target:4][pname:4][param:4]

    // === Four int commands (20 bytes) ===
    public static final int VIEWPORT = 40;           // [cmd:4][x:4][y:4][width:4][height:4]
    public static final int BLEND_FUNC = 41;         // [cmd:4][srcRgb:4][dstRgb:4][srcAlpha:4][dstAlpha:4]
    public static final int COLOR_MASK = 42;         // [cmd:4][r:4][g:4][b:4][a:4] (0 or 1 each)
    public static final int STENCIL_FUNC_SEPARATE = 43; // [cmd:4][face:4][func:4][ref:4][mask:4]
    public static final int STENCIL_OP_SEPARATE = 44; // [cmd:4][face:4][sfail:4][dpfail:4][dppass:4]

    // === Float commands ===
    public static final int POINT_SIZE = 50;         // [cmd:4][size:4f]
    public static final int LINE_WIDTH = 51;         // [cmd:4][width:4f]
    public static final int POLYGON_OFFSET = 52;     // [cmd:4][factor:4f][units:4f]
    public static final int NORMAL = 53;             // [cmd:4][x:4f][y:4f][z:4f]
    public static final int COLOR = 54;              // [cmd:4][r:4f][g:4f][b:4f][a:4f]
    public static final int CLEAR_COLOR = 55;        // [cmd:4][r:4f][g:4f][b:4f][a:4f]
    public static final int BLEND_COLOR = 56;        // [cmd:4][r:4f][g:4f][b:4f][a:4f]

    // === Mixed int+float commands ===
    public static final int ALPHA_FUNC = 60;         // [cmd:4][func:4][ref:4f]
    public static final int FOGF = 61;               // [cmd:4][pname:4][param:4f]
    public static final int LIGHTF = 62;             // [cmd:4][light:4][pname:4][param:4f]
    public static final int LIGHT_MODELF = 63;       // [cmd:4][pname:4][param:4f]
    public static final int LIGHTI = 64;             // [cmd:4][light:4][pname:4][param:4]
    public static final int LIGHT_MODELI = 65;       // [cmd:4][pname:4][param:4]
    public static final int MATERIALF = 66;          // [cmd:4][face:4][pname:4][param:4f]
    public static final int TEX_PARAMETERF = 67;     // [cmd:4][target:4][pname:4][param:4f]

    // === Double commands ===
    public static final int TRANSLATE = 70;          // [cmd:4][mode:4][x:8d][y:8d][z:8d] = 32 bytes
    public static final int ROTATE = 71;             // [cmd:4][mode:4][angle:8d][x:8d][y:8d][z:8d] = 40 bytes
    public static final int SCALE = 72;              // [cmd:4][mode:4][x:8d][y:8d][z:8d] = 32 bytes
    public static final int ORTHO = 73;              // [cmd:4][left:8d][right:8d][bottom:8d][top:8d][zNear:8d][zFar:8d] = 52 bytes
    public static final int FRUSTUM = 74;            // [cmd:4][left:8d][right:8d][bottom:8d][top:8d][zNear:8d][zFar:8d] = 52 bytes
    public static final int CLEAR_DEPTH = 75;        // [cmd:4][depth:8d] = 12 bytes

    // === Matrix commands (72 bytes) ===
    public static final int MULT_MATRIX = 80;        // [cmd:4][mode:4][m00-m33:64f] = 72 bytes
    public static final int LOAD_MATRIX = 81;        // [cmd:4][mode:4][m00-m33:64f] = 72 bytes

    // === Buffer commands (inline 4 floats max) ===
    public static final int FOG = 90;                // [cmd:4][pname:4][count:4][params:16f] = 28 bytes
    public static final int LIGHT = 91;              // [cmd:4][light:4][pname:4][count:4][params:16f] = 32 bytes
    public static final int LIGHT_MODEL = 92;        // [cmd:4][pname:4][count:4][params:16f] = 28 bytes
    public static final int MATERIAL = 93;           // [cmd:4][face:4][pname:4][count:4][params:16f] = 32 bytes

    // === Draw commands ===
    public static final int DRAW_RANGE = 100;        // [cmd:4][vboIndex:4] = 8 bytes
    public static final int DRAW_RANGE_RESTORE = 101; // [cmd:4][vboIndex:4][flags:4][color:4][normal:4][lastTexCoord:8f] = 28 bytes
    public static final int CALL_LIST = 102;         // [cmd:4][listId:4] = 8 bytes


    public static final int DRAW_ARRAYS = 110;       // [cmd:4][drawMode:4][start:4][count:4] = 20 bytes
    public static final int DRAW_ELEMENTS = 111;     // [cmd:4][mode:4][indices_count:4][type:4][indices_buffer_offset:8] = 28 bytes
    public static final int DRAW_BUFFER = 112;       // [cmd:4][mode:4] = 8 bytes
    public static final int DRAW_BUFFERS = 113;      // [cmd:4][count:4][bufs:4*8] = 40 bytes (up to 8 buffers)
    /**
     * Draw VBO range with attribute restoration after draw.
     * Used for immediate mode VBOs to restore GL current state (color, normal, texcoord).
     */


    // === Bind commands ===
    public static final int BIND_VBO = 120;
    public static final int BIND_VAO = 121;

    // === Complex object reference ===
    public static final int COMPLEX_REF = 255;       // [cmd:4][index:4] = 8 bytes

    /**
     * Get a human-readable name for an opcode (for debugging).
     */
    public static String getName(int opcode) {
        return switch (opcode) {
            case ENABLE -> "ENABLE";
            case DISABLE -> "DISABLE";
            case CLEAR -> "CLEAR";
            case CLEAR_STENCIL -> "CLEAR_STENCIL";
            case CULL_FACE -> "CULL_FACE";
            case DEPTH_FUNC -> "DEPTH_FUNC";
            case SHADE_MODEL -> "SHADE_MODEL";
            case LOGIC_OP -> "LOGIC_OP";
            case MATRIX_MODE -> "MATRIX_MODE";
            case ACTIVE_TEXTURE -> "ACTIVE_TEXTURE";
            case USE_PROGRAM -> "USE_PROGRAM";
            case PUSH_ATTRIB -> "PUSH_ATTRIB";
            case POP_ATTRIB -> "POP_ATTRIB";
            case LOAD_IDENTITY -> "LOAD_IDENTITY";
            case PUSH_MATRIX -> "PUSH_MATRIX";
            case POP_MATRIX -> "POP_MATRIX";
            case STENCIL_MASK -> "STENCIL_MASK";
            case DEPTH_MASK -> "DEPTH_MASK";
            case FRONT_FACE -> "FRONT_FACE";
            case BIND_TEXTURE -> "BIND_TEXTURE";
            case POLYGON_MODE -> "POLYGON_MODE";
            case COLOR_MATERIAL -> "COLOR_MATERIAL";
            case LINE_STIPPLE -> "LINE_STIPPLE";
            case STENCIL_MASK_SEPARATE -> "STENCIL_MASK_SEPARATE";
            case FOGI -> "FOGI";
            case HINT -> "HINT";
            case STENCIL_FUNC -> "STENCIL_FUNC";
            case STENCIL_OP -> "STENCIL_OP";
            case TEX_PARAMETERI -> "TEX_PARAMETERI";
            case VIEWPORT -> "VIEWPORT";
            case BLEND_FUNC -> "BLEND_FUNC";
            case COLOR_MASK -> "COLOR_MASK";
            case STENCIL_FUNC_SEPARATE -> "STENCIL_FUNC_SEPARATE";
            case STENCIL_OP_SEPARATE -> "STENCIL_OP_SEPARATE";
            case POINT_SIZE -> "POINT_SIZE";
            case LINE_WIDTH -> "LINE_WIDTH";
            case POLYGON_OFFSET -> "POLYGON_OFFSET";
            case COLOR -> "COLOR";
            case CLEAR_COLOR -> "CLEAR_COLOR";
            case BLEND_COLOR -> "BLEND_COLOR";
            case ALPHA_FUNC -> "ALPHA_FUNC";
            case FOGF -> "FOGF";
            case LIGHTF -> "LIGHTF";
            case LIGHT_MODELF -> "LIGHT_MODELF";
            case LIGHTI -> "LIGHTI";
            case LIGHT_MODELI -> "LIGHT_MODELI";
            case MATERIALF -> "MATERIALF";
            case TEX_PARAMETERF -> "TEX_PARAMETERF";
            case TRANSLATE -> "TRANSLATE";
            case ROTATE -> "ROTATE";
            case SCALE -> "SCALE";
            case ORTHO -> "ORTHO";
            case FRUSTUM -> "FRUSTUM";
            case CLEAR_DEPTH -> "CLEAR_DEPTH";
            case MULT_MATRIX -> "MULT_MATRIX";
            case LOAD_MATRIX -> "LOAD_MATRIX";
            case FOG -> "FOG";
            case LIGHT -> "LIGHT";
            case LIGHT_MODEL -> "LIGHT_MODEL";
            case MATERIAL -> "MATERIAL";
            case DRAW_RANGE -> "DRAW_RANGE";
            case CALL_LIST -> "CALL_LIST";
            case DRAW_BUFFER -> "DRAW_BUFFER";
            case DRAW_BUFFERS -> "DRAW_BUFFERS";
            case COMPLEX_REF -> "COMPLEX_REF";
            case DRAW_RANGE_RESTORE -> "DRAW_RANGE_RESTORE";
            case BIND_VAO -> "BIND_VAO";
            case BIND_VBO -> "BIND_VBO";
            default -> "UNKNOWN(" + opcode + ")";
        };
    }

    /**
     * Get size of a command in bytes (including the opcode).
     */
    public static int getCommandSize(int cmd, long ptr) {
        return switch (cmd) {
            // Opcode-only commands (4 bytes)
            case GLCommand.LOAD_IDENTITY, GLCommand.PUSH_MATRIX, GLCommand.POP_MATRIX -> 4;

            // Single int commands (8 bytes)
            case GLCommand.ENABLE, GLCommand.DISABLE, GLCommand.CLEAR, GLCommand.CLEAR_STENCIL,
                 GLCommand.CULL_FACE, GLCommand.DEPTH_FUNC, GLCommand.SHADE_MODEL, GLCommand.LOGIC_OP,
                 GLCommand.MATRIX_MODE, GLCommand.ACTIVE_TEXTURE, GLCommand.USE_PROGRAM,
                 GLCommand.PUSH_ATTRIB, GLCommand.POP_ATTRIB, GLCommand.STENCIL_MASK,
                 GLCommand.DEPTH_MASK, GLCommand.FRONT_FACE, GLCommand.POINT_SIZE, GLCommand.LINE_WIDTH,
                 GLCommand.CALL_LIST, GLCommand.COMPLEX_REF, GLCommand.DRAW_RANGE, GLCommand.BIND_VBO,
                 GLCommand.BIND_VAO -> 8;

            // Two int commands (12 bytes)
            case GLCommand.BIND_TEXTURE, GLCommand.POLYGON_MODE, GLCommand.COLOR_MATERIAL,
                 GLCommand.LINE_STIPPLE, GLCommand.STENCIL_MASK_SEPARATE, GLCommand.FOGI,
                 GLCommand.HINT, GLCommand.POLYGON_OFFSET, GLCommand.ALPHA_FUNC, GLCommand.FOGF,
                 GLCommand.LIGHT_MODELF, GLCommand.LIGHT_MODELI -> 12;

            // Three int commands (16 bytes)
            case GLCommand.STENCIL_FUNC, GLCommand.STENCIL_OP, GLCommand.TEX_PARAMETERI,
                 GLCommand.LIGHTF, GLCommand.LIGHTI,
                 GLCommand.MATERIALF, GLCommand.TEX_PARAMETERF, GLCommand.DRAW_ARRAYS -> 16;

            // Four int commands (20 bytes)
            case GLCommand.VIEWPORT, GLCommand.BLEND_FUNC, GLCommand.COLOR_MASK,
                 GLCommand.STENCIL_FUNC_SEPARATE, GLCommand.STENCIL_OP_SEPARATE,
                 GLCommand.COLOR, GLCommand.CLEAR_COLOR, GLCommand.BLEND_COLOR -> 20;

            case GLCommand.DRAW_ELEMENTS -> 24;

            // DRAW_RANGE_RESTORE: 28 bytes
            case GLCommand.DRAW_RANGE_RESTORE -> 28;

            // DRAW_BUFFER: 8 bytes [cmd:4][mode:4]
            case GLCommand.DRAW_BUFFER -> 8;

            // DRAW_BUFFERS: 40 bytes [cmd:4][count:4][bufs:4*8]
            case GLCommand.DRAW_BUFFERS -> 40;

            // Double commands
            case GLCommand.TRANSLATE, GLCommand.SCALE -> 28;  // cmd + 3 doubles
            case GLCommand.CLEAR_DEPTH -> 12;  // cmd + 1 double
            case GLCommand.ROTATE -> 36;  // cmd + 4 doubles
            case GLCommand.ORTHO, GLCommand.FRUSTUM -> 52;  // cmd + 6 doubles

            // Matrix commands
            case GLCommand.MULT_MATRIX, GLCommand.LOAD_MATRIX -> 68;  // cmd + 16 floats

            // Buffer commands (variable, read count)
            case GLCommand.FOG, GLCommand.LIGHT_MODEL -> {
                final int count = memGetInt(ptr + 8);  // pname at +4, count at +8
                yield 12 + count * 4;
            }
            case GLCommand.LIGHT, GLCommand.MATERIAL -> {
                final int count = memGetInt(ptr + 12);  // light/face at +4, pname at +8, count at +12
                yield 16 + count * 4;
            }

            default -> throw new IllegalStateException("Unknown command: " + cmd);
        };
    }
}
