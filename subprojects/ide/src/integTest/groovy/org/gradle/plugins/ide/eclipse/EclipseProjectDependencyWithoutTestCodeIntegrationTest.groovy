/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.plugins.ide.eclipse


import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.plugins.ide.eclipse.internal.EclipsePluginConstants

class EclipseProjectDependencyWithoutTestCodeIntegrationTest extends AbstractEclipseIntegrationSpec {

    @ToBeFixedForConfigurationCache
    def "test code is not available by default"() {
        settingsFile << "include 'a', 'b'"
        file('a/build.gradle') << """
            plugins {
                id 'eclipse'
                id 'java-library'
            }
        """
        file('b/build.gradle') << """
            plugins {
                id 'eclipse'
                id 'java-library'
            }

            dependencies {
                implementation project(':a')
            }
        """

        when:
        run "eclipse"

        then:
        def projectDependency = classpath('b').projects.find { it.name == 'a' }
        projectDependency.assertHasAttribute(EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_KEY, EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_VALUE)
    }

    @ToBeFixedForConfigurationCache
    def "test code is available if target project applies the java-test-fixtures plugin"() {
        settingsFile << "include 'a', 'b'"
        file('a/build.gradle') << """
            plugins {
                id 'eclipse'
                id 'java-library'
                id 'java-test-fixtures'
            }
        """
        file('b/build.gradle') << """
            plugins {
                id 'eclipse'
                id 'java-library'
            }

            dependencies {
                implementation project(':a')
            }
        """

        when:
        run "eclipse"

        then:
        def projectDependency = classpath('b').projects.find { it.name == 'a' }
        projectDependency.assertHasNoAttribute(EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_KEY, EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_VALUE)
    }

    @ToBeFixedForConfigurationCache
    def "test code is available if target project has the eclipse.classpath.containsTestFixtures=true configures"() {
        settingsFile << "include 'a', 'b'"
        file('a/build.gradle') << """
            plugins {
                id 'eclipse'
                id 'java-library'
            }

            eclipse {
                classpath {
                    containsTestFixtures = true
                }
            }
        """
        file('b/build.gradle') << """
            plugins {
                id 'eclipse'
                id 'java-library'
            }

            dependencies {
                implementation project(':a')
            }
        """

        when:
        run "eclipse"

        then:
        def projectDependency = classpath('b').projects.find { it.name == 'a' }
        projectDependency.assertHasAttribute(EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_KEY, 'false')
    }

    @ToBeFixedForConfigurationCache
    def "the eclipse.classpath.containsTestFixtures configuration has precedence over the applied java-test-fixtures plugin"() {
        settingsFile << "include 'a', 'b'"
        file('a/build.gradle') << """
            plugins {
                id 'eclipse'
                id 'java-test-fixtures'
                id 'java-library'
            }

            eclipse {
                classpath {
                    containsTestFixtures = false
                }
            }
        """
        file('b/build.gradle') << """
            plugins {
                id 'eclipse'
                id 'java-library'
            }

            dependencies {
                implementation project(':a')
            }
        """

        when:
        run "eclipse"

        then:
        def projectDependency = classpath('b').projects.find { it.name == 'a' }
        projectDependency.assertHasAttribute(EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_KEY, EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_VALUE)
    }
}
