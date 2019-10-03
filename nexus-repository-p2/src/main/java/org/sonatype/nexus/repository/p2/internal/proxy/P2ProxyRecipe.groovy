/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2017-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.repository.p2.internal.proxy

import javax.annotation.Nonnull
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Provider
import javax.inject.Singleton

import org.sonatype.nexus.repository.Format
import org.sonatype.nexus.repository.RecipeSupport
import org.sonatype.nexus.repository.Repository
import org.sonatype.nexus.repository.Type
import org.sonatype.nexus.repository.attributes.AttributesFacet
import org.sonatype.nexus.repository.cache.NegativeCacheFacet
import org.sonatype.nexus.repository.cache.NegativeCacheHandler
import org.sonatype.nexus.repository.http.HttpHandlers
import org.sonatype.nexus.repository.http.PartialFetchHandler
import org.sonatype.nexus.repository.httpclient.HttpClientFacet
import org.sonatype.nexus.repository.p2.internal.AssetKind
import org.sonatype.nexus.repository.p2.internal.P2Format
import org.sonatype.nexus.repository.p2.internal.security.P2SecurityFacet
import org.sonatype.nexus.repository.proxy.ProxyHandler
import org.sonatype.nexus.repository.search.SearchFacet
import org.sonatype.nexus.repository.security.SecurityHandler
import org.sonatype.nexus.repository.storage.DefaultComponentMaintenanceImpl
import org.sonatype.nexus.repository.storage.StorageFacet
import org.sonatype.nexus.repository.storage.UnitOfWorkHandler
import org.sonatype.nexus.repository.types.ProxyType
import org.sonatype.nexus.repository.purge.PurgeUnusedFacet
import org.sonatype.nexus.repository.view.ConfigurableViewFacet
import org.sonatype.nexus.repository.view.Context
import org.sonatype.nexus.repository.view.Matcher
import org.sonatype.nexus.repository.view.Route.Builder
import org.sonatype.nexus.repository.view.Router
import org.sonatype.nexus.repository.view.ViewFacet
import org.sonatype.nexus.repository.view.handlers.BrowseUnsupportedHandler
import org.sonatype.nexus.repository.view.handlers.ConditionalRequestHandler
import org.sonatype.nexus.repository.view.handlers.ContentHeadersHandler
import org.sonatype.nexus.repository.view.handlers.ExceptionHandler
import org.sonatype.nexus.repository.view.handlers.HandlerContributor
import org.sonatype.nexus.repository.view.handlers.TimingHandler
import org.sonatype.nexus.repository.view.matchers.ActionMatcher
import org.sonatype.nexus.repository.view.matchers.RegexMatcher
import org.sonatype.nexus.repository.view.matchers.logic.LogicMatchers
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher

import static org.sonatype.nexus.repository.http.HttpMethods.GET
import static org.sonatype.nexus.repository.http.HttpMethods.HEAD
import static org.sonatype.nexus.repository.p2.internal.AssetKind.*
import static org.sonatype.nexus.repository.view.matchers.logic.LogicMatchers.and
import static org.sonatype.nexus.repository.view.matchers.logic.LogicMatchers.or

/**
 * P2 proxy repository recipe.
 */
@Named(P2ProxyRecipe.NAME)
@Singleton
class P2ProxyRecipe
    extends RecipeSupport
{
  public static final String NAME = 'p2-proxy'

  private static final CONTENT_NAME = "content"
  private static final ARTIFACTS_NAME = "artifacts"
  private static final COMPOSITE_ARTIFACTS = "compositeArtifacts"
  private static final COMPOSITE_CONTENT = "compositeContent"
  private static final XML_EXTENSION = ".*[xX][mM][lL]"
  private static final XML_XZ_EXTENSION = "${XML_EXTENSION}\\.[xX][zZ]"
  private static final JAR_EXTENSION = ".*[jJ][aA][rR]"
  private static final INDEX_EXTENSION = ".*[iI][nN][dD][eE][xX]"

  @Inject
  Provider<P2SecurityFacet> securityFacet

  @Inject
  Provider<ConfigurableViewFacet> viewFacet

  @Inject
  Provider<StorageFacet> storageFacet

  @Inject
  Provider<SearchFacet> searchFacet

  @Inject
  Provider<AttributesFacet> attributesFacet

  @Inject
  ExceptionHandler exceptionHandler

  @Inject
  TimingHandler timingHandler

  @Inject
  SecurityHandler securityHandler

  @Inject
  PartialFetchHandler partialFetchHandler

  @Inject
  ConditionalRequestHandler conditionalRequestHandler

  @Inject
  ContentHeadersHandler contentHeadersHandler

  @Inject
  UnitOfWorkHandler unitOfWorkHandler

  @Inject
  BrowseUnsupportedHandler browseUnsupportedHandler

  @Inject
  HandlerContributor handlerContributor

  @Inject
  Provider<DefaultComponentMaintenanceImpl> componentMaintenanceFacet

  @Inject
  Provider<HttpClientFacet> httpClientFacet

  @Inject
  Provider<P2ProxyFacetImpl> proxyFacet

  @Inject
  Provider<NegativeCacheFacet> negativeCacheFacet

  @Inject
  Provider<PurgeUnusedFacet> purgeUnusedFacet

  @Inject
  NegativeCacheHandler negativeCacheHandler

  @Inject
  ProxyHandler proxyHandler

  @Inject
  P2ProxyRecipe(@Named(ProxyType.NAME) final Type type,
                @Named(P2Format.NAME) final Format format) {
    super(type, format)
  }

  @Override
  void apply(@Nonnull final Repository repository) throws Exception {
    repository.attach(securityFacet.get())
    repository.attach(configure(viewFacet.get()))
    repository.attach(httpClientFacet.get())
    repository.attach(negativeCacheFacet.get())
    repository.attach(componentMaintenanceFacet.get())
    repository.attach(proxyFacet.get())
    repository.attach(storageFacet.get())
    repository.attach(searchFacet.get())
    repository.attach(purgeUnusedFacet.get())
    repository.attach(attributesFacet.get())
  }

  Closure assetKindHandler = { Context context, AssetKind value ->
    context.attributes.set(AssetKind, value)
    return context.proceed()
  }

  static Builder pluginsMatcher() {
    new Builder().matcher(
        and(
            new ActionMatcher(GET, HEAD),
            new RegexMatcher('.*plugins\\/.*'),
            componentFileTypeMatcher()
        ))
  }

  static Builder binaryMatcher() {
    new Builder().matcher(
        and(
            new ActionMatcher(GET, HEAD),
            new RegexMatcher('.*binary\\/.*'),
            binaryFileTypeMatcher()
        ))
  }

  static Builder featuresMatcher() {
    new Builder().matcher(
        and(
            new ActionMatcher(GET, HEAD),
            new RegexMatcher('.*features\\/.*'),
            componentFileTypeMatcher()
        ))
  }

  static Matcher componentFileTypeMatcher() {
    return or(
        tokenMatcherForExtensionAndName('jar'),
        tokenMatcherForExtensionAndName('jar.pack.gz')
    )
  }

  static Matcher binaryFileTypeMatcher() {
    return tokenMatcherForBinary()
  }

  static Builder matchRequestWithExtensionAndName(final String extension, final String name = '.+', final String path = '.+') {
    new Builder().matcher(
        and(
            new ActionMatcher(GET, HEAD),
            tokenMatcherForExtensionAndName(extension, name, path)
        ))
  }

  static TokenMatcher tokenMatcherForExtensionAndName(final String extension, final String name = '.+', final String path = '.+') {
    new TokenMatcher("{path:${path}}/{name:${name}}.{extension:${extension}}")
  }

  static TokenMatcher tokenMatcherForBinary() {
    new TokenMatcher("{path:.*}/{name:.*}_{version:.*}")
  }

  /**
   * Configure {@link ViewFacet}.
   */
  private ViewFacet configure(final ConfigurableViewFacet facet) {
    Router.Builder builder = new Router.Builder()

    builder.route(matchRequestWithExtensionAndName(INDEX_EXTENSION, 'p2', '.?')
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(P2_INDEX))
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(negativeCacheHandler)
        .handler(conditionalRequestHandler)
        .handler(partialFetchHandler)
        .handler(contentHeadersHandler)
        .handler(unitOfWorkHandler)
        .handler(proxyHandler)
        .create())

    builder.route(matchRequestWithExtensionAndName(JAR_EXTENSION, COMPOSITE_ARTIFACTS, '.?')
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(COMPOSITE_ARTIFACTS_JAR))
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(negativeCacheHandler)
        .handler(conditionalRequestHandler)
        .handler(partialFetchHandler)
        .handler(contentHeadersHandler)
        .handler(unitOfWorkHandler)
        .handler(proxyHandler)
        .create())

    builder.route(matchRequestWithExtensionAndName(XML_EXTENSION, COMPOSITE_ARTIFACTS, '.?')
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(COMPOSITE_ARTIFACTS_XML))
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(negativeCacheHandler)
        .handler(conditionalRequestHandler)
        .handler(partialFetchHandler)
        .handler(contentHeadersHandler)
        .handler(unitOfWorkHandler)
        .handler(proxyHandler)
        .create())

    builder.route(matchRequestWithExtensionAndName(JAR_EXTENSION, COMPOSITE_CONTENT, '.?')
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(COMPOSITE_CONTENT_JAR))
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(negativeCacheHandler)
        .handler(conditionalRequestHandler)
        .handler(partialFetchHandler)
        .handler(contentHeadersHandler)
        .handler(unitOfWorkHandler)
        .handler(proxyHandler)
        .create())

    builder.route(matchRequestWithExtensionAndName(XML_EXTENSION, COMPOSITE_CONTENT, '.?')
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(COMPOSITE_CONTENT_XML))
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(negativeCacheHandler)
        .handler(conditionalRequestHandler)
        .handler(partialFetchHandler)
        .handler(contentHeadersHandler)
        .handler(unitOfWorkHandler)
        .handler(proxyHandler)
        .create())

    builder.route(matchRequestWithExtensionAndName(JAR_EXTENSION, ARTIFACTS_NAME, '.*')
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(ARTIFACT_JAR))
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(negativeCacheHandler)
        .handler(conditionalRequestHandler)
        .handler(partialFetchHandler)
        .handler(contentHeadersHandler)
        .handler(unitOfWorkHandler)
        .handler(proxyHandler)
        .create())

    builder.route(matchRequestWithExtensionAndName(XML_EXTENSION, ARTIFACTS_NAME, '.*')
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(ARTIFACT_XML))
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(negativeCacheHandler)
        .handler(conditionalRequestHandler)
        .handler(partialFetchHandler)
        .handler(contentHeadersHandler)
        .handler(unitOfWorkHandler)
        .handler(proxyHandler)
        .create())

    builder.route(matchRequestWithExtensionAndName(XML_XZ_EXTENSION, ARTIFACTS_NAME, '.*')
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(ARTIFACT_XML_XZ))
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(negativeCacheHandler)
        .handler(conditionalRequestHandler)
        .handler(partialFetchHandler)
        .handler(contentHeadersHandler)
        .handler(unitOfWorkHandler)
        .handler(proxyHandler)
        .create())

    builder.route(matchRequestWithExtensionAndName(JAR_EXTENSION, CONTENT_NAME, '.*')
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(CONTENT_JAR))
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(negativeCacheHandler)
        .handler(conditionalRequestHandler)
        .handler(partialFetchHandler)
        .handler(contentHeadersHandler)
        .handler(unitOfWorkHandler)
        .handler(proxyHandler)
        .create())

    builder.route(matchRequestWithExtensionAndName(XML_EXTENSION, CONTENT_NAME, '.*')
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(CONTENT_XML))
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(negativeCacheHandler)
        .handler(conditionalRequestHandler)
        .handler(partialFetchHandler)
        .handler(contentHeadersHandler)
        .handler(unitOfWorkHandler)
        .handler(proxyHandler)
        .create())

    builder.route(matchRequestWithExtensionAndName(XML_XZ_EXTENSION, CONTENT_NAME, '.*')
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(CONTENT_XML_XZ))
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(negativeCacheHandler)
        .handler(conditionalRequestHandler)
        .handler(partialFetchHandler)
        .handler(contentHeadersHandler)
        .handler(unitOfWorkHandler)
        .handler(proxyHandler)
        .create())

    builder.route(binaryMatcher()
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(COMPONENT_BINARY))
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(negativeCacheHandler)
        .handler(conditionalRequestHandler)
        .handler(partialFetchHandler)
        .handler(contentHeadersHandler)
        .handler(unitOfWorkHandler)
        .handler(proxyHandler)
        .create())

    builder.route(featuresMatcher()
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(COMPONENT_FEATURES))
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(negativeCacheHandler)
        .handler(conditionalRequestHandler)
        .handler(partialFetchHandler)
        .handler(contentHeadersHandler)
        .handler(unitOfWorkHandler)
        .handler(proxyHandler)
        .create())

    builder.route(pluginsMatcher()
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(COMPONENT_PLUGINS))
        .handler(securityHandler)
        .handler(exceptionHandler)
        .handler(handlerContributor)
        .handler(negativeCacheHandler)
        .handler(conditionalRequestHandler)
        .handler(partialFetchHandler)
        .handler(contentHeadersHandler)
        .handler(unitOfWorkHandler)
        .handler(proxyHandler)
        .create())

    builder.defaultHandlers(HttpHandlers.notFound())

    facet.configure(builder.create())

    return facet
  }
}
