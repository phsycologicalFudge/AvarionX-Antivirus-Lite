cmake_minimum_required(VERSION 3.22.1)
project(avlite_jni LANGUAGES CXX)

add_library(avlite_jni SHARED engine_jni.cpp)
target_link_libraries(avlite_jni log)
target_link_options(avlite_jni PRIVATE -Wl,-z,max-page-size=16384)
