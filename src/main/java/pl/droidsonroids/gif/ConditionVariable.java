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

package pl.droidsonroids.gif;

class ConditionVariable {
	private volatile boolean mCondition;

	synchronized void set(boolean state) {
		if (state) {
			open();
		} else {
			close();
		}
	}

	synchronized void open() {
		boolean old = mCondition;
		mCondition = true;
		if (!old) {
			this.notify();
		}
	}

	synchronized void close() {
		mCondition = false;
	}

	synchronized void block() throws InterruptedException {
		while (!mCondition) {
			this.wait();
		}
	}
}