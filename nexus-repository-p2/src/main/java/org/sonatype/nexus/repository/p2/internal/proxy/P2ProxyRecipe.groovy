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
import org.sonatype.nexus.repository.p2.internal.P2ComponentMaintenance
import org.sonatype.nexus.repository.p2.internal.P2Format
import org.sonatype.nexus.repository.p2.internal.security.P2SecurityFacet
import org.sonatype.nexus.repository.proxy.ProxyHandler
import org.sonatype.nexus.repository.purge.PurgeUnusedFacet
import org.sonatype.nexus.repository.routing.RoutingRuleHandler
import org.sonatype.nexus.repository.search.SearchFacet
import org.sonatype.nexus.repository.security.SecurityHandler
import org.sonatype.nexus.repository.storage.StorageFacet
import org.sonatype.nexus.repository.storage.UnitOfWorkHandler
import org.sonatype.nexus.repository.types.ProxyType
import org.sonatype.nexus.repository.view.ConfigurableViewFacet
import org.sonatype.nexus.repository.view.Context
import org.sonatype.nexus.repository.view.Matcher
import org.sonatype.nexus.repository.view.Route
import org.sonatype.nexus.repository.view.Router
import org.sonatype.nexus.repository.view.ViewFacet
import org.sonatype.nexus.repository.view.handlers.BrowseUnsupportedHandler
import org.sonatype.nexus.repository.view.handlers.ConditionalRequestHandler
import org.sonatype.nexus.repository.view.handlers.ContentHeadersHandler
import org.sonatype.nexus.repository.view.handlers.ExceptionHandler
import org.sonatype.nexus.repository.view.handlers.FormatHighAvailabilitySupportHandler
import org.sonatype.nexus.repository.view.handlers.HandlerContributor
import org.sonatype.nexus.repository.view.handlers.HighAvailabilitySupportChecker
import org.sonatype.nexus.repository.view.handlers.TimingHandler
import org.sonatype.nexus.repository.view.matchers.ActionMatcher
import org.sonatype.nexus.repository.view.matchers.RegexMatcher
import org.sonatype.nexus.repository.view.matchers.logic.LogicMatchers
import org.sonatype.nexus.repository.view.matchers.token.TokenMatcher

import static org.sonatype.nexus.repository.http.HttpMethods.GET
import static org.sonatype.nexus.repository.http.HttpMethods.HEAD
import static org.sonatype.nexus.repository.p2.internal.AssetKind.*
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
  Provider<P2ComponentMaintenance> componentMaintenanceFacet

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
  RoutingRuleHandler routingRuleHandler

  @Inject
  FormatHighAvailabilitySupportHandler highAvailabilitySupportHandler;

  @Inject
  HighAvailabilitySupportChecker highAvailabilitySupportChecker

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

  static Matcher pluginBinaryAndFeaturesMatcher(final String pattern,
                                                final AssetKind assetKind,
                                                final Matcher matcher,
                                                final String... actions) {
    LogicMatchers.and(
        new ActionMatcher(actions),
        new RegexMatcher(pattern),
        matcher,
        new Matcher() {
          @Override
          boolean matches(final Context context) {
            context.attributes.set(AssetKind.class, assetKind)
            return true
          }
        }
    )
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

  static TokenMatcher tokenMatcherForBinary() {
    new TokenMatcher("/{path:.*}/{name:.*}_{version:.*}")
  }

  static TokenMatcher tokenMatcherForExtensionAndName(final String extension, final String name = '.+', final String path = '.+') {
    new TokenMatcher("/{path:${path}}/{name:${name}}.{extension:${extension}}")
  }

  static Matcher buildSimpleMatcher(final String path, final String name, final String extension, final AssetKind assetKind) {
    buildTokenMatcherForPatternAndAssetKind("/{path:${path}}/{name:${name}}.{extension:${extension}}", assetKind, GET, HEAD)
  }

  static Matcher buildSimpleMatcherAtRoot(final String name, final String extension, final AssetKind assetKind) {
    buildTokenMatcherForPatternAndAssetKind("/{name:${name}}.{extension:${extension}}", assetKind, GET, HEAD)
  }

  static Matcher buildTokenMatcherForPatternAndAssetKind(final String pattern,
                                                         final AssetKind assetKind,
                                                         final String... actions) {
    LogicMatchers.and(
        new ActionMatcher(actions),
        new TokenMatcher(pattern),
        new Matcher() {
          @Override
          boolean matches(final Context context) {
            context.attributes.set(AssetKind.class, assetKind)
            return true
          }
        }
    )
  }

  /**
   * Configure {@link ViewFacet}.
   */
  private ViewFacet configure(final ConfigurableViewFacet facet) {
    Router.Builder builder = new Router.Builder()

    [buildSimpleMatcher('.*', 'p2', 'index', P2_INDEX),
     buildSimpleMatcherAtRoot('p2', 'index', P2_INDEX),
     buildSimpleMatcherAtRoot(COMPOSITE_ARTIFACTS, JAR_EXTENSION, COMPOSITE_ARTIFACTS_JAR),
     buildSimpleMatcherAtRoot(COMPOSITE_ARTIFACTS, XML_EXTENSION, COMPOSITE_ARTIFACTS_XML),
     buildSimpleMatcherAtRoot(COMPOSITE_CONTENT, JAR_EXTENSION, COMPOSITE_CONTENT_JAR),
     buildSimpleMatcherAtRoot(COMPOSITE_CONTENT, XML_EXTENSION, COMPOSITE_CONTENT_XML),

     buildSimpleMatcherAtRoot(CONTENT_NAME, JAR_EXTENSION, CONTENT_JAR),
     buildSimpleMatcherAtRoot(CONTENT_NAME, XML_EXTENSION, CONTENT_XML),
     buildSimpleMatcherAtRoot(CONTENT_NAME, XML_XZ_EXTENSION, CONTENT_XML_XZ),
     buildSimpleMatcherAtRoot(ARTIFACTS_NAME, JAR_EXTENSION, ARTIFACT_JAR),
     buildSimpleMatcherAtRoot(ARTIFACTS_NAME, XML_EXTENSION, ARTIFACT_XML),
     buildSimpleMatcherAtRoot(ARTIFACTS_NAME, XML_XZ_EXTENSION, ARTIFACT_XML_XZ),

     buildSimpleMatcher('.*', ARTIFACTS_NAME, JAR_EXTENSION, ARTIFACT_JAR),
     buildSimpleMatcher('.*', ARTIFACTS_NAME, XML_EXTENSION, ARTIFACT_XML),
     buildSimpleMatcher('.*', ARTIFACTS_NAME, XML_XZ_EXTENSION, ARTIFACT_XML_XZ),
     buildSimpleMatcher('.*', CONTENT_NAME, JAR_EXTENSION, CONTENT_JAR),
     buildSimpleMatcher('.*', CONTENT_NAME, XML_EXTENSION, CONTENT_XML),
     buildSimpleMatcher('.*', CONTENT_NAME, XML_XZ_EXTENSION, CONTENT_XML_XZ),
     pluginBinaryAndFeaturesMatcher('.*features\\/.*', COMPONENT_FEATURES, componentFileTypeMatcher(), GET, HEAD),
     pluginBinaryAndFeaturesMatcher('.*binary\\/.*', COMPONENT_BINARY, binaryFileTypeMatcher(), GET, HEAD),
     pluginBinaryAndFeaturesMatcher('.*plugins\\/.*', COMPONENT_PLUGINS, componentFileTypeMatcher(), GET, HEAD)].each { matcher ->
      builder.route(new Route.Builder().matcher(matcher)
          .handler(timingHandler)
          .handler(securityHandler)
          .handler(highAvailabilitySupportHandler)
          .handler(routingRuleHandler)
          .handler(exceptionHandler)
          .handler(handlerContributor)
          .handler(negativeCacheHandler)
          .handler(conditionalRequestHandler)
          .handler(partialFetchHandler)
          .handler(contentHeadersHandler)
          .handler(unitOfWorkHandler)
          .handler(proxyHandler)
          .create())
    }

    builder.defaultHandlers(HttpHandlers.notFound())

    facet.configure(builder.create())

    return facet
  }

  @Override
  boolean isFeatureEnabled() {
    return highAvailabilitySupportChecker.isSupported(getFormat().getValue());
  }
}
