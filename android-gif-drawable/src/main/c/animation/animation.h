#pragma once

#include <stdint.h>

typedef long long (*RenderBitmap)(void *descriptor, void *pixels);

typedef bool (*Reset)(void *descriptor);

typedef int (*SeekToTime)(void *descriptor, long desiredPosition);

typedef int (*SeekToFrame)(void *descriptor, uint_fast32_t desiredIndex);

typedef void (*Release)(void *descriptor);

typedef void (*SetOptions)(void *descriptor, char sampleSize, bool isOpaque);

typedef char *(*GetComment)(void *descriptor);

typedef int (*GetDuration)(void *descriptor);

typedef long long (*GetCurrentPosition)(void *descriptor);

typedef long (*GetMetadataByteCount)(void *descriptor);

typedef size_t (*GetAllocationByteCount)(void *descriptor);

typedef int (*GetFrameDuration)(void *descriptor, int index);

typedef struct Animation {
	RenderBitmap renderBitmap;
	Reset reset;
	SeekToTime seekToTime;
	SeekToFrame seekToFrame;
	Release release;
	SetOptions setOptions;
	GetComment getComment;
	GetDuration getDuration;
	GetCurrentPosition getCurrentPosition;
	GetMetadataByteCount getMetadataByteCount;
	GetAllocationByteCount getAllocationByteCount;
	GetFrameDuration getFrameDuration;

	float speedFactor;
	uint_fast16_t sampleSize;
	long long lastFrameRemainder;
	long long nextStartTime;
	uint_fast32_t currentFrameIndex;
	uint_fast16_t loopCount;
	uint_fast16_t currentLoopIndex;
	long sourceLength;
	bool isOpaque;
	uint_fast32_t canvasWidth;
	uint_fast32_t canvasHeight;
	uint_fast32_t numberOfFrames;
	void *data;
} Animation;