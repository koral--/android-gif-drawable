#include "gif.h"

time_t calculateInvalidationDelay(GifInfo *info, time_t renderStartTime, uint_fast16_t frameDuration) {
    if (frameDuration) {
        time_t invalidationDelay = frameDuration;
        if (info->speedFactor != 1.0) {
            invalidationDelay /= info->speedFactor; //TODO handle overflow
        }
        const time_t renderingTime = getRealTime() - renderStartTime;
        invalidationDelay -= renderingTime;
        if (invalidationDelay < 0)
            invalidationDelay = 0;
        info->nextStartTime = renderStartTime + invalidationDelay;
        return invalidationDelay;
    }
    info->lastFrameRemainder = 0; //TODO optimize flow
    return -1;
}

inline time_t getRealTime() {
    struct timespec ts; //result not checked since CLOCK_MONOTONIC_RAW availability is checked in JNI_ONLoad
    clock_gettime(CLOCK_MONOTONIC_RAW, &ts);
    return ts.tv_sec * 1000 + ts.tv_nsec / 1000000;
}