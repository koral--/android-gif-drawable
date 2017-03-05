#!/usr/bin/env bash

set -e

rm -rf ${ANDROID_NDK_HOME}
mkdir -p ${ANDROID_HOME}/licenses
echo 8933bad161af4178b1185d1a37fbf41ea5269c55 > ${ANDROID_HOME}/licenses/android-sdk-license
echo y | ${ANDROID_HOME}/tools/android update sdk --no-ui --filter tools
${ANDROID_HOME}/tools/bin/sdkmanager --update
${ANDROID_HOME}/tools/bin/sdkmanager ndk-bundle
ln -s ${ANDROID_HOME}/ndk-bundle ${ANDROID_NDK_HOME}