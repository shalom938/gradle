/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.configurationcache

import com.google.common.collect.Sets
import org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationType
import org.gradle.internal.operations.BuildOperationAncestryTracker
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.BuildOperationListenerManager
import org.gradle.internal.operations.CurrentBuildOperationRef
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.internal.service.scopes.EventScope
import org.gradle.internal.service.scopes.Scopes
import java.io.Closeable


/**
 * Tracks task execution. Can be used to find when an included build runs some forbidden action as part of the task execution, where it should be allowed.
 */
@EventScope(Scopes.BuildTree::class)
interface TaskExecutionTracker {
    fun isCurrentThreadExecutingTaskBuildOperation(): Boolean
}


class DefaultTaskExecutionTracker(
    private val buildOperationAncestryTracker: BuildOperationAncestryTracker,
    private val buildOperationListenerManager: BuildOperationListenerManager,
) : TaskExecutionTracker, Closeable {
    private
    class OperationListener : BuildOperationListener {
        val currentRunningTaskOperations: MutableSet<OperationIdentifier> = Sets.newConcurrentHashSet()

        override fun started(buildOperation: BuildOperationDescriptor, startEvent: OperationStartEvent) {
            if (buildOperation.details is ExecuteTaskBuildOperationType.Details) {
                val id = buildOperation.id ?: throw IllegalArgumentException("Task build operation $buildOperation has no valid id")
                currentRunningTaskOperations.add(id)
            }
        }

        override fun progress(operationIdentifier: OperationIdentifier, progressEvent: OperationProgressEvent) {
        }

        override fun finished(buildOperation: BuildOperationDescriptor, finishEvent: OperationFinishEvent) {
            if (buildOperation.details is ExecuteTaskBuildOperationType.Details) {
                val id = buildOperation.id ?: throw IllegalArgumentException("Task build operation $buildOperation has no valid id")
                currentRunningTaskOperations.remove(id)
            }
        }
    }

    private
    val currentBuildOperationRef = CurrentBuildOperationRef.instance()

    private
    val operationListener = OperationListener()

    init {
        buildOperationListenerManager.addListener(operationListener)
    }

    override fun close() {
        buildOperationListenerManager.removeListener(operationListener)
    }

    override fun isCurrentThreadExecutingTaskBuildOperation(): Boolean {
        val currentBuildOperationId = currentBuildOperationRef.id
        val taskAncestorId = buildOperationAncestryTracker.findClosestMatchingAncestor(currentBuildOperationId, operationListener.currentRunningTaskOperations::contains)
        return taskAncestorId.isPresent
    }
}
