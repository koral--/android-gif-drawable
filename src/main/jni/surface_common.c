#include "gif.h"
#include <sys/eventfd.h>

void releaseSurfaceDescriptor(SurfaceDescriptor *surfaceDescriptor, JNIEnv *env) {
	if (surfaceDescriptor == NULL)
		return;
	free(surfaceDescriptor->surfaceBackupPtr);
	surfaceDescriptor->surfaceBackupPtr = NULL;
	int closeResult = close(surfaceDescriptor->eventPollFd.fd);
	if (closeResult != 0 && errno != EINTR) {
		throwException(env, RUNTIME_EXCEPTION_ERRNO, "Eventfd close failed ");
	}
	THROW_ON_NONZERO_RESULT(pthread_mutex_destroy(&surfaceDescriptor->slurpMutex), "Slurp mutex destroy failed ");
	THROW_ON_NONZERO_RESULT(pthread_mutex_destroy(&surfaceDescriptor->renderMutex), "Render mutex destroy failed ");
	THROW_ON_NONZERO_RESULT(pthread_cond_destroy(&surfaceDescriptor->slurpCond), "Slurp cond destroy failed ");
	THROW_ON_NONZERO_RESULT(pthread_cond_destroy(&surfaceDescriptor->renderCond), "Render cond  destroy failed ");
}

bool initSurfaceDescriptor(SurfaceDescriptor *surfaceDescriptor, JNIEnv *env) {
	surfaceDescriptor->eventPollFd.events = POLL_IN;
	surfaceDescriptor->eventPollFd.fd = eventfd(0, 0);
	if (surfaceDescriptor->eventPollFd.fd == -1) {
		throwException(env, RUNTIME_EXCEPTION_ERRNO, "Could not create eventfd ");
		return false;
	}
	const pthread_cond_t condInitializer = PTHREAD_COND_INITIALIZER;
	surfaceDescriptor->slurpCond = condInitializer;
	surfaceDescriptor->renderCond = condInitializer;
	const pthread_mutex_t mutexInitializer = PTHREAD_MUTEX_INITIALIZER;
	surfaceDescriptor->slurpMutex = mutexInitializer;
	surfaceDescriptor->renderMutex = mutexInitializer;

	surfaceDescriptor->surfaceBackupPtr = NULL;
	return true;
}
