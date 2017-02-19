extra_ldlibs :=

ifeq ($(NDK_DEBUG),1)
	extra_ldlibs= -llog
endif

WEBP_CFLAGS := -Wall -DANDROID -DHAVE_MALLOC_H -DHAVE_PTHREAD -DWEBP_USE_THREAD

ifeq ($(APP_OPTIM),release)
  WEBP_CFLAGS += -finline-functions -ffast-math \
                 -ffunction-sections -fdata-sections
  ifeq ($(findstring clang,$(NDK_TOOLCHAIN_VERSION)),)
    WEBP_CFLAGS += -frename-registers -s
  endif
endif

ifneq ($(findstring armeabi-v7a, $(TARGET_ARCH_ABI)),)
  # Setting LOCAL_ARM_NEON will enable -mfpu=neon which may cause illegal
  # instructions to be generated for armv7a code. Instead target the neon code
  # specifically.
  NEON := c.neon
  USE_CPUFEATURES := yes
else
  NEON := c
endif

LOCAL_PATH := $(call my-dir)

dec_srcs := \
    libwebp/src/dec/alpha_dec.c \
    libwebp/src/dec/buffer_dec.c \
    libwebp/src/dec/frame_dec.c \
    libwebp/src/dec/idec_dec.c \
    libwebp/src/dec/io_dec.c \
    libwebp/src/dec/quant_dec.c \
    libwebp/src/dec/tree_dec.c \
    libwebp/src/dec/vp8_dec.c \
    libwebp/src/dec/vp8l_dec.c \
    libwebp/src/dec/webp_dec.c \

demux_srcs := \
    libwebp/src/demux/anim_decode.c \
    libwebp/src/demux/demux.c \

dsp_dec_srcs := \
    libwebp/src/dsp/alpha_processing.c \
    libwebp/src/dsp/alpha_processing_mips_dsp_r2.c \
    libwebp/src/dsp/alpha_processing_neon.$(NEON) \
    libwebp/src/dsp/alpha_processing_sse2.c \
    libwebp/src/dsp/alpha_processing_sse41.c \
    libwebp/src/dsp/argb.c \
    libwebp/src/dsp/argb_mips_dsp_r2.c \
    libwebp/src/dsp/argb_sse2.c \
    libwebp/src/dsp/cpu.c \
    libwebp/src/dsp/dec.c \
    libwebp/src/dsp/dec_clip_tables.c \
    libwebp/src/dsp/dec_mips32.c \
    libwebp/src/dsp/dec_mips_dsp_r2.c \
    libwebp/src/dsp/dec_msa.c \
    libwebp/src/dsp/dec_neon.$(NEON) \
    libwebp/src/dsp/dec_sse2.c \
    libwebp/src/dsp/dec_sse41.c \
    libwebp/src/dsp/filters.c \
    libwebp/src/dsp/filters_mips_dsp_r2.c \
    libwebp/src/dsp/filters_msa.c \
    libwebp/src/dsp/filters_neon.$(NEON) \
    libwebp/src/dsp/filters_sse2.c \
    libwebp/src/dsp/lossless.c \
    libwebp/src/dsp/lossless_mips_dsp_r2.c \
    libwebp/src/dsp/lossless_msa.c \
    libwebp/src/dsp/lossless_neon.$(NEON) \
    libwebp/src/dsp/lossless_sse2.c \
    libwebp/src/dsp/rescaler.c \
    libwebp/src/dsp/rescaler_mips32.c \
    libwebp/src/dsp/rescaler_mips_dsp_r2.c \
    libwebp/src/dsp/rescaler_msa.c \
    libwebp/src/dsp/rescaler_neon.$(NEON) \
    libwebp/src/dsp/rescaler_sse2.c \
    libwebp/src/dsp/upsampling.c \
    libwebp/src/dsp/upsampling_mips_dsp_r2.c \
    libwebp/src/dsp/upsampling_msa.c \
    libwebp/src/dsp/upsampling_neon.$(NEON) \
    libwebp/src/dsp/upsampling_sse2.c \
    libwebp/src/dsp/yuv.c \
    libwebp/src/dsp/yuv_mips32.c \
    libwebp/src/dsp/yuv_mips_dsp_r2.c \
    libwebp/src/dsp/yuv_sse2.c \

dsp_enc_srcs := \
    libwebp/src/dsp/cost.c \
    libwebp/src/dsp/cost_mips32.c \
    libwebp/src/dsp/cost_mips_dsp_r2.c \
    libwebp/src/dsp/cost_sse2.c \
    libwebp/src/dsp/enc.c \
    libwebp/src/dsp/enc_avx2.c \
    libwebp/src/dsp/enc_mips32.c \
    libwebp/src/dsp/enc_mips_dsp_r2.c \
    libwebp/src/dsp/enc_msa.c \
    libwebp/src/dsp/enc_neon.$(NEON) \
    libwebp/src/dsp/enc_sse2.c \
    libwebp/src/dsp/enc_sse41.c \
    libwebp/src/dsp/lossless_enc.c \
    libwebp/src/dsp/lossless_enc_mips32.c \
    libwebp/src/dsp/lossless_enc_mips_dsp_r2.c \
    libwebp/src/dsp/lossless_enc_msa.c \
    libwebp/src/dsp/lossless_enc_neon.$(NEON) \
    libwebp/src/dsp/lossless_enc_sse2.c \
    libwebp/src/dsp/lossless_enc_sse41.c \

enc_srcs := \
    libwebp/src/enc/alpha_enc.c \
    libwebp/src/enc/analysis_enc.c \
    libwebp/src/enc/backward_references_enc.c \
    libwebp/src/enc/config_enc.c \
    libwebp/src/enc/cost_enc.c \
    libwebp/src/enc/delta_palettization_enc.c \
    libwebp/src/enc/filter_enc.c \
    libwebp/src/enc/frame_enc.c \
    libwebp/src/enc/histogram_enc.c \
    libwebp/src/enc/iterator_enc.c \
    libwebp/src/enc/near_lossless_enc.c \
    libwebp/src/enc/picture_enc.c \
    libwebp/src/enc/picture_csp_enc.c \
    libwebp/src/enc/picture_psnr_enc.c \
    libwebp/src/enc/picture_rescale_enc.c \
    libwebp/src/enc/picture_tools_enc.c \
    libwebp/src/enc/predictor_enc.c \
    libwebp/src/enc/quant_enc.c \
    libwebp/src/enc/syntax_enc.c \
    libwebp/src/enc/token_enc.c \
    libwebp/src/enc/tree_enc.c \
    libwebp/src/enc/vp8l_enc.c \
    libwebp/src/enc/webp_enc.c \

mux_srcs := \
    libwebp/src/mux/anim_encode.c \
    libwebp/src/mux/muxedit.c \
    libwebp/src/mux/muxinternal.c \
    libwebp/src/mux/muxread.c \

utils_dec_srcs := \
    libwebp/src/utils/bit_reader_utils.c \
    libwebp/src/utils/color_cache_utils.c \
    libwebp/src/utils/filters_utils.c \
    libwebp/src/utils/huffman_utils.c \
    libwebp/src/utils/quant_levels_dec_utils.c \
    libwebp/src/utils/random_utils.c \
    libwebp/src/utils/rescaler_utils.c \
    libwebp/src/utils/thread_utils.c \
    libwebp/src/utils/utils.c \

utils_enc_srcs := \
    libwebp/src/utils/bit_writer_utils.c \
    libwebp/src/utils/huffman_encode_utils.c \
    libwebp/src/utils/quant_levels_utils.c \

################################################################################
# libwebpdecoder
include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    $(dec_srcs) \
    $(dsp_dec_srcs) \
    $(utils_dec_srcs) \

LOCAL_CFLAGS := $(WEBP_CFLAGS)
LOCAL_C_INCLUDES += $(LOCAL_PATH)/libwebp/src

# prefer arm over thumb mode for performance gains
LOCAL_ARM_MODE := arm

ifeq ($(USE_CPUFEATURES),yes)
  LOCAL_STATIC_LIBRARIES := cpufeatures
endif

LOCAL_MODULE := webpdecoder_static

include $(BUILD_STATIC_LIBRARY)

################################################################################
# libwebp

include $(CLEAR_VARS)

LOCAL_SRC_FILES := \
    $(dsp_enc_srcs) \
    $(enc_srcs) \
    $(utils_enc_srcs) \

LOCAL_CFLAGS := $(WEBP_CFLAGS)
LOCAL_C_INCLUDES += $(LOCAL_PATH)/libwebp/src

# prefer arm over thumb mode for performance gains
LOCAL_ARM_MODE := arm

LOCAL_WHOLE_STATIC_LIBRARIES := webpdecoder_static

LOCAL_MODULE := webp

include $(BUILD_STATIC_LIBRARY)

include $(CLEAR_VARS)

################################################################################
# libwebpdemux

include $(CLEAR_VARS)

LOCAL_SRC_FILES := $(demux_srcs)

LOCAL_CFLAGS := $(WEBP_CFLAGS)
LOCAL_C_INCLUDES += $(LOCAL_PATH)/libwebp/src

# prefer arm over thumb mode for performance gains
LOCAL_ARM_MODE := arm

LOCAL_MODULE := webpdemux

LOCAL_STATIC_LIBRARIES := webp
include $(BUILD_STATIC_LIBRARY)

################################################################################
# pl_droidsonroids_gif

include $(CLEAR_VARS)
LOCAL_MODULE := pl_droidsonroids_gif
LOCAL_LDLIBS := -ljnigraphics -landroid -lGLESv2 $(extra_ldlibs)
LOCAL_CFLAGS := -Weverything -std=c11

LOCAL_SRC_FILES := \
	drawing.c \
	gif.c \
	metadata.c \
	memset32_neon.S \
	bitmap.c \
	decoding.c \
	exception.c \
	time.c \
	control.c \
	memset.arm.S \
    surface.c \
	opengl.c \
	jni.c \
	init.c \
	dispose.c \
	webp/webp.c \
	gif/metadata.c \
	giflib/dgif_lib.c \
	giflib/gifalloc.c \
	giflib/openbsd-reallocarray.c \

LOCAL_STATIC_LIBRARIES := webpdemux

include $(BUILD_SHARED_LIBRARY)

ifeq ($(USE_CPUFEATURES),yes)
  $(call import-module,android/cpufeatures)
endif