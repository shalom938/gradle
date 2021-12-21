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

package org.gradle.plugins.ide.eclipse

import org.gradle.plugins.ide.eclipse.internal.EclipsePluginConstants
import org.gradle.tooling.model.eclipse.EclipseClasspathEntry

abstract class AbstractEclipseTestSourcesIntegrationTest extends AbstractEclipseIntegrationSpec {

    void assertProjectDependencyDoesNotHaveTestAttribute(String project, String targetProject) {
        projectDoesNotHaveTestAttribute(classpath(project), targetProject)
    }

    void assertSourceDirectoryHasTestAttribute(String project, String targetProject) {
        projectHasTestAttribute(classpath(project), targetProject)
    }

    void assertSourceDirectoryDoesNotHaveTestAttribute(String path) {
        sourceDirectoryDoesNotHaveTestAttribute(classpath, path)
    }

    void assertSourceDirectoryHasTestAttribute(String path) {
        sourceDirectoryHasTestAttribute(classpath, path)
    }

    void assertJarDependencyDoesNotHaveTestAttribute(String jarName) {
        jarDoesNotHaveTestAttribute(classpath, jarName)
    }

    void assertJarDependencyHasTestAttribute(String jarName) {
        jarHasTestAttribute(classpath, jarName)
    }

    private static void projectDoesNotHaveTestAttribute(EclipseClasspathFixture classpath, String targetProject) {
        doesNotHaveTestAttribute(classpath.projects.find {it.name == targetProject })
    }

    private static void projectHasTestAttribute(EclipseClasspathFixture classpath, String targetProject) {
        hasTestAttribute(classpath.projects.find {it.name == targetProject })
    }

    private static void sourceDirectoryDoesNotHaveTestAttribute(EclipseClasspathFixture classpath, String path) {
        doesNotHaveTestAttribute(classpath.sourceDir(path))
    }

    private static void sourceDirectoryHasTestAttribute(EclipseClasspathFixture classpath, String path) {
        hasTestAttribute(classpath.sourceDir(path))
    }

    private static void jarDoesNotHaveTestAttribute(EclipseClasspathFixture classpath, String jarName) {
        doesNotHaveTestAttribute(classpath.lib(jarName))
    }

    private static void jarHasTestAttribute(EclipseClasspathFixture classpath, String jarName) {
        hasTestAttribute(classpath.lib(jarName))
    }

    private static void doesNotHaveTestAttribute(EclipseClasspathEntry entry) {
        entry.assertHasNoAttribute(EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY, EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE)
    }

    private static void hasTestAttribute(EclipseClasspathEntry entry) {
        entry.assertHasAttribute(EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_KEY, EclipsePluginConstants.TEST_SOURCES_ATTRIBUTE_VALUE)
    }
}
