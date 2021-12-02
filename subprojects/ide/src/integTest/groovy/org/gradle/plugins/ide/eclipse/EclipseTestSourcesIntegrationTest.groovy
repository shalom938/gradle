/*
 * Copyright 2019 the original author or authors.
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

class EclipseTestSourcesIntegrationTest extends AbstractEclipseIntegrationSpec {

    @ToBeFixedForConfigurationCache
    def "All test source folders and test dependencies are marked with test attribute"() {
        setup:
        file('src/main/java').mkdirs()
        file('src/test/java').mkdirs()
        buildFile << """
            plugins {
                id 'java'
                id 'eclipse'
            }

            ${mavenCentralRepository()}

            dependencies {
                 implementation "com.google.guava:guava:21.0"
                 testImplementation "junit:junit:4.13"
            }
        """
        when:
        run 'eclipse'

        then:
        classpath.lib("guava-21.0.jar").assertHasNoAttribute(EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY, EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE)
        classpath.lib("junit-4.13.jar").assertHasAttribute(EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY, EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE)
        classpath.sourceDir("src/main/java").assertHasNoAttribute(EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY, EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE)
        classpath.sourceDir("src/test/java").assertHasAttribute(EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY, EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE)
    }

    def "Can configure test source sets"() {
        setup:
        file('src/main/java').mkdirs()
        file('src/test/java').mkdirs()
        buildFile << """
            plugins {
                id 'java'
                id 'eclipse'
            }

            eclipse {
                classpath {
                    testSourceSes = '.*main.*'
                }
            }
        """

        when:
        run 'eclipse'

        then:
        EclipseClasspathFixture classpath = classpath('.')
        classpath.sourceDir('src/main/java').assertHasAttribute(EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY, EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE)
        classpath.sourceDir('src/test/java').assertHasNoAttribute(EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY, EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE)
    }
}
