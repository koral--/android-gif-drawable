#pragma once

#include <jni.h>
#include <libwebp/src/webp/demux.h>

typedef struct WebPAnimation WebPAnimation;

struct WebPAnimation {
	WebPDemuxer *dmux;
	void *source;
	size_t source_length;

	int has_animation;
	int has_color_profile;
	int done;
	int decoding_error;

	uint32_t canvas_width, canvas_height;
	int loop_count; //todo uint32_t?

	WebPData data;
	WebPDecoderConfig config;
	const WebPDecBuffer *pic;
	WebPIterator curr_frame;
	WebPIterator prev_frame;
	WebPChunkIterator iccp;
	uint32_t frame_count;
	void* frame_buffer;
	int frame_buffer_stride;//TODO size_t?
};

WebPAnimation *openFd(JNIEnv *env, const int fd, const long fileSize, jlong demuxer);