cmake_minimum_required(VERSION 3.22.1)
project(face_runtime_jni)

set(THIRD_PARTY_DIR "${CMAKE_CURRENT_SOURCE_DIR}/third_party")

add_library(ncnn SHARED IMPORTED)
set_target_properties(ncnn PROPERTIES
    IMPORTED_LOCATION "${THIRD_PARTY_DIR}/ncnn/${ANDROID_ABI}/lib/libncnn.so")

add_library(MNN SHARED IMPORTED)
set_target_properties(MNN PROPERTIES
    IMPORTED_LOCATION "${THIRD_PARTY_DIR}/mnn/${ANDROID_ABI}/libMNN.so")

add_library(face_runtime_jni SHARED face_runtime_jni.cpp)
target_compile_features(face_runtime_jni PRIVATE cxx_std_17)
target_include_directories(face_runtime_jni PRIVATE
    "${THIRD_PARTY_DIR}/ncnn/${ANDROID_ABI}/include"
    "${THIRD_PARTY_DIR}/mnn/include")
target_link_libraries(face_runtime_jni ncnn MNN log)
