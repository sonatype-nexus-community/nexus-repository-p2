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
import org.sonatype.nexus.repository.view.matchers.logic.LogicMatchers
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher

import static org.sonatype.nexus.repository.http.HttpMethods.GET
import static org.sonatype.nexus.repository.http.HttpMethods.HEAD
import static org.sonatype.nexus.repository.p2.internal.AssetKind.*

/**
 * P2 proxy repository recipe.
 */
@Named(P2ProxyRecipe.NAME)
@Singleton
class P2ProxyRecipe
    extends RecipeSupport
{
  public static final String NAME = 'p2-proxy'

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

  /**
   * Matcher for p2.index mapping.
   */
  static Builder p2IndexMatcher() {
    new Builder().matcher(
        LogicMatchers.and(
            new ActionMatcher(GET, HEAD),
            new TokenMatcher('/{path:.+}/p2.index')
        ))
  }

  /**
   * Matcher for artifacts.jar mapping.
   */
  static Builder artifactsJarMatcher() {
    new Builder().matcher(
        LogicMatchers.and(
            new ActionMatcher(GET, HEAD),
            new TokenMatcher('/{path:.+}/{filename:.+}')
        ))
  }

  /**
   * Matcher for artifacts.xml mapping.
   */
  static Builder artifactsXmlMatcher() {
    new Builder().matcher(
        LogicMatchers.and(
            new ActionMatcher(GET, HEAD),
            new TokenMatcher('/{path:.+}/artifacts.xml')
        ))
  }

  /**
   * Matcher for artifacts.xml.xz mapping.
   */
  static Builder artifactsXmlXzMatcher() {
    new Builder().matcher(
        LogicMatchers.and(
            new ActionMatcher(GET, HEAD),
            new TokenMatcher('/{path:.+}/artifacts.xml.xz')
        ))
  }

  /**
   * Matcher for content.jar mapping.
   */
  static Builder contentJarMatcher() {
    new Builder().matcher(
        LogicMatchers.and(
            new ActionMatcher(GET, HEAD),
            new TokenMatcher('/{path:.+}/content.jar')
        ))
  }

  /**
   * Matcher for content.xml mapping.
   */
  static Builder contentXmlMatcher() {
    new Builder().matcher(
        LogicMatchers.and(
            new ActionMatcher(GET, HEAD),
            new TokenMatcher('/{path:.+}/content.xml')
        ))
  }

  /**
   * Matcher for content.xml.xz mapping.
   */
  static Builder contentXmlXzMatcher() {
    new Builder().matcher(
        LogicMatchers.and(
            new ActionMatcher(GET, HEAD),
            new TokenMatcher('/{path:.+}/content.xml.xz')
        ))
  }

  /**
   * Matcher for {path}/plugins/{filename}.jar mapping.
   */
  static Builder componentPluginJarMatcher() {
    new Builder().matcher(
        LogicMatchers.and(
            new ActionMatcher(GET, HEAD),
            new TokenMatcher('/{path:.+}/plugins/{filename}.jar')
        ))
  }

  /**
   * Matcher for {path}/features/{filename}.jar mapping.
   */
  static Builder componentFeatureJarMatcher() {
    new Builder().matcher(
        LogicMatchers.and(
            new ActionMatcher(GET, HEAD),
            new TokenMatcher('/{path:.+}/features/{filename}.jar')
        ))
  }

  /**
   * Matcher for {path}/plugins/{filename}.pack.gz mapping.
   */
  static Builder componentPluginPackGzMatcher() {
    new Builder().matcher(
        LogicMatchers.and(
            new ActionMatcher(GET, HEAD),
            new TokenMatcher('/{path:.+}/plugins/{filename}.pack.gz')
        ))
  }

  /**
   * Matcher for {path}/features/{filename}.pack.gz mapping.
   */
  static Builder componentFeaturePackGzMatcher() {
    new Builder().matcher(
        LogicMatchers.and(
            new ActionMatcher(GET, HEAD),
            new TokenMatcher('/{path:.+}/features/{filename}.pack.gz')
        ))
  }

  /**
   * Configure {@link ViewFacet}.
   */
  private ViewFacet configure(final ConfigurableViewFacet facet) {
    Router.Builder builder = new Router.Builder()

    builder.route(p2IndexMatcher()
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

    builder.route(artifactsJarMatcher()
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

    builder.route(artifactsXmlMatcher()
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

    builder.route(artifactsXmlXzMatcher()
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

    builder.route(contentJarMatcher()
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

    builder.route(contentXmlMatcher()
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

    builder.route(contentXmlXzMatcher()
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

    builder.route(componentFeatureJarMatcher()
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(COMPONENT_FEATURES_JAR))
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

    builder.route(componentFeaturePackGzMatcher()
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(COMPONENT_FEATURES_PACK_GZ))
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

    builder.route(componentPluginJarMatcher()
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(COMPONENT_PLUGINS_JAR))
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

    builder.route(componentPluginPackGzMatcher()
        .handler(timingHandler)
        .handler(assetKindHandler.rcurry(COMPONENT_PLUGINS_PACK_GZ))
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
