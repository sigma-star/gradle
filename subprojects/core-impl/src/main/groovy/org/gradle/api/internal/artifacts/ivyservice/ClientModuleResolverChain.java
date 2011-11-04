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

package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.core.cache.ArtifactOrigin;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.report.DownloadReport;
import org.apache.ivy.core.resolve.DownloadOptions;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;

/**
 * Resolver which looks for definitions first in defined Client Modules, before delegating to the user-defined resolver chain.
 * Artifact download is delegated to user-defined resolver chain.
 */
public class ClientModuleResolverChain extends AbstractLimitedDependencyResolver implements DependencyResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(ClientModuleResolverChain.class);
    private final ClientModuleResolver clientModuleResolver;
    private final DependencyResolver userResolverChain;

    public ClientModuleResolverChain(ClientModuleResolver clientModuleResolver, DependencyResolver userResolverChain) {
        this.clientModuleResolver = clientModuleResolver;
        this.userResolverChain = userResolverChain;
    }

    public ResolvedModuleRevision getDependency(DependencyDescriptor dd, ResolveData data) throws ParseException {
        ResolvedModuleRevision clientModuleDependency = clientModuleResolver.getDependency(dd, data);
        if (clientModuleDependency != null) {
            LOGGER.debug("Found client module:", clientModuleDependency);
            return clientModuleDependency;
        }
        return userResolverChain.getDependency(dd, data);
    }

    public ArtifactOrigin locate(Artifact artifact) {
        return userResolverChain.locate(artifact);
    }

    public DownloadReport download(Artifact[] artifacts, DownloadOptions options) {
        return userResolverChain.download(artifacts, options);
    }
}
