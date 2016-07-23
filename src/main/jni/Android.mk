cflags:= -Wpedantic -std=c11
extra_ldlibs :=

ifeq ($(NDK_DEBUG),1)
	cflags+= -DDEBUG
	extra_ldlibs= -llog
else
	cflags+= -fvisibility=hidden
endif

LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := pl_droidsonroids_gif
LOCAL_CFLAGS := $(cflags)
LOCAL_LDLIBS := -ljnigraphics -landroid -lGLESv2 $(extra_ldlibs)

LOCAL_SRC_FILES := \
	drawing.c \
	gif.c \
	metadata.c \
	memset32_neon.S \
	bitmap.c \
	open_close.c \
	decoding.c \
	exception.c \
	time.c \
	control.c \
	memset.arm.S \
    surface.c \
	opengl.c \
	giflib/dgif_lib.c \
	giflib/gifalloc.c \
	giflib/openbsd-reallocarray.c \

include $(BUILD_SHARED_LIBRARY)