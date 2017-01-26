#pragma once

#include <stdint.h>

typedef int (*RenderBitmap)(void *descriptor, void *pixels);

typedef bool (*Reset)(void *descriptor);

typedef int (*SeekToTime)(void *descriptor, long desiredPosition);

typedef int (*SeekToFrame)(void *descriptor, uint_fast32_t desiredIndex);

typedef void (*Release)(void *descriptor);

typedef void (*SetOptions)(void *descriptor, char sampleSize, bool isOpaque);

typedef char *(*GetComment)(void *descriptor);

typedef struct Animation {
	float speedFactor;
	uint_fast16_t sampleSize;
	long long lastFrameRemainder;
	long long nextStartTime;
	uint_fast32_t currentFrameIndex;
	uint_fast16_t loopCount;
	uint_fast16_t currentLoopIndex;
} Animation;