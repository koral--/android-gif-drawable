#include "time.h"

long long calculateInvalidationDelay(Animation *animation, long renderStartTime, uint_fast32_t frameDuration) {
	if (frameDuration) {
		long long invalidationDelay = frameDuration;
		if (animation->speedFactor != 1.0) {
			invalidationDelay /= animation->speedFactor;
		}
		const long renderingTime = getRealTime() - renderStartTime;
		if (renderingTime >= invalidationDelay) {
			invalidationDelay = 0;
		} else {
			invalidationDelay -= renderingTime;
		}
		animation->nextStartTime = renderStartTime + invalidationDelay;
		return invalidationDelay;
	}
	return -1;
}

long getRealTime() {
	struct timespec ts; //result not checked since CLOCK_MONOTONIC_RAW availability is checked in JNI_ONLoad
	clock_gettime(CLOCK_MONOTONIC_RAW, &ts);
	return ts.tv_sec * 1000L + ts.tv_nsec / 1000000L;
}
