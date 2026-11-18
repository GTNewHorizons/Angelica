cmake_minimum_required(VERSION 3.16)
# 13.3: TracyServer uses floating-point std::to_chars, unavailable in older libc++
set(CMAKE_OSX_DEPLOYMENT_TARGET 13.3)
project(angelica-tracy-capture LANGUAGES C CXX)

if(NOT DEFINED TRACY_SRC_DIR)
    message(FATAL_ERROR "TRACY_SRC_DIR must be set to a Tracy source checkout")
endif()

set(NO_ISA_EXTENSIONS ON)
set(NO_LTO ON)
set(NO_STATISTICS ON)

set(CMAKE_POSITION_INDEPENDENT_CODE ON)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_C_VISIBILITY_PRESET hidden)
set(CMAKE_CXX_VISIBILITY_PRESET hidden)
set(CMAKE_VISIBILITY_INLINES_HIDDEN ON)

include(${TRACY_SRC_DIR}/cmake/config.cmake)
include(${TRACY_SRC_DIR}/cmake/vendor.cmake)
include(${TRACY_SRC_DIR}/cmake/server.cmake)

add_library(AngelicaTracyCapture SHARED angelica_tracy_capture.cpp)
set_target_properties(AngelicaTracyCapture PROPERTIES
    OUTPUT_NAME "AngelicaTracyCapture"
    CXX_VISIBILITY_PRESET hidden
    VISIBILITY_INLINES_HIDDEN ON
    POSITION_INDEPENDENT_CODE ON
)
target_link_libraries(AngelicaTracyCapture PRIVATE TracyServer)

if(CMAKE_SYSTEM_NAME STREQUAL "Linux")
    target_link_options(AngelicaTracyCapture PRIVATE -static-libstdc++ -static-libgcc -Wl,--exclude-libs,ALL)
endif()

if(CMAKE_SYSTEM_NAME STREQUAL "Darwin")
    target_link_options(AngelicaTracyCapture PRIVATE
        -Wl,-exported_symbols_list,${CMAKE_CURRENT_SOURCE_DIR}/angelica_tracy_capture.exports
    )
endif()
