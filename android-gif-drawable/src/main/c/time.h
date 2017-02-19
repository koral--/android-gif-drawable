#pragma once

#include <stdint.h>
#include "animation/animation.h"

/**
* @return the real time, in ms
*/
long getRealTime();

long long calculateInvalidationDelay(Animation *animation, long renderStartTime, uint_fast32_t frameDuration);