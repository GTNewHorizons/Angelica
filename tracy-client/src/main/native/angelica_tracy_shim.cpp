#include <cstddef>
#include <cstdint>
#include <cstring>

#include "tracy/TracyC.h"
#include "client/TracyProfiler.hpp"
#include "common/TracyApi.h"


static_assert(offsetof(TracyCZoneCtx, active) == 4, "TracyClientBackend.ACTIVE_OFFSET reads the active flag at a fixed offset of 4");

// Shim to cover things not reachable, or not efficiently usable, from Java; including header-inlined functions.
extern "C" {

TRACY_API uint64_t ang_zone_ctx_size(void) {
    return (uint64_t)sizeof(TracyCZoneCtx);
}

TRACY_API void ang_zone_begin(const struct ___tracy_source_location_data* srcloc, int32_t active, void* out) {
    *(TracyCZoneCtx*)out = ___tracy_emit_zone_begin(srcloc, active);
}

TRACY_API void ang_zone_end(const void* ctx) {
    ___tracy_emit_zone_end(*(const TracyCZoneCtx*)ctx);
}

TRACY_API void ang_zone_text(const void* ctx, const char* txt, uint64_t size) {
    ___tracy_emit_zone_text(*(const TracyCZoneCtx*)ctx, txt, (size_t)size);
}

TRACY_API void ang_zone_value(const void* ctx, uint64_t value) {
    ___tracy_emit_zone_value(*(const TracyCZoneCtx*)ctx, value);
}

TRACY_API void ang_plot(const char* name, int64_t valueBits) {
    double value;
    memcpy(&value, &valueBits, sizeof(value));
    ___tracy_emit_plot(name, value);
}

TRACY_API uint32_t ang_section_enter(uint32_t category, const char* txt) {
    return tracy::Profiler::SectionEnter((uint16_t)category, "%s", txt);
}

TRACY_API void ang_section_leave(uint32_t id) {
    tracy::Profiler::SectionLeave(id);
}

TRACY_API void ang_section_setup(uint32_t category, const char* txt) {
    tracy::Profiler::SectionSetup((uint16_t)category, "%s", txt);
}

}
