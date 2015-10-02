/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.util

import com.intellij.xdebugger.XDebugSession
import org.jetbrains.concurrency.Promise
import org.jetbrains.debugger.SuspendContext
import org.jetbrains.debugger.Vm
import org.jetbrains.rpc.CommandProcessor
import org.jetbrains.util.concurrency.AsyncPromise

// have to use package "com.intellij.xdebugger.util" to avoid package clash
public fun XDebugSession.rejectedErrorReporter(description: String? = null): (Throwable) -> Unit = {
  Promise.logError(CommandProcessor.LOG, it)
  if (it != AsyncPromise.OBSOLETE_ERROR) {
    reportError("${if (description == null) "" else description + ": "}${it.getMessage()}")
  }
}

public inline fun <T> contextDependentResultConsumer(context: SuspendContext, crossinline done: (result: T, vm: Vm) -> Unit) : (T) -> Unit {
  return {
    val vm = context.valueManager.vm
    if (vm.attachStateManager.isAttached() && !vm.getSuspendContextManager().isContextObsolete(context)) {
      done(it, vm)
    }
  }
}