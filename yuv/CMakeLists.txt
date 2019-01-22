cmake_minimum_required(VERSION 3.4.1)

#libyuv
include_directories(${CMAKE_SOURCE_DIR}/libs/libyuv/include)
add_subdirectory(${CMAKE_SOURCE_DIR}/libs/libyuv)

#native-lib
add_library(yuvconvert-lib
        SHARED
        src/main/cpp/yuv-handle.cpp)

find_library( # Sets the name of the path variable.
        log-lib
        log)

target_link_libraries( # Specifies the target library.
        yuvconvert-lib
        yuv
        # Links the target library to the log library
        # included in the NDK.
        ${log-lib})