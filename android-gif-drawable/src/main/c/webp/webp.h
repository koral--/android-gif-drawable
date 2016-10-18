#pragma once

#include <jni.h>
#include <libwebp/src/webp/demux.h>

typedef struct WebPAnimation WebPAnimation;

struct WebPAnimation{
	WebPDemuxer *dmux;
	void *source;
	size_t source_length;

	int has_animation;
	int has_color_profile;
	int done;
	int decoding_error;
	int print_info;
	int only_deltas;
	int use_color_profile;

	int canvas_width, canvas_height;
	int loop_count; //todo uint32_t?
	uint32_t bg_color;

	const char* file_name;
	WebPData data;
	WebPDecoderConfig config;
	const WebPDecBuffer* pic;
	WebPIterator curr_frame;
	WebPIterator prev_frame;
	WebPChunkIterator iccp;
	int viewport_width, viewport_height;
	int frame_count;
};

WebPAnimation *openFd(JNIEnv *env, const int fd, const long fileSize, jlong demuxer);