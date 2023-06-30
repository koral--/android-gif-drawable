project(androidGifDrawable)
cmake_minimum_required(VERSION 3.4.1)

set(CMAKE_VERBOSE_MAKEFILE on)
set(can_use_assembler TRUE)
enable_language(ASM)

set(SOURCES)

file(GLOB_RECURSE SOURCES "*.c")

if((${ANDROID_ABI} STREQUAL "armeabi") OR (${ANDROID_ABI} STREQUAL "armeabi-v7a"))
    list(APPEND SOURCES	memset.arm.S)
endif((${ANDROID_ABI} STREQUAL "armeabi") OR (${ANDROID_ABI} STREQUAL "armeabi-v7a"))

add_library(pl_droidsonroids_gif SHARED ${SOURCES})

set(LIBS)
list(APPEND LIBS
	jnigraphics
    android
    GLESv2
    log
)

target_link_libraries(pl_droidsonroids_gif ${LIBS})
