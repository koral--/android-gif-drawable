#include "webp.h"
#include "gif.h"
#include <sys/mman.h>
#include <GLES2/gl2.h>

static void ClearPreviousFrame(WebPAnimation *animation) {
	WebPIterator *const prev = &animation->prev_frame;
	prev->width = animation->canvas_width;
	prev->height = animation->canvas_height;
	prev->x_offset = prev->y_offset = 0;
	prev->dispose_method = WEBP_MUX_DISPOSE_BACKGROUND;
}

static void ClearPreviousPic(WebPAnimation *animation) {
	WebPFreeDecBuffer((WebPDecBuffer *) animation->pic);
	animation->pic = NULL;
}

static int Decode(WebPAnimation *animation) {
	const WebPIterator *const curr = &animation->curr_frame;
	WebPDecoderConfig *const config = &animation->config;
	WebPDecBuffer *const output_buffer = &config->output;
	int ok = 0;

	ClearPreviousPic(animation);
	output_buffer->colorspace = MODE_RGBA;
	ok = (WebPDecode(curr->fragment.bytes, curr->fragment.size, config) == VP8_STATUS_OK);
	if (!ok) {
		fprintf(stderr, "Decoding of frame #%d failed!\n", curr->frame_num);
	} else {
		animation->pic = output_buffer;
//			TODO? ok = ApplyColorProfile(&kParams.iccp.chunk, output_buffer);
//			if (!ok) {
//				fprintf(stderr, "Applying color profile to frame #%d failed!\n",
//				        curr->frame_num);
//			}
	}
	return ok;
}

WebPAnimation *openFd(JNIEnv *env, const int fd, const long fileSize, jlong offset) {

	off64_t pa_offset = offset & ~(sysconf(_SC_PAGE_SIZE) - 1);
	size_t length = (size_t) (fileSize + offset - pa_offset);
	void *source = mmap(NULL, length, PROT_READ, MAP_PRIVATE, fd, pa_offset);
	if (source == NULL) {
		return NULL;
	}
	WebPData webpData = {.bytes=source + offset - pa_offset, .size=length};
	WebPDemuxer *demuxer = WebPDemux(&webpData);

	if (demuxer == NULL) {
		//FIXME distinguish error
		munmap(source, length);
		return NULL;
	}

	WebPAnimation *const animation = calloc(1, sizeof(WebPAnimation));
	animation->dmux = demuxer;
	animation->source = source;
	animation->source_length = length;
	animation->canvas_width = WebPDemuxGetI(demuxer, WEBP_FF_CANVAS_WIDTH);
	animation->canvas_height = WebPDemuxGetI(demuxer, WEBP_FF_CANVAS_HEIGHT);
	animation->frame_count = WebPDemuxGetI(demuxer, WEBP_FF_FRAME_COUNT);
	animation->frame_buffer = calloc(animation->canvas_width * animation->canvas_height, 4);
	animation->frame_buffer_stride = animation->canvas_width * 4;
	//FIXME handle OOME

	WebPInitDecoderConfig(&animation->config);
	animation->config.options.dithering_strength = 50;
	animation->config.options.alpha_dithering_strength = 100;
	animation->loop_count = (int) WebPDemuxGetI(animation->dmux, WEBP_FF_LOOP_COUNT);

	//todo move to render?
	ClearPreviousFrame(animation);

	WebPIterator *const curr = &animation->curr_frame;
	if (!WebPDemuxGetFrame(animation->dmux, 1, curr)) {
		//TODO align, release
		return NULL;
	}

	animation->has_animation = (curr->num_frames > 1);
	if (!Decode(animation)) {
		//TODO align, release
		return NULL;
	};
	WebPDemuxGetFrame(animation->dmux, 0, curr);
	if (animation->loop_count) ++animation->loop_count;
	return animation;
}

static void decode_callback(WebPAnimation *animation) {
	if (!animation->done) {
		int duration = 0;
		if (animation->dmux != NULL) {
			WebPIterator *const curr = &animation->curr_frame;
			if (!WebPDemuxNextFrame(curr)) {
				WebPDemuxReleaseIterator(curr);
				if (WebPDemuxGetFrame(animation->dmux, 1, curr)) {
					--animation->loop_count;
					animation->done = (animation->loop_count == 0);
					if (animation->done) return;
					ClearPreviousFrame(animation);
				} else {
					animation->decoding_error = 1;
					animation->done = 1;
					return;
				}
			}
			duration = curr->duration;
		}
		if (!Decode(animation)) {
			animation->decoding_error = 1;
			animation->done = 1;
		} else {
			//TODO Ok
		}
	}
}

JNIEXPORT jlong JNICALL
Java_pl_droidsonroids_gif_WebpInfoHandle_openFd(JNIEnv *env, jclass type, jobject jfd, jlong offset) {
	if (isSourceNull(jfd, env)) {
		return NULL_JLONG_POINTER;
	}
	//TODO extract duplicated code>
	jclass fdClass = (*env)->GetObjectClass(env, jfd);
	static jfieldID fdClassDescriptorFieldID = NULL;
	if (fdClassDescriptorFieldID == NULL) {
		fdClassDescriptorFieldID = (*env)->GetFieldID(env, fdClass, "descriptor", "I");
	}
	if (fdClassDescriptorFieldID == NULL) {
		return NULL_JLONG_POINTER;
	}
	const jint oldFd = (*env)->GetIntField(env, jfd, fdClassDescriptorFieldID);
	const int fd = dup(oldFd);
	if (fd == -1) {
		throwGifIOException(D_GIF_ERR_OPEN_FAILED, env, true);
		return NULL_JLONG_POINTER;
	}
	//TODO extract duplicated code<

	struct stat st;
	const long sourceLength = fstat(fd, &st) == 0 ? st.st_size : -1;
	//TODO handle -1
	WebPAnimation *const info = openFd(env, fd, sourceLength, offset);
	if (info == NULL) {
		close(fd);
	}
	return (jlong) (intptr_t) info;
}

JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_WebpInfoHandle_free(JNIEnv *env, jclass type, jlong infoPtr) {
	WebPAnimation *animation = (WebPAnimation *) infoPtr;
	if (animation == NULL) {
		return;
	}
	WebPDemuxDelete(animation->dmux);
	munmap(animation->source, animation->source_length);
	free(animation);
}

JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_WebpInfoHandle_getWidth(JNIEnv *env, jclass type, jlong infoPtr) {
	WebPAnimation *animation = (WebPAnimation *) infoPtr;
	return animation != NULL ? animation->canvas_width : 0;
}

JNIEXPORT jint JNICALL
Java_pl_droidsonroids_gif_WebpInfoHandle_getHeight(JNIEnv *env, jclass type, jlong infoPtr) {
	WebPAnimation *animation = (WebPAnimation *) infoPtr;
	return animation != NULL ? animation->canvas_height : 0;
}

static inline uint8_t *getBufferAddress(uint8_t *addr, int stride, int left, int top) {
	return addr + top * stride + left * 4;
}

JNIEXPORT void JNICALL
Java_pl_droidsonroids_gif_WebpInfoHandle_glTexSubImage2D(JNIEnv *env, jclass type, jlong infoPtr, jint target, jint level) {
	usleep(500000);
	WebPAnimation *animation = (WebPAnimation *) infoPtr;
	if (animation == NULL) {
		return;
	}
	decode_callback(animation);
	const WebPDecBuffer *const pic = animation->pic;
	const WebPIterator *const curr = &animation->curr_frame;
	WebPIterator *const prev = &animation->prev_frame;
	if (pic == NULL) return;

	if (curr->frame_num == 1) {
		memset(animation->frame_buffer, 0, (size_t) animation->frame_buffer_stride * animation->canvas_height);
	}
	
	if (prev->dispose_method == WEBP_MUX_DISPOSE_BACKGROUND) {
		uint8_t *dst = getBufferAddress(animation->frame_buffer, animation->frame_buffer_stride, curr->x_offset, curr->y_offset);
		int y;
		for (y = 0; y < pic->height; y++) {
			memset(dst, 0, (size_t) animation->frame_buffer_stride);
			dst += animation->frame_buffer_stride;
		}
	}

	if (prev->dispose_method == WEBP_MUX_DISPOSE_BACKGROUND ||
	    curr->blend_method == WEBP_MUX_NO_BLEND) {
		if (prev->dispose_method == WEBP_MUX_DISPOSE_BACKGROUND) {
			// Clear the previous frame rectangle.
		} else {  // curr->blend_method == WEBP_MUX_NO_BLEND.
			// We simulate no-blending behavior by first clearing the current frame
			// rectangle (to a checker-board) and then alpha-blending against it.
			//TODO handle
		}
	}

	uint8_t *dst = getBufferAddress(animation->frame_buffer, animation->frame_buffer_stride, curr->x_offset, curr->y_offset);
	uint8_t *src = pic->u.RGBA.rgba;
	int y;
	for (y = 0; y < pic->height; y++) {
		memcpy(dst, src, (size_t) pic->width * 4);
		dst += animation->frame_buffer_stride;
		src += pic->u.RGBA.stride;
	}

	glTexSubImage2D((GLenum) target, level, 0, 0, animation->canvas_width, animation->canvas_height, GL_RGBA, GL_UNSIGNED_BYTE, animation->frame_buffer);

	*prev = *curr;
}