#pragma once

#include "animation/animation.h"

char *getGifComment(Animation *animation);

uint_fast32_t getGifDuration(Animation *animation, uint_fast32_t frameIndex);

size_t getGifMetadataByteCount(Animation *animation);

size_t getGifAllocationByteCount(Animation *animation);

int getGifErrorCode(Animation *animation);

