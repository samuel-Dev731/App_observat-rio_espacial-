cmake_minimum_required(VERSION 3.22.1)
project(observatorio_native LANGUAGES C CXX)

add_library(observatorio_native SHARED
    native_bridge.cpp
    orbit_math.c
)

find_library(log-lib log)
find_library(gles-lib GLESv2)

target_compile_features(observatorio_native PRIVATE cxx_std_17)
target_compile_options(observatorio_native PRIVATE
    $<$<COMPILE_LANGUAGE:C>:-O2>
    $<$<COMPILE_LANGUAGE:CXX>:-O2>
)
target_link_libraries(observatorio_native
    ${log-lib}
    ${gles-lib}
    m
)
