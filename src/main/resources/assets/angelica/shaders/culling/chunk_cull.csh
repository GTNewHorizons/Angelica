#version 430 core

layout(local_size_x = 64) in;

struct Section {
    ivec4 origin;   // x, y, z (block coords), sliceMask
    uvec4 posts0;   // vertex fence posts 0..3
    uvec4 posts1;   // vertex fence posts 4..7; facing f spans posts [f, f+1)
    uvec4 extra;    // .x = section index base in elements (0 when unsorted), .yzw unused
};

uint post(Section s, uint i) {
    return i < 4u ? s.posts0[i] : s.posts1[i - 4u];
}

struct DrawElementsIndirectCommand {
    uint count;
    uint instanceCount;
    uint firstIndex;
    int  baseVertex;
    uint baseInstance;
};

layout(std430, binding = 0) readonly buffer VisibleIds {
    uvec2 entries[];
} visible;

layout(std430, binding = 1) readonly buffer SectionMeta {
    Section sections[];
} meta;

layout(std430, binding = 2) writeonly buffer IndirectCmds {
    DrawElementsIndirectCommand cmds[];
} indirect;

layout(std140, binding = 1) uniform Frustum {
    vec4 planes[6];     // camera-relative planes from `proj * mv` (no translate baked in)
    //   control.x = visibleCount (iteration bound)
    //   control.y = indexPointerMask (0 non-sorted -> firstIndex masked to 0; 0xFFFFFFFF sorted)
    //   control.z = bypassFrustum (shadow passes; renderLists already carry the caster set)
    uvec4 control;
    //   cameraWorld.xyz = current-frame camera (subtract from world AABB for frustum test)
    vec4 cameraWorld;
    //   batch.x = visible-list entryBase for this dispatch (batched culls)
    uvec4 batch;
    //   pyr.zw = vertices and index elements per primitive, from ChunkPrimitiveType (exact as floats)
    vec4 pyr;
} frustum;

bool insideFrustum(vec3 minP, vec3 maxP) {
    // Planes are camera-relative; reduce world AABB to camera-relative before the n-vertex test.
    vec3 cam  = frustum.cameraWorld.xyz;
    vec3 minR = minP - cam;
    vec3 maxR = maxP - cam;
    for (int i = 0; i < 6; i++) {
        vec4 p = frustum.planes[i];
        vec3 ext = vec3(p.x > 0.0 ? maxR.x : minR.x,
                        p.y > 0.0 ? maxR.y : minR.y,
                        p.z > 0.0 ? maxR.z : minR.z);
        if (dot(p.xyz, ext) + p.w < 0.0) return false;
    }
    return true;
}

void main() {
    uint gid = gl_GlobalInvocationID.x;
    if (gid >= frustum.control.x) return;

    uint entryIdx    = frustum.batch.x + gid;
    uvec2 entry      = visible.entries[entryIdx];
    uint slot        = entry.x;
    uint packedEntry = entry.y;
    uint facingMask  = packedEntry & 0xFFu;
    uint outputBase  = packedEntry >> 8;

    Section s = meta.sections[slot];
    vec3 minP = vec3(s.origin.xyz);
    vec3 maxP = minP + vec3(16.0);

    bool inside = (frustum.control.z != 0u) || insideFrustum(minP, maxP);

    uint indexMask = frustum.control.y;
    uint vertsPerPrim = uint(frustum.pyr.z);
    uint elemsPerPrim = uint(frustum.pyr.w);
    uint sectionBase = post(s, 0u);

    bool mergeRuns = indexMask == 0u;
    uint mask = facingMask & 0x7Fu;

    uint localIdx = 0u;
    while (mask != 0u) {
        uint first  = uint(findLSB(mask));
        uint length = mergeRuns ? uint(findLSB(~(mask >> first))) : 1u;
        uint startV = post(s, first);
        uint endV   = post(s, first + length);

        DrawElementsIndirectCommand c;
        c.count         = inside ? ((endV - startV) / vertsPerPrim) * elemsPerPrim : 0u;
        c.instanceCount = inside ? 1u : 0u;
        c.firstIndex    = (s.extra.x + ((startV - sectionBase) / vertsPerPrim) * elemsPerPrim) & indexMask;
        c.baseVertex    = int(startV);
        c.baseInstance  = 0u;
        indirect.cmds[outputBase + localIdx] = c;
        localIdx++;

        mask &= ~(((1u << length) - 1u) << first);
    }
}
