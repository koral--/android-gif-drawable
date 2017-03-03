cmake_minimum_required(VERSION 3.4.1)
enable_language(ASM)

set(SOURCES)
list(APPEND SOURCES
	drawing.c
	gif.c
	metadata.c
	bitmap.c
	decoding.c
	exception.c
	time.c
	control.c
    surface.c
	opengl.c
	jni.c
	init.c
	dispose.c
	giflib/dgif_lib.c
	giflib/gifalloc.c
	giflib/openbsd-reallocarray.c)


add_library(pl_droidsonroids_gif SHARED ${SOURCES})

set(LIBS)
list(APPEND LIBS
	jnigraphics
    android
    GLESv2
)

target_link_libraries(pl_droidsonroids_gif ${LIBS})
