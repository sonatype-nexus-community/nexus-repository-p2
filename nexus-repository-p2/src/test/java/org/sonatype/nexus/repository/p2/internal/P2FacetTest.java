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

import java.io.InputStream;
import java.util.Date;
import java.util.List;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.blobstore.api.Blob;
import org.sonatype.nexus.common.collect.NestedAttributesMap;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.p2.internal.util.P2TempBlobUtils;
import org.sonatype.nexus.repository.storage.Asset;
import org.sonatype.nexus.repository.storage.AssetBlob;
import org.sonatype.nexus.repository.storage.Bucket;
import org.sonatype.nexus.repository.storage.Component;
import org.sonatype.nexus.repository.storage.StorageTx;
import org.sonatype.nexus.repository.view.Content;
import org.sonatype.nexus.repository.view.Payload;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA1;
import static org.sonatype.nexus.repository.p2.internal.P2FacetImpl.HASH_ALGORITHMS;

/**
 * Test for {@link P2TempBlobUtils}
 */
public class P2FacetTest
    extends TestSupport
{
  private final String assetName = "test";

  @Mock
  StorageTx tx;

  @Mock
  Repository repository;

  @Mock
  Component component;

  @Mock
  Asset asset;

  @Mock
  Bucket bucket;

  @Mock
  Payload payload;

  @Mock
  NestedAttributesMap nestedAttributesMap;

  @Mock
  Supplier<InputStream> supplier;

  @Mock
  Blob blob;

  @Mock
  AssetBlob assetBlob;

  P2FacetImpl underTest;

  @Rule
  public ExpectedException exceptionRule = ExpectedException.none();

  @Before
  public void setUp() throws Exception {
    underTest = new P2FacetImpl();

    when(asset.attributes()).thenReturn(nestedAttributesMap);
    when(nestedAttributesMap.child("content")).thenReturn(nestedAttributesMap);
    when(nestedAttributesMap.child("cache")).thenReturn(nestedAttributesMap);
    when(nestedAttributesMap.get("last_modified", Date.class)).thenReturn(new Date());
    when(tx.setBlob(any(), any(), any(), any(), any(), any(), anyBoolean())).thenReturn(assetBlob);
    when(assetBlob.getBlob()).thenReturn(blob);
  }

  @Test
  public void immutableListIsSha1() {
    assertThat(HASH_ALGORITHMS, is(equalTo(ImmutableList.of(SHA1))));
  }

  @Test
  public void findComponent() {
    List<Component> list = ImmutableList.of(component);
    when(tx.findComponents(any(), any()))
        .thenReturn(list);

    assertThat(underTest.findComponent(tx, repository, "test", "test"), is(equalTo(component)));
  }

  @Test
  public void findAsset() {
    when(tx.findAssetWithProperty(any(), any(), any(Bucket.class)))
        .thenReturn(asset);

    assertThat(underTest.findAsset(tx, bucket, assetName), is(equalTo(asset)));
  }

  @Test
  public void saveAsset() throws Exception {
    underTest.saveAsset(tx, asset, supplier, payload);
    verify(tx).saveAsset(asset);
  }

  @Test
  public void returnContentOnSaveAsset() throws Exception {
    assertThat(underTest.saveAsset(tx, asset, supplier, payload), is(notNullValue()));
  }

  @Test
  public void markAssetAsDownloadedOnSave() throws Exception {
    underTest.saveAsset(tx, asset, supplier, payload);
    verify(asset).markAsDownloaded();
  }

  @Test
  public void applyContentToAssetOnSave() throws Exception {
    underTest.saveAsset(tx, asset, supplier, payload);
    verify(nestedAttributesMap).set(eq("last_modified"), any());
  }

  @Test
  public void toContent() {
    Content content = underTest.toContent(asset, blob);
    assertThat(content.getAttributes().get("lastModified"), is(notNullValue()));
  }

  @Test
  public void testGetAssetKind() {
    assertThat(underTest.getAssetKind("p2.index"), is(AssetKind.P2_INDEX));
    assertThat(underTest.getAssetKind("https/download.eclipse.org/technology/epp/packages/2019-12/p2.index"),
        is(AssetKind.P2_INDEX));
    assertThat(underTest.getAssetKind(
        "https/download.eclipse.org/technology/epp/packages/2019-12/features/org.eclipse.epp.package.java.feature_4.14.0.20191212-1200.jar"),
        is(AssetKind.COMPONENT_FEATURES));
    assertThat(underTest.getAssetKind(
        "https/download.eclipse.org/technology/epp/packages/2019-12/binary/epp.package.java.executable.cocoa.macosx.x86_64_4.14.0.20191212-1200"),
        is(AssetKind.COMPONENT_BINARY));
    assertThat(underTest.getAssetKind(
        "https/download.eclipse.org/technology/epp/packages/2019-12/plugins/org.eclipse.epp.package.java_4.14.0.20191212-1200.jar.pack.gz"),
        is(AssetKind.COMPONENT_PLUGINS));
    assertThat(underTest.getAssetKind("compositeArtifacts.jar"), is(AssetKind.COMPOSITE_ARTIFACTS_JAR));
    assertThat(underTest.getAssetKind("compositeArtifacts.xml"), is(AssetKind.COMPOSITE_ARTIFACTS_XML));
    assertThat(underTest.getAssetKind("compositeContent.jar"), is(AssetKind.COMPOSITE_CONTENT_JAR));
    assertThat(underTest.getAssetKind("compositeContent.xml"), is(AssetKind.COMPOSITE_CONTENT_XML));
    assertThat(underTest.getAssetKind("https/download.eclipse.org/technology/epp/packages/2019-12/content.xml.xz"),
        is(AssetKind.CONTENT_XML_XZ));
    assertThat(underTest.getAssetKind("https/download.eclipse.org/technology/epp/packages/2019-12/content.jar"),
        is(AssetKind.CONTENT_JAR));
    assertThat(underTest.getAssetKind("https/download.eclipse.org/technology/epp/packages/2019-12/content.xml"),
        is(AssetKind.CONTENT_XML));
    assertThat(underTest.getAssetKind("content.xml.xz"), is(AssetKind.CONTENT_XML_XZ));
    assertThat(underTest.getAssetKind("content.jar"), is(AssetKind.CONTENT_JAR));
    assertThat(underTest.getAssetKind("content.xml"), is(AssetKind.CONTENT_XML));
    assertThat(underTest.getAssetKind("https/download.eclipse.org/technology/epp/packages/2019-12/artifacts.xml.xz"),
        is(AssetKind.ARTIFACT_XML_XZ));
    assertThat(underTest.getAssetKind("https/download.eclipse.org/technology/epp/packages/2019-12/artifacts.xml"),
        is(AssetKind.ARTIFACT_XML));
    assertThat(underTest.getAssetKind("https/download.eclipse.org/technology/epp/packages/2019-12/artifacts.jar"),
        is(AssetKind.ARTIFACT_JAR));
    assertThat(underTest.getAssetKind("artifacts.xml.xz"), is(AssetKind.ARTIFACT_XML_XZ));
    assertThat(underTest.getAssetKind("artifacts.xml"), is(AssetKind.ARTIFACT_XML));
    assertThat(underTest.getAssetKind("artifacts.jar"), is(AssetKind.ARTIFACT_JAR));
  }

  @Test
  public void testGetAssetKindException() {
    exceptionRule.expect(RuntimeException.class);
    exceptionRule.expectMessage("Asset path has not supported asset kind");
    underTest.getAssetKind("artifacts.rar");
  }
}
