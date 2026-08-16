cmake_minimum_required(VERSION 3.13)
project(angelica-tracy-native CXX)

if(NOT DEFINED TRACY_SRC_DIR)
    message(FATAL_ERROR "TRACY_SRC_DIR must be set to a Tracy source checkout")
endif()

add_subdirectory("${TRACY_SRC_DIR}" tracy)

target_sources(TracyClient PRIVATE "${CMAKE_CURRENT_LIST_DIR}/angelica_tracy_shim.cpp")
target_include_directories(TracyClient PRIVATE "${TRACY_SRC_DIR}/public")
