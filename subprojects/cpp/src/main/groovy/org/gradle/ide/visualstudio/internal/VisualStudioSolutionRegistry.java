/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.ide.visualstudio.internal;

import org.gradle.api.internal.DefaultNamedDomainObjectSet;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativebinaries.NativeComponentBinary;

public class VisualStudioSolutionRegistry extends DefaultNamedDomainObjectSet<DefaultVisualStudioSolution> {
    private final FileResolver fileResolver;
    private final VisualStudioProjectRegistry localProjects;
    private final VisualStudioProjectResolver projectResolver;

    public VisualStudioSolutionRegistry(FileResolver fileResolver, VisualStudioProjectResolver projectResolver, VisualStudioProjectRegistry localProjects, Instantiator instantiator) {
        super(DefaultVisualStudioSolution.class, instantiator);
        this.fileResolver = fileResolver;
        this.localProjects = localProjects;
        this.projectResolver = projectResolver;
    }

    public DefaultVisualStudioSolution addSolution(NativeComponentBinary nativeBinary) {
        DefaultVisualStudioSolution solution = createSolution(nativeBinary);
        add(solution);
        return solution;
    }

    private DefaultVisualStudioSolution createSolution(NativeComponentBinary nativeBinary) {
        return new DefaultVisualStudioSolution(rootConfiguration(nativeBinary), nativeBinary, fileResolver, projectResolver, getInstantiator());
    }

    private VisualStudioProjectConfiguration rootConfiguration(NativeComponentBinary nativeBinary) {
        return localProjects.getProjectConfiguration(nativeBinary);
    }
}
