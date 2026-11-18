// Taken mostly from mesa

uint _vg_word(uint slot, uint i) {
    switch (slot) {
        case 0u: return _vg_vertices0.d[i];
        case 1u: return _vg_vertices1.d[i];
        case 2u: return _vg_vertices2.d[i];
        case 3u: return _vg_vertices3.d[i];
        default: return _vg_indices.d[i];
    }
}

uint _vg_read(uint slot, uint at, uint size) {
    uint shift = (at & 3u) * 8u;
    uint value = _vg_word(slot, at >> 2) >> shift;
    if (shift + size * 8u > 32u) value |= _vg_word(slot, (at >> 2) + 1u) << (32u - shift);
    return size == 4u ? value : value & ((1u << (size * 8u)) - 1u);
}

const uint _VG_COMPONENT_SIZE[12] = uint[12](4u, 2u, 1u, 1u, 2u, 2u, 4u, 4u, 4u, 4u, 4u, 4u);

bool _vg_enabled(uint a) {
    return (_vg_attr[a].x & 0x2000u) != 0u;
}

uint _vg_element(uint a, uint vertex, uint instance) {
    uvec4 desc = _vg_attr[a];
    uint divisor = desc.x >> 16;
    uint element = divisor == 0u ? vertex : instance / divisor;
    return element > desc.w ? 0u : element;
}

uvec4 _vg_raw(uint a, uint element) {
    uvec4 desc = _vg_attr[a];
    uint type = (desc.x >> 4) & 15u;
    uint at = desc.y + element * desc.z;
    bool none = (desc.x & 0x8000u) != 0u;
    if (type >= 9u) {
        uint packed = none ? 0u : _vg_read(desc.x & 7u, at, 4u);
        if (type == 9u) return uvec4(ivec4(int(packed << 22), int(packed << 12), int(packed << 2), int(packed)) >> ivec4(22, 22, 22, 30));
        if (type == 10u) return (uvec4(packed) >> uvec4(0u, 10u, 20u, 30u)) & uvec4(1023u, 1023u, 1023u, 3u);
        return uvec4(packed, 0u, 0u, 1u);
    }
    uint count = (desc.x >> 8) & 7u;
    uint size = _VG_COMPONENT_SIZE[type];
    uvec4 raw = uvec4(0u, 0u, 0u, 1u);
    for (uint c = 0u; c < count; c++) {
        uint v = none ? 0u : _vg_read(desc.x & 7u, at + c * size, size);
        if (type == 2u) v = uint(int(v << 24) >> 24);
        else if (type == 4u) v = uint(int(v << 16) >> 16);
        raw[c] = v;
    }
    return raw;
}

vec4 _vg_float(uint a, uint vertex, uint instance) {
    if (!_vg_enabled(a)) return _vg_attrDefault[a];
    uvec4 desc = _vg_attr[a];
    uint type = (desc.x >> 4) & 15u;
    uint count = (desc.x >> 8) & 7u;
    bool normalized = (desc.x & 0x800u) != 0u;
    uvec4 raw = _vg_raw(a, _vg_element(a, vertex, instance));
    vec4 v = vec4(0.0, 0.0, 0.0, 1.0);
    if (type == 9u) {
        vec4 c = vec4(ivec4(raw));
        v = normalized ? max(c / vec4(511.0, 511.0, 511.0, 1.0), vec4(-1.0)) : c;
    } else if (type == 10u) {
        vec4 c = vec4(raw);
        v = normalized ? c / vec4(1023.0, 1023.0, 1023.0, 3.0) : c;
    } else if (type == 11u) {
        uint p = raw.x;
        v = vec4(unpackHalf2x16((p & 0x7ffu) << 4).x, unpackHalf2x16((p & 0x3ff800u) >> 7).x,
                 unpackHalf2x16((p & 0xffc00000u) >> 17).x, 1.0);
    } else {
        for (uint c = 0u; c < count; c++) {
            uint r = raw[c];
            switch (type) {
                case 0u: v[c] = uintBitsToFloat(r); break;
                case 1u: v[c] = unpackHalf2x16(r).x; break;
                case 2u: v[c] = normalized ? max(float(int(r)) / 127.0, -1.0) : float(int(r)); break;
                case 3u: v[c] = normalized ? float(r) / 255.0 : float(r); break;
                case 4u: v[c] = normalized ? max(float(int(r)) / 32767.0, -1.0) : float(int(r)); break;
                case 5u: v[c] = normalized ? float(r) / 65535.0 : float(r); break;
                case 6u: v[c] = normalized ? max(float(int(r)) / 2147483647.0, -1.0) : float(int(r)); break;
                case 7u: v[c] = normalized ? float(r) / 4294967295.0 : float(r); break;
                default: v[c] = float(int(r)) / 65536.0; break;
            }
        }
    }
    return (desc.x & 0x4000u) != 0u ? v.zyxw : v;
}

ivec4 _vg_int(uint a, uint vertex, uint instance) {
    if (!_vg_enabled(a)) return ivec4(_vg_attrDefault[a]);
    if ((_vg_attr[a].x & 0x1000u) == 0u) return ivec4(_vg_float(a, vertex, instance));
    return ivec4(_vg_raw(a, _vg_element(a, vertex, instance)));
}

uvec4 _vg_uint(uint a, uint vertex, uint instance) {
    if (!_vg_enabled(a)) return uvec4(_vg_attrDefault[a]);
    if ((_vg_attr[a].x & 0x1000u) == 0u) return uvec4(_vg_float(a, vertex, instance));
    return _vg_raw(a, _vg_element(a, vertex, instance));
}
