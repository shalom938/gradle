/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.events

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

class BuildExecutionEventsIntegrationTest extends AbstractIntegrationSpec {
    @UnsupportedWithConfigurationCache(because = "tests listener behaviour")
    def "nags when TaskExecutionListener is registered via #path"() {
        buildFile """
            def listener = new TaskExecutionListener() {
                void beforeExecute(Task task) { }
                void afterExecute(Task task, TaskState state) { }
            }
            $path(listener)
            task broken
        """

        when:
        executer.expectDocumentedDeprecationWarning("Listener registration using ${registrationPoint}() has been deprecated. This will fail with an error in Gradle 8.0.")
        run("broken")

        then:
        noExceptionThrown()

        where:
        path                                        | registrationPoint
        "gradle.addListener"                        | "Gradle.addListener"
        "gradle.taskGraph.addTaskExecutionListener" | "TaskExecutionGraph.addTaskExecutionListener"
    }

    @UnsupportedWithConfigurationCache(because = "tests listener behaviour")
    def "events passed to any task execution listener are synchronised"() {
        settingsFile << "include 'a', 'b', 'c'"
        buildFile """
            def listener = new MyListener()
            gradle.addListener(listener)

            allprojects {
                task foo
            }

            class MyListener implements TaskExecutionListener {
                def called = []
                void beforeExecute(Task task) {
                    check(task)
                }
                void afterExecute(Task task, TaskState state) {
                    check(task)
                }
                void check(task) {
                    called << task
                    Thread.sleep(100)
                    //the last task added to the list should be exactly what we have added before sleep
                    //this way we assert that events passed to the listener are synchronised and any listener implementation is thread safe
                    assert called[-1] == task
                }
            }
        """

        when:
        run("foo")

        then:
        noExceptionThrown()
    }
}
