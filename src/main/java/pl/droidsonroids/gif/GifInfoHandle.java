package pl.droidsonroids.gif;

class GifInfoHandle {
    volatile long gifInfoPtr;
    final int width;
    final int height;
    final int imageCount;

    GifInfoHandle(long gifInfoPtr, int width, int height, int imageCount) {
        this.gifInfoPtr = gifInfoPtr;
        this.width = width;
        this.height = height;
        this.imageCount = imageCount;
    }
}
