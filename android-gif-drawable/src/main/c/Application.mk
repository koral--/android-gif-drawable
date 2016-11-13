APP_ABI := all
APP_PLATFORM := android-9

ifeq ($(NDK_DEBUG),1)
	APP_OPTIM := debug
else
	APP_OPTIM := release
endif

NDK_TOOLCHAIN_VERSION := clang