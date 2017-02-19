#pragma once

#include <stdint.h>
#include "../gif.h"

typedef struct AnimationInterface {
	uint_fast32_t (*RenderBitmap)(Animation *animation, void *pixels, uint_fast32_t frameIndex);

	bool (*Reset)(Animation *animation);

	int (*SeekToTime)(Animation *animation, long desiredPosition);

	int (*SeekToFrame)(Animation *animation, uint_fast32_t desiredIndex);

	void (*Release)(Animation *animation);

	void (*SetOptions)(Animation *animation, char sampleSize, bool isOpaque);

	char *(*GetComment)(Animation *animation);

	uint_fast32_t (*GetDuration)(Animation *animation, uint_fast32_t frameIndex);

	long long (*GetCurrentPosition)(Animation *animation);

	size_t (*GetMetadataByteCount)(Animation *animation);

	size_t (*GetAllocationByteCount)(Animation *animation);

	int (*GetFrameDuration)(Animation *animation, int index);

	int (*GetErrorCode)(Animation *animation);
} AnimationInterface;

typedef struct Animation {
	AnimationInterface functions;
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
	uint32_t stride;
	void *data;
} Animation;