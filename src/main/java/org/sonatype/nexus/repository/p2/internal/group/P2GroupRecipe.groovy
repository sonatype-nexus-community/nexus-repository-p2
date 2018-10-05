/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2018-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.repository.p2.internal.group

import javax.annotation.Nonnull
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider
import javax.inject.Singleton

import org.sonatype.nexus.repository.Format
import org.sonatype.nexus.repository.Repository
import org.sonatype.nexus.repository.Type
import org.sonatype.nexus.repository.group.GroupFacetImpl
import org.sonatype.nexus.repository.group.GroupHandler
import org.sonatype.nexus.repository.http.HttpHandlers
import org.sonatype.nexus.repository.p2.internal.P2Format
import org.sonatype.nexus.repository.p2.internal.P2RecipeSupport
import org.sonatype.nexus.repository.types.GroupType
import org.sonatype.nexus.repository.view.ConfigurableViewFacet
import org.sonatype.nexus.repository.view.Router
import org.sonatype.nexus.repository.view.ViewFacet

import static org.sonatype.nexus.repository.p2.internal.AssetKind.*

/**
 * Recipe for creating a P2 group repository.
 */
@Named(P2GroupRecipe.NAME)
@Singleton
class P2GroupRecipe
  extends P2RecipeSupport
{
  public static final String NAME = 'p2-group'

  @Inject
  Provider<GroupFacetImpl> groupFacet

  @Inject
  GroupHandler standardGroupHandler

  @Inject
  P2GroupArtifactsMergingHandler artifactsMergingHandler

  @Inject
  P2GroupRecipe(@Named(GroupType.NAME) final Type type, @Named(P2Format.NAME) final Format format) {
    super(type, format)
  }

  @Override
  void apply(@Nonnull final Repository repository) throws Exception {
    repository.attach(groupFacet.get())
    repository.attach(storageFacet.get())
    repository.attach(securityFacet.get())
    repository.attach(configure(viewFacet.get()))
    repository.attach(attributesFacet.get())
  }

  private ViewFacet configure(final ConfigurableViewFacet facet) {
    Router.Builder builder = new Router.Builder()

    builder.route(matchRequestWithExtensionAndName(INDEX_EXTENSION, 'p2', '.?')
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(P2_INDEX))
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(standardGroupHandler)
        .create())

    builder.route(matchRequestWithExtensionAndName(JAR_EXTENSION, COMPOSITE_ARTIFACTS, '.?')
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(COMPOSITE_ARTIFACTS_JAR))
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(standardGroupHandler)
        .create())

    builder.route(matchRequestWithExtensionAndName(XML_EXTENSION, COMPOSITE_ARTIFACTS, '.?')
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(COMPOSITE_ARTIFACTS_XML))
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(standardGroupHandler)
        .create())

    builder.route(matchRequestWithExtensionAndName(JAR_EXTENSION, COMPOSITE_CONTENT, '.?')
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(COMPOSITE_CONTENT_JAR))
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(standardGroupHandler)
        .create())

    builder.route(matchRequestWithExtensionAndName(XML_EXTENSION, COMPOSITE_CONTENT, '.?')
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(COMPOSITE_CONTENT_XML))
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(standardGroupHandler)
        .create())

    builder.route(matchRequestWithExtensionAndName(JAR_EXTENSION, ARTIFACTS_NAME, '.*')
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(ARTIFACT_JAR))
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(artifactsMergingHandler)
        .create())

    builder.route(matchRequestWithExtensionAndName(XML_EXTENSION, ARTIFACTS_NAME, '.*')
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(ARTIFACT_XML))
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(artifactsMergingHandler)
        .create())

    builder.route(matchRequestWithExtensionAndName(XML_XZ_EXTENSION, ARTIFACTS_NAME, '.*')
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(ARTIFACT_XML_XZ))
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(artifactsMergingHandler)
        .create())

    builder.route(matchRequestWithExtensionAndName(JAR_EXTENSION, CONTENT_NAME, '.*')
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(CONTENT_JAR))
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(standardGroupHandler)
        .create())

    builder.route(matchRequestWithExtensionAndName(XML_EXTENSION, CONTENT_NAME, '.*')
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(CONTENT_XML))
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(standardGroupHandler)
        .create())

    builder.route(matchRequestWithExtensionAndName(XML_XZ_EXTENSION, CONTENT_NAME, '.*')
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(CONTENT_XML_XZ))
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(standardGroupHandler)
        .create())

    builder.route(binaryMatcher()
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(COMPONENT_BINARY))
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(standardGroupHandler)
        .create())

    builder.route(featuresMatcher()
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(COMPONENT_FEATURES))
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(standardGroupHandler)
        .create())

    builder.route(pluginsMatcher()
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(COMPONENT_PLUGINS))
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(standardGroupHandler)
        .create())

    addBrowseUnsupportedRoute(builder)

    builder.defaultHandlers(HttpHandlers.notFound())

    facet.configure(builder.create())

    return facet
  }
}
