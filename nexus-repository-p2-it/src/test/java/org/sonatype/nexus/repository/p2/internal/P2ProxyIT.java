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
package org.sonatype.nexus.repository.p2.internal;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;

import org.sonatype.goodies.httpfixture.server.fluent.Server;
import org.sonatype.nexus.common.entity.EntityHelper;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.http.HttpStatus;
import org.sonatype.nexus.repository.storage.Asset;
import org.sonatype.nexus.repository.storage.ComponentMaintenance;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.tukaani.xz.XZInputStream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayContaining;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.sonatype.nexus.testsuite.testsupport.FormatClientSupport.status;

public class P2ProxyIT
    extends P2ITSupport
{
  private static final String PATH_PLUGIN = "plugins/com.sonatype.nexus_1.2.0.v20170518-1049.jar.pack.gz";

  private static final String BINARY_PATH = "binary/org.eclipse.platform.ide.executable.gtk.linux.x86_64_4.15.0.I20200110-0905";

  private static final String FEATURE_PATH = "features/com.sonatype.nexus.feature_1.2.0.v20170518-1049.jar";

  private static final String TEXT_MIME_TYPE = "text/plain";

  private static final String PATH_COMPOSITE_CHILD = "composite-child";

  private static final String PATH_CHILD_SITE = "child";

  private static final String ASSET_KIND_ARTIFACTS_METADATA = "ARTIFACTS_METADATA";

  private static final String ASSET_KIND_BUNDLE = "BUNDLE";

  private static final String ASSET_KIND_BINARY_BUNDLE = "BINARY_BUNDLE";

  private static final String ASSET_KIND_COMPOSITE_CONTENT = "COMPOSITE_CONTENT";

  private static final String ASSET_KIND_COMPOSITE_ARTIFACTS = "COMPOSITE_ARTIFACTS";

  private static final String ASSET_KIND_CONTENT_METADATA = "CONTENT_METADATA";

  private static final String ASSET_KIND_P2_INDEX = "P2_INDEX";

  private static final String FORMAT_NAME = "p2";

  private static final String ARTIFACTS_JAR = "artifacts.jar";

  private static final String ARTIFACTS_XML = "artifacts.xml";

  private static final String ARTIFACTS_XML_XZ = "artifacts.xml.xz";

  private static final String COMPOSITE_ARTIFACTS_JAR = "compositeArtifacts.jar";

  private static final String COMPOSITE_ARTIFACTS_XML = "compositeArtifacts.xml";

  private static final String COMPOSITE_CONTENT_JAR = "compositeContent.jar";

  private static final String COMPOSITE_CONTENT_XML = "compositeContent.xml";

  private static final String CONTENT_JAR = "content.jar";

  private static final String CONTENT_XML = "content.xml";

  private static final String CONTENT_XML_XZ = "content.xml.xz";

  private static final String P2_INDEX = "p2.index";

  private P2Client compositeSiteClient;

  private Repository compositeSiteRepo;

  private Repository siteRepo;

  @Before
  public void setup() throws Exception {
    server = Server.withPort(0);
    buildSite(testData.resolveFile("sample-site"));
    server.start();

    compositeSiteRepo = repos.createP2Proxy("p2-test-composite-proxy", server.getUrl().toExternalForm());
    compositeSiteClient = p2Client(compositeSiteRepo);

    siteRepo = repos.createP2Proxy("p2-test-proxy", server.getUrl().toExternalForm() + "/" + PATH_CHILD_SITE);
  }

  @Test
  public void unresponsiveRemoteProduces502() throws Exception {
    server.stop();
    assertThat(status(compositeSiteClient.get(COMPOSITE_ARTIFACTS_JAR)), is(HttpStatus.BAD_GATEWAY));
  }

  @Test
  public void testArtifacts() throws Exception {
    testPath(siteRepo, ARTIFACTS_JAR, JAR_MIME_TYPE, ASSET_KIND_ARTIFACTS_METADATA);
    testPath(siteRepo, ARTIFACTS_XML, XML_MIME_TYPE, ASSET_KIND_ARTIFACTS_METADATA);
    testPath(siteRepo, ARTIFACTS_XML_XZ, XZ_MIME_TYPE, ASSET_KIND_ARTIFACTS_METADATA);

    // test non-root
    assertOk(compositeSiteClient, COMPOSITE_ARTIFACTS_XML); // prime
    testPath(compositeSiteRepo, childSitePath(PATH_CHILD_SITE, ARTIFACTS_JAR), JAR_MIME_TYPE,
        ASSET_KIND_ARTIFACTS_METADATA);
    testPath(compositeSiteRepo, childSitePath(PATH_CHILD_SITE, ARTIFACTS_XML), XML_MIME_TYPE,
        ASSET_KIND_ARTIFACTS_METADATA);
    testPath(compositeSiteRepo, childSitePath(PATH_CHILD_SITE, ARTIFACTS_XML_XZ), XZ_MIME_TYPE,
        ASSET_KIND_ARTIFACTS_METADATA);

    // Uses cached assets when remote offline
    server.stop();
    testPath(siteRepo, ARTIFACTS_JAR, JAR_MIME_TYPE, ASSET_KIND_ARTIFACTS_METADATA);
    testPath(siteRepo, ARTIFACTS_XML, XML_MIME_TYPE, ASSET_KIND_ARTIFACTS_METADATA);
    testPath(siteRepo, ARTIFACTS_XML_XZ, XZ_MIME_TYPE, ASSET_KIND_ARTIFACTS_METADATA);

    // test non-root
    testPath(compositeSiteRepo, childSitePath(PATH_CHILD_SITE, ARTIFACTS_JAR),
        JAR_MIME_TYPE,
        ASSET_KIND_ARTIFACTS_METADATA);
    testPath(compositeSiteRepo, childSitePath(PATH_CHILD_SITE, ARTIFACTS_XML),
        XML_MIME_TYPE,
        ASSET_KIND_ARTIFACTS_METADATA);
    testPath(compositeSiteRepo, childSitePath(PATH_CHILD_SITE, ARTIFACTS_XML_XZ),
        XZ_MIME_TYPE,
        ASSET_KIND_ARTIFACTS_METADATA);
  }

  @Test
  public void testContent() throws Exception {
    testPath(siteRepo, CONTENT_JAR, JAR_MIME_TYPE, ASSET_KIND_CONTENT_METADATA);
    testPath(siteRepo, CONTENT_XML, XML_MIME_TYPE, ASSET_KIND_CONTENT_METADATA);
    testPath(siteRepo, CONTENT_XML_XZ, XZ_MIME_TYPE, ASSET_KIND_CONTENT_METADATA);

    // test non-root
    assertOk(compositeSiteClient, COMPOSITE_CONTENT_XML); // prime
    testPath(compositeSiteRepo, childSitePath(PATH_CHILD_SITE, CONTENT_JAR), JAR_MIME_TYPE,
        ASSET_KIND_CONTENT_METADATA);
    testPath(compositeSiteRepo, childSitePath(PATH_CHILD_SITE, CONTENT_XML), XML_MIME_TYPE,
        ASSET_KIND_CONTENT_METADATA);
    testPath(compositeSiteRepo, childSitePath(PATH_CHILD_SITE, CONTENT_XML_XZ), XZ_MIME_TYPE,
        ASSET_KIND_CONTENT_METADATA);

    // Uses cached assets when remote offline
    server.stop();
    testPath(siteRepo, CONTENT_JAR, JAR_MIME_TYPE, ASSET_KIND_CONTENT_METADATA);
    testPath(siteRepo, CONTENT_XML, XML_MIME_TYPE, ASSET_KIND_CONTENT_METADATA);
    testPath(siteRepo, CONTENT_XML_XZ, XZ_MIME_TYPE, ASSET_KIND_CONTENT_METADATA);

    // test non-root
    testPath(compositeSiteRepo, childSitePath(PATH_CHILD_SITE, CONTENT_JAR), JAR_MIME_TYPE,
        ASSET_KIND_CONTENT_METADATA);
    testPath(compositeSiteRepo, childSitePath(PATH_CHILD_SITE, CONTENT_XML), XML_MIME_TYPE,
        ASSET_KIND_CONTENT_METADATA);
    testPath(compositeSiteRepo, childSitePath(PATH_CHILD_SITE, CONTENT_XML_XZ), XZ_MIME_TYPE,
        ASSET_KIND_CONTENT_METADATA);
  }

  @Test
  public void testCompositeArtifacts() throws Exception {
    testPath(compositeSiteRepo, COMPOSITE_ARTIFACTS_XML, XML_MIME_TYPE, ASSET_KIND_COMPOSITE_ARTIFACTS);
    testPath(compositeSiteRepo, COMPOSITE_ARTIFACTS_JAR, JAR_MIME_TYPE, ASSET_KIND_COMPOSITE_ARTIFACTS);

    // test non-root (already primed)
    testPath(compositeSiteRepo, childSitePath(PATH_COMPOSITE_CHILD, COMPOSITE_ARTIFACTS_JAR), JAR_MIME_TYPE,
        ASSET_KIND_COMPOSITE_ARTIFACTS);
    testPath(compositeSiteRepo, childSitePath(PATH_COMPOSITE_CHILD, COMPOSITE_ARTIFACTS_XML), XML_MIME_TYPE,
        ASSET_KIND_COMPOSITE_ARTIFACTS);

    // Uses cached assets when remote offline
    server.stop();
    testPath(compositeSiteRepo, COMPOSITE_ARTIFACTS_JAR, JAR_MIME_TYPE, ASSET_KIND_COMPOSITE_ARTIFACTS);
    testPath(compositeSiteRepo, COMPOSITE_ARTIFACTS_XML, XML_MIME_TYPE, ASSET_KIND_COMPOSITE_ARTIFACTS);

    // test non-root (already primed)
    testPath(compositeSiteRepo, childSitePath(PATH_COMPOSITE_CHILD, COMPOSITE_ARTIFACTS_JAR), JAR_MIME_TYPE,
        ASSET_KIND_COMPOSITE_ARTIFACTS);
    testPath(compositeSiteRepo, childSitePath(PATH_COMPOSITE_CHILD, COMPOSITE_ARTIFACTS_XML), XML_MIME_TYPE,
        ASSET_KIND_COMPOSITE_ARTIFACTS);
  }

  @Test
  public void testCompositeContent() throws Exception {
    testPath(compositeSiteRepo, COMPOSITE_CONTENT_JAR, JAR_MIME_TYPE, ASSET_KIND_COMPOSITE_CONTENT);
    testPath(compositeSiteRepo, COMPOSITE_CONTENT_XML, XML_MIME_TYPE, ASSET_KIND_COMPOSITE_CONTENT);

    // test non-root (already primed)
    testPath(compositeSiteRepo, childSitePath(PATH_COMPOSITE_CHILD, COMPOSITE_CONTENT_JAR), JAR_MIME_TYPE,
        ASSET_KIND_COMPOSITE_CONTENT);
    testPath(compositeSiteRepo, childSitePath(PATH_COMPOSITE_CHILD, COMPOSITE_CONTENT_XML), XML_MIME_TYPE,
        ASSET_KIND_COMPOSITE_CONTENT);

    // Uses cached assets when remote offline
    server.stop();
    testPath(compositeSiteRepo, COMPOSITE_CONTENT_JAR, JAR_MIME_TYPE, ASSET_KIND_COMPOSITE_CONTENT);
    testPath(compositeSiteRepo, COMPOSITE_CONTENT_XML, XML_MIME_TYPE, ASSET_KIND_COMPOSITE_CONTENT);

    // test non-root (already primed)
    testPath(compositeSiteRepo, childSitePath(PATH_COMPOSITE_CHILD, COMPOSITE_CONTENT_JAR), JAR_MIME_TYPE,
        ASSET_KIND_COMPOSITE_CONTENT);
    testPath(compositeSiteRepo, childSitePath(PATH_COMPOSITE_CHILD, COMPOSITE_CONTENT_XML), XML_MIME_TYPE,
        ASSET_KIND_COMPOSITE_CONTENT);
  }

  @Test
  public void testP2Index() throws Exception {
    testPath(compositeSiteRepo, P2_INDEX, TEXT_MIME_TYPE, ASSET_KIND_P2_INDEX);

    // test non-root
    assertOk(compositeSiteClient, COMPOSITE_CONTENT_XML); // prime
    testPath(compositeSiteRepo, childSitePath(PATH_CHILD_SITE, P2_INDEX), TEXT_MIME_TYPE, ASSET_KIND_P2_INDEX);

    // Uses cached assets when remote offline
    server.stop();
    testPath(compositeSiteRepo, P2_INDEX, TEXT_MIME_TYPE, ASSET_KIND_P2_INDEX);

    // test non-root
    testPath(compositeSiteRepo, childSitePath(PATH_CHILD_SITE, P2_INDEX), TEXT_MIME_TYPE, ASSET_KIND_P2_INDEX);
  }

  @Test
  public void testBinary() throws Exception {
    testPath(siteRepo, BINARY_PATH, null, ASSET_KIND_BINARY_BUNDLE);

    // test non-root
    assertOk(compositeSiteClient, COMPOSITE_CONTENT_XML); // prime
    testPath(compositeSiteRepo, childSitePath(PATH_CHILD_SITE, BINARY_PATH), null, ASSET_KIND_BINARY_BUNDLE);

    // Uses cached assets when remote offline
    server.stop();
    testPath(siteRepo, BINARY_PATH, null, ASSET_KIND_BINARY_BUNDLE);

    // test non-root
    testPath(compositeSiteRepo, childSitePath(PATH_CHILD_SITE, BINARY_PATH), null, ASSET_KIND_BINARY_BUNDLE);
  }

  @Test
  public void testFeatures() throws Exception {
    String identifier = "com.sonatype.nexus.feature";
    String version = "1.2.0.v20170518-1049";

    testPath(siteRepo, FEATURE_PATH, JAR_MIME_TYPE, ASSET_KIND_BUNDLE, identifier, version);

    // test non-root
    assertOk(compositeSiteClient, COMPOSITE_CONTENT_XML); // prime
    testPath(compositeSiteRepo, childSitePath(PATH_CHILD_SITE, FEATURE_PATH), JAR_MIME_TYPE, ASSET_KIND_BUNDLE,
        identifier, version);

    // Uses cached assets when remote offline
    server.stop();
    testPath(siteRepo, FEATURE_PATH, JAR_MIME_TYPE, ASSET_KIND_BUNDLE, identifier, version);

    // test non-root
    testPath(compositeSiteRepo, childSitePath(PATH_CHILD_SITE, FEATURE_PATH), JAR_MIME_TYPE, ASSET_KIND_BUNDLE,
        identifier, version);
  }

  @Test
  public void testPlugin() throws Exception {
    String identifier = "com.sonatype.nexus";
    String version = "1.2.0.v20170518-1049";

    testPath(siteRepo, PATH_PLUGIN, GZIP_MIME_TYPE, ASSET_KIND_BUNDLE, identifier, version);

    // test non-root
    assertOk(compositeSiteClient, COMPOSITE_CONTENT_XML); // prime
    testPath(compositeSiteRepo, childSitePath(PATH_CHILD_SITE, PATH_PLUGIN), GZIP_MIME_TYPE, ASSET_KIND_BUNDLE,
        identifier, version);

    // Uses cached assets when remote offline
    server.stop();
    testPath(siteRepo, PATH_PLUGIN, GZIP_MIME_TYPE, ASSET_KIND_BUNDLE, identifier, version);

    // test non-root
    testPath(compositeSiteRepo, childSitePath(PATH_CHILD_SITE, PATH_PLUGIN), GZIP_MIME_TYPE,
        ASSET_KIND_BUNDLE,
        identifier, version);
  }

  @Test
  public void testMissingPaths() throws Exception {
    assertMissing(compositeSiteClient, CONTENT_XML);
    assertMissing(compositeSiteClient, CONTENT_JAR);
    assertMissing(compositeSiteClient, CONTENT_XML_XZ);

    assertMissing(compositeSiteClient, ARTIFACTS_XML_XZ);
    assertMissing(compositeSiteClient, ARTIFACTS_XML_XZ);
    assertMissing(compositeSiteClient, ARTIFACTS_XML_XZ);

    assertMissing(compositeSiteClient, BINARY_PATH);
    assertMissing(compositeSiteClient, FEATURE_PATH);
    assertMissing(compositeSiteClient, PATH_PLUGIN);

    P2Client childClient = p2Client(siteRepo);
    assertMissing(childClient, COMPOSITE_CONTENT_XML);
    assertMissing(childClient, COMPOSITE_CONTENT_JAR);

    assertMissing(childClient, COMPOSITE_ARTIFACTS_XML);
    assertMissing(childClient, COMPOSITE_ARTIFACTS_JAR);
  }

  @Test
  public void testChildSite() throws Exception {
    // Test child site when repo is not primed
    assertMissing(compositeSiteClient, childSitePath("child", CONTENT_XML));

    String[] expectedHashes = new String[]{childSiteHash("child"), childSiteHash("composite-child")};

    assertThat(retrieveChildPaths(compositeSiteClient, COMPOSITE_ARTIFACTS_JAR), arrayContaining(expectedHashes));
    assertThat(retrieveChildPaths(compositeSiteClient, COMPOSITE_ARTIFACTS_XML), arrayContaining(expectedHashes));
    assertThat(retrieveChildPaths(compositeSiteClient, COMPOSITE_CONTENT_JAR), arrayContaining(expectedHashes));
    assertThat(retrieveChildPaths(compositeSiteClient, COMPOSITE_CONTENT_XML), arrayContaining(expectedHashes));

    expectedHashes = new String[]{"../" + childSiteHash("relative-child")};
    assertThat(retrieveChildPaths(compositeSiteClient, childSitePath("composite-child", COMPOSITE_ARTIFACTS_JAR)),
        arrayContaining(expectedHashes));
    assertThat(retrieveChildPaths(compositeSiteClient, childSitePath("composite-child", COMPOSITE_ARTIFACTS_XML)),
        arrayContaining(expectedHashes));
    assertThat(retrieveChildPaths(compositeSiteClient, childSitePath("composite-child", COMPOSITE_CONTENT_JAR)),
        arrayContaining(expectedHashes));
    assertThat(retrieveChildPaths(compositeSiteClient, childSitePath("composite-child", COMPOSITE_CONTENT_XML)),
        arrayContaining(expectedHashes));

    // Test child site when repo is primed
    assertMissing(compositeSiteClient, childSitePath("unknown-site", CONTENT_XML));
  }

  @Test
  public void testArtifacts_mirrorRemoved() throws Exception {
    P2Client client = p2Client(siteRepo);

    for (String path : new String[]{ARTIFACTS_XML, ARTIFACT_JAR, ARTIFACT_XML_XZ}) {
      try (CloseableHttpResponse response = client.get(path)) {
        assertThat(status(response), is(HttpStatus.OK));
        String metadata = IOUtils.toString(openMetadata(response.getEntity()));
        assertThat("Links mirrors: " + path, metadata, not(containsString("p2.mirrorsURL")));
      }
    }
  }

  @Test
  public void checkComponentRemovedWhenAssetRemoved() throws Exception {
    assertComponentCleanedUp(siteRepo, BINARY_PATH, "org.eclipse.platform.ide.executable.gtk.linux.x86_64");
    assertComponentCleanedUp(siteRepo, FEATURE_PATH, "com.sonatype.nexus.feature");
    assertComponentCleanedUp(siteRepo, PATH_PLUGIN, "com.sonatype.nexus");
  }

  private void assertComponentCleanedUp(
      final Repository repository,
      final String path,
      final String identifier) throws Exception
  {
    P2Client client = p2Client(repository);
    assertOk(client, path);
    Asset asset = findAsset(repository, path);

    assertNotNull(asset);
    assertTrue(componentAssetTestHelper.assertComponentExists(repository, identifier));

    ComponentMaintenance maintenanceFacet = repository.facet(ComponentMaintenance.class);

    maintenanceFacet.deleteAsset(EntityHelper.id(asset));

    assertNull(findAsset(repository, path));
    assertNull(findComponent(repository, identifier));
  }

  private static void assertOk(final P2Client client, final String path) throws IOException {
    try (CloseableHttpResponse response = client.get(path)) {
      assertThat(status(response), is(HttpStatus.OK));
    }
  }

  private static void assertMissing(final P2Client client, final String path) throws IOException {
    try (CloseableHttpResponse response = client.get(path)) {
      assertThat(status(response), is(HttpStatus.NOT_FOUND));
    }
  }

  private static String[] retrieveChildPaths(final P2Client client, final String path) throws Exception {
    try (CloseableHttpResponse response = client.get(path)) {
      assertThat(status(response), is(HttpStatus.OK));

      HttpEntity entity = response.getEntity();
      try (InputStream in = openMetadata(entity)) {
        String metadata = IOUtils.toString(in);

        List<String> children = new ArrayList<>();
        Matcher matcher = Pattern.compile("location=\"(.*)\\/\"").matcher(metadata);
        while (matcher.find()) {
          children.add(matcher.group(1));
        }
        return children.toArray(new String[children.size()]);
      }
      finally {
        EntityUtils.consumeQuietly(entity);
      }
    }
  }

  private static InputStream openMetadata(final HttpEntity entity) throws Exception {
    String contentType = entity.getContentType().getValue();
    if (contentType.contains(XML_MIME_TYPE)) {
      return entity.getContent();
    }
    else if (contentType.contains(XZ_MIME_TYPE)) {
      return new XZInputStream(entity.getContent());
    }
    else if (contentType.contains(JAR_MIME_TYPE)) {
      ZipInputStream in = new ZipInputStream(entity.getContent());
      in.getNextEntry();
      return in;
    }
    fail("Unexpected mimetype " + contentType);
    return null;
  }

  private Asset testPath(
      final Repository repository,
      final String path,
      final String mimeType,
      final String assetKind) throws Exception
  {
    P2Client client = p2Client(repository);

    assertOk(client, path);

    Asset asset = findAsset(repository, path);
    assertThat(asset.name(), is(equalTo(path)));
    assertThat(asset.format(), is(equalTo(FORMAT_NAME)));
    if (mimeType != null) {
      assertThat(asset.contentType(), is(equalTo(mimeType)));
    }
    assertThat(asset.formatAttributes().get("asset_kind", String.class), is(assetKind));

    return asset;
  }

  private void testPath(
      final Repository repository,
      final String path,
      final String mimeType,
      final String assetKind,
      final String identifier,
      final String version) throws Exception
  {
    testPath(repository, path, mimeType, assetKind);

    componentAssetTestHelper.assertComponentExists(repository, identifier, version);
  }

  @After
  public void tearDown() throws Exception {
    server.stop();
  }
}
