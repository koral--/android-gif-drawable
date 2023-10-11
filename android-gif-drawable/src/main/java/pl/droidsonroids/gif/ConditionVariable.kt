/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pl.droidsonroids.gif

import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

class ConditionVariable {
    @Volatile
    private var mCondition: Boolean = false
    private val lock: Lock = ReentrantLock()
    private val condition: Condition = lock.newCondition()

    fun set(state: Boolean) {
        lock.lock()
        try {
            if (state) {
                open()
            } else {
                close()
            }
        } finally {
            lock.unlock()
        }
    }

    fun open() {
        lock.lock()
        try {
            val old = mCondition
            mCondition = true
            if (!old) {
                condition.signal()
            }
        } finally {
            lock.unlock()
        }
    }

    fun close() {
        mCondition = false
    }

    fun block() {
        lock.lock()
        try {
            while (!mCondition) {
                condition.await()
            }
        } finally {
            lock.unlock()
        }
    }
}






