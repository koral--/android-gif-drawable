cmake_minimum_required(VERSION 3.4.1)

add_library(pl_droidsonroids_gif SHARED
	drawing.c
	gif.c
	metadata.c
	memset32_neon.S
	bitmap.c
	decoding.c
	exception.c
	time.c
	control.c
	memset.arm.S
    surface.c
	opengl.c
	jni.c
	init.c
	dispose.c
	giflib/dgif_lib.c
	giflib/gifalloc.c
	giflib/openbsd-reallocarray.c)

target_link_libraries(pl_droidsonroids_gif
                      jnigraphics
                      android
                      GLESv2)