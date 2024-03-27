#version 330 core

uniform mat4 u_ModelProjection;
uniform float u_SectionHeight;
uniform int u_BaseY;
uniform vec2 u_UV[2]; // {min, max}

out vec2 v_TexCoord;
out float v_yPos; // The y-position of the vertex in world space

const int QUAD_INDICES[] = int[] (0, 1, 2, 0, 2, 3);

const float LONG_DISTANCE = 0.447076585625; // (1.0 + sqrt(2.0)) / 5.4
/** Distance from center to end of parallel side */
const float SHORT_DISTANCE = 0.185185185185; // 1.0f / 5.4f

const float EDGE_X[8] = float[] ( LONG_DISTANCE, LONG_DISTANCE, SHORT_DISTANCE, -SHORT_DISTANCE,
-LONG_DISTANCE, -LONG_DISTANCE, -SHORT_DISTANCE, SHORT_DISTANCE );
const float EDGE_Z[8] = float[] ( SHORT_DISTANCE, -SHORT_DISTANCE, -LONG_DISTANCE, -LONG_DISTANCE,
-SHORT_DISTANCE, SHORT_DISTANCE, LONG_DISTANCE, LONG_DISTANCE );
const float X_OFFSETS[4] = float[] (0.0f, 0.0f, 1.0f, 1.0f);
const float Z_OFFSETS[4] = float[] (0.0f, 1.0f, 1.0f, 0.0f);

const float CABLE_HEIGHT = 512.0f;
const float SIDE = 2.0f / 5.4f;
const float SECTION_HEIGHT = 8 * SIDE;
const int SECTIONS = int(ceil(CABLE_HEIGHT / SECTION_HEIGHT));

int imod(int a, int d) {
    return a - ((a / d) * d);
}

void main() {
    vec3 a_Pos;
    vec2 a_TexCoord;

    // Decompose the vertex ID into identifiers of the different "loops" involved in the helix construction
    int id = gl_VertexID;
    int quadRawVtxIdx = imod(id, 6);
    int quadVtxIdx = QUAD_INDICES[quadRawVtxIdx];
    id = (id - quadRawVtxIdx) / 6;
    int inPart = imod(id, 8);
    int inPartP1 = imod(inPart + 1, 8);
    id = (id - inPart) / 8;
    int inStrand = imod(id, 4);
    id = (id - inStrand) / 4;

    int curSegment = id;

    a_Pos = vec3(
        0.5 + EDGE_X[(quadVtxIdx < 2) ? inPartP1 : inPart],
        SIDE * inPart + ((quadVtxIdx < 2) ? SIDE : 0.0) + ((quadVtxIdx > 0 && quadVtxIdx < 3) ? 0.75 : 0.0),
        0.5 + EDGE_Z[(quadVtxIdx < 2) ? inPartP1 : inPart]
    );
    a_TexCoord = vec2(
        (quadVtxIdx < 2) ? u_UV[0].x : u_UV[1].x,
        (quadVtxIdx > 0 && quadVtxIdx < 3) ? u_UV[0].y : u_UV[1].y
    );

    mat4 strandTranslation = transpose(mat4(
        1,0,0,X_OFFSETS[inStrand],
        0,1,0,0,
        0,0,1,Z_OFFSETS[inStrand],
        0,0,0,1
    ));
    // rotation around Y by rotY degrees
    float rotY = 90.0 * inStrand;
    float cosY = cos(radians(rotY));
    float sinY = sin(radians(rotY));
    mat4 strandRotation = transpose(mat4(
        cosY ,0,sinY,0,
        0    ,1,0   ,0,
        -sinY,0,cosY,0,
        0    ,0,0   ,1
    ));

    vec4 pos = vec4(a_Pos, 1.0);
    pos.y += u_SectionHeight * float(curSegment);

    vec4 projectedPosition = u_ModelProjection * strandTranslation * strandRotation * pos;

    gl_Position = projectedPosition;

    v_TexCoord = a_TexCoord;
    v_yPos = max(u_BaseY + u_SectionHeight * float(curSegment), 0.0);
}
