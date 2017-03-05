#!/usr/bin/env bash

# Copyright (c) 2013 Embark Mobile
# Licensed under the MIT License.
# https://github.com/embarkmobile/android-sdk-installer

set +e

#detecting os
os=linux
if [[ `uname` == 'Darwin' ]]; then
    os=darwin
fi

ANDROID_NDK_VERSION=r14
INSTALLER_DIR=$HOME/.android-ndk

cd $HOME
wget -q https://dl.google.com/android/repository/android-ndk-${ANDROID_NDK_VERSION}-$os-x86_64.zip -O android-ndk.zip \
    && unzip -q ./android-ndk.zip \
    && sync \
    && mv ./android-ndk-${ANDROID_NDK_VERSION} ${INSTALLER_DIR} \
    && rm -rf android-ndk.zip