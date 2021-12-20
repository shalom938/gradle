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

import org.apache.commons.io.FileUtils
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.plugins.ide.eclipse.internal.EclipsePluginConstants
import spock.lang.IgnoreRest

class EclipseTestDependenciesIntegrationTest extends AbstractEclipseIntegrationSpec {

    @ToBeFixedForConfigurationCache
    def "project dependency does not leak test sources"() {
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
    def "project dependency pointing to test fixture project exposes test sources"() {
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
    def "can configure test sources via eclipse classpath"() {
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
    def "classpath configuration has precedence for test dependencies"() {
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

    def "can configure test dependencies"() {
        given:
        settingsFile << "include 'a', 'b', 'c'"
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
        """
        file('c/build.gradle') << """
            plugins {
                id 'eclipse'
                id 'java-library'
            }

            configurations {
                integration
            }

            dependencies {
                integration project(':a')
                testImplementation project(':b')
            }

            eclipse {
                classpath {
                    plusConfigurations += [configurations.integration]
                    testConfigurations = [configurations.integration]
                }
            }
        """

        when:
        run 'eclipse'

        then:
        def dependencyA = classpath('c').projects.find { it.name == 'a' }
        def dependencyB = classpath('c').projects.find { it.name == 'b' }

        dependencyA.assertHasAttribute(EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY, EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE)
        dependencyB.assertHasNoAttribute(EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY, EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE)
    }

    private static final Closure DEPENDENCY_WITH_TEST_SOURCES = { EclipseClasspathFixture.EclipseClasspathEntry d ->
        d.assertHasNoAttribute(EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY, EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE)
        d.assertHasAttribute(EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_KEY, 'false')
    }

    private static final Closure DEPENDENCY_WITHOUT_TEST_SOURCES = { EclipseClasspathFixture.EclipseClasspathEntry d ->
        d.assertHasNoAttribute(EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY, EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE)
        d.assertHasNoAttribute(EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_KEY, 'false')
    }

    private static final Closure TEST_DEPENDENCY_WITH_TEST_SOURCES = { EclipseClasspathFixture.EclipseClasspathEntry d ->
        d.assertHasAttribute(EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY, EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE)
        d.assertHasAttribute(EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_KEY, 'false')
    }

    private static final Closure TEST_DEPENDENCY_WITHOUT_TEST_SOURCES = { EclipseClasspathFixture.EclipseClasspathEntry d ->
        d.assertHasAttribute(EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY, EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE)
        d.assertHasNoAttribute(EclipsePluginConstants.WITHOUT_TEST_CODE_ATTRIBUTE_KEY, 'false')
    }
}
