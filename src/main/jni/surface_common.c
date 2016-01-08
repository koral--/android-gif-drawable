#include "gif.h"

void releaseSurfaceDescriptor(SurfaceDescriptor *surfaceDescriptor, JNIEnv *env) {
    if (surfaceDescriptor == NULL)
        return;
    free(surfaceDescriptor->surfaceBackupPtr);
    surfaceDescriptor->surfaceBackupPtr = NULL;
    int closeResult = close(surfaceDescriptor->eventPollFd.fd);
    if (closeResult !=0 && errno != EINTR) {
        throwException(env, RUNTIME_EXCEPTION_ERRNO, "Eventfd close failed ");
    }
    THROW_ON_NONZERO_RESULT(pthread_mutex_destroy(&surfaceDescriptor->slurpMutex), "Slurp mutex destroy failed ");
    THROW_ON_NONZERO_RESULT(pthread_mutex_destroy(&surfaceDescriptor->renderMutex), "Render mutex destroy failed ");
    THROW_ON_NONZERO_RESULT(pthread_cond_destroy(&surfaceDescriptor->slurpCond), "Slurp cond destroy failed ");
    THROW_ON_NONZERO_RESULT(pthread_cond_destroy(&surfaceDescriptor->renderCond), "Render cond  destroy failed ");
}
