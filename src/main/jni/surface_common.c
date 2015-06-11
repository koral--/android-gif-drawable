#include "gif.h"

void releaseSurfaceDescriptor(SurfaceDescriptor *surfaceDescriptor, JNIEnv *env) {
    if (surfaceDescriptor == NULL)
        return;
    free(surfaceDescriptor->surfaceBackupPtr);
    surfaceDescriptor->surfaceBackupPtr = NULL;
    THROW_ON_NONZERO_RESULT(close(surfaceDescriptor->eventPollFd.fd), "eventfd close failed");
    THROW_ON_NONZERO_RESULT(pthread_mutex_destroy(&surfaceDescriptor->slurpMutex), "slurp mutex destroy failed");
    THROW_ON_NONZERO_RESULT(pthread_mutex_destroy(&surfaceDescriptor->renderMutex), "render mutex destroy failed");
    THROW_ON_NONZERO_RESULT(pthread_cond_destroy(&surfaceDescriptor->slurpCond), "slurp cond destroy failed");
    THROW_ON_NONZERO_RESULT(pthread_cond_destroy(&surfaceDescriptor->renderCond), "render cond  destroy failed");
}