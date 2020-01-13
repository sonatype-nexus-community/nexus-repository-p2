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
package org.sonatype.nexus.blobstore.restore.p2.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.Properties;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.blobstore.api.Blob;
import org.sonatype.nexus.blobstore.api.BlobStore;
import org.sonatype.nexus.blobstore.api.BlobStoreManager;
import org.sonatype.nexus.blobstore.restore.RestoreBlobData;
import org.sonatype.nexus.common.log.DryRunPrefix;
import org.sonatype.nexus.common.node.NodeAccess;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.manager.RepositoryManager;
import org.sonatype.nexus.repository.p2.P2RestoreFacet;
import org.sonatype.nexus.repository.storage.AssetBlob;
import org.sonatype.nexus.repository.storage.StorageFacet;
import org.sonatype.nexus.repository.storage.StorageTx;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA1;

/**
 * @since 0.next
 */
public class P2RestoreBlobStrategyTest extends TestSupport
{
  private static final String TEST_BLOB_STORE_NAME = "test";

  private static final String PACKAGE_PATH = "https/download.eclipse.org/releases/2019-12/201912181000/plugins/org.eclipse.core.resources_3.13.600.v20191122-2104.jar";

  @Mock
  RepositoryManager repositoryManager;

  @Mock
  NodeAccess nodeAccess;

  @Mock
  BlobStoreManager blobStoreManager;

  @Mock
  Repository repository;

  @Mock
  StorageFacet storageFacet;

  @Mock
  P2RestoreFacet p2RestoreFacet;

  @Mock
  P2RestoreBlobData p2RestoreBlobData;

  @Mock
  private RestoreBlobData restoreBlobData;

  @Mock
  Blob blob;

  @Mock
  BlobStore blobStore;

  @Mock
  StorageTx storageTx;

  private byte[] blobBytes = "blobbytes".getBytes();

  private Properties properties = new Properties();

  private P2RestoreBlobStrategy restoreBlobStrategy;

  @Before
  public void setup() {
    restoreBlobStrategy = new P2RestoreBlobStrategy(nodeAccess, repositoryManager, blobStoreManager, new DryRunPrefix("dryrun"));

    when(repositoryManager.get(anyString())).thenReturn(repository);
    when(repository.facet(P2RestoreFacet.class)).thenReturn(p2RestoreFacet);
    when(repository.optionalFacet(P2RestoreFacet.class)).thenReturn(Optional.of(p2RestoreFacet));
    when(repository.optionalFacet(StorageFacet.class)).thenReturn(Optional.of(storageFacet));
    when(blob.getInputStream()).thenReturn(new ByteArrayInputStream(blobBytes));
    when(p2RestoreBlobData.getBlobData()).thenReturn(restoreBlobData);
    when(restoreBlobData.getBlobName()).thenReturn(PACKAGE_PATH);
    when(restoreBlobData.getRepository()).thenReturn(repository);
    when(restoreBlobData.getBlob()).thenReturn(blob);
    when(storageFacet.txSupplier()).thenReturn(() -> storageTx);
    when(blobStoreManager.get(TEST_BLOB_STORE_NAME)).thenReturn(blobStore);
    when(restoreBlobData.getRepository()).thenReturn(repository);

    properties.setProperty("@BlobStore.created-by", "anonymous");
    properties.setProperty("size", "894185");
    properties.setProperty("@Bucket.repo-name", "p2-proxy");
    properties.setProperty("creationTime", "1577179311620");
    properties.setProperty("@BlobStore.created-by-ip", "127.0.0.1");
    properties.setProperty("@BlobStore.content-type", "application/java-archive");
    properties.setProperty("@BlobStore.blob-name", PACKAGE_PATH);
    properties.setProperty("sha1", "ac7306bee8742701a1e81a702685a55c17b07e4a");
  }

  @Test
  public void testBlobDataIsCreated() {
    assertThat(restoreBlobStrategy.createRestoreData(restoreBlobData).getBlobData(), is(restoreBlobData));
  }

  @Test(expected = IllegalStateException.class)
  public void testIfBlobDataNameIsEmptyExceptionIsThrown() {
    when(p2RestoreBlobData.getBlobData().getBlobName()).thenReturn("");
    restoreBlobStrategy.createRestoreData(restoreBlobData);
  }

  @Test
  public void testCorrectHashAlgorithmsAreSupported() {
    assertThat(restoreBlobStrategy.getHashAlgorithms(), containsInAnyOrder(SHA1));
  }

  @Test
  public void testAppropriatePathIsReturned() {
    assertThat(restoreBlobStrategy.getAssetPath(p2RestoreBlobData), is(PACKAGE_PATH));
  }

  @Test
  public void testPackageIsRestored() throws IOException {
    restoreBlobStrategy.restore(properties, blob, TEST_BLOB_STORE_NAME, false);
    verify(p2RestoreFacet).assetExists(PACKAGE_PATH);
    verify(p2RestoreFacet).restore(any(AssetBlob.class), eq(PACKAGE_PATH));
    verifyNoMoreInteractions(p2RestoreFacet);
  }

  @Test
  public void testRestoreIsSkipIfPackageExists() {
    when(p2RestoreFacet.assetExists(PACKAGE_PATH)).thenReturn(true);
    restoreBlobStrategy.restore(properties, blob, TEST_BLOB_STORE_NAME, false);

    verify(p2RestoreFacet).assetExists(PACKAGE_PATH);
    verify(p2RestoreFacet).componentRequired(PACKAGE_PATH);
    verifyNoMoreInteractions(p2RestoreFacet);
  }

  @Test
  public void testComponentIsRequiredForGz() {
    boolean expected = true;
    when(p2RestoreFacet.componentRequired(PACKAGE_PATH)).thenReturn(expected);
    assertThat(restoreBlobStrategy.componentRequired(p2RestoreBlobData), is(expected));
    verify(p2RestoreFacet).componentRequired(PACKAGE_PATH);
    verifyNoMoreInteractions(p2RestoreFacet);
  }

  @Test
  public void testComponentQuery() throws IOException
  {
    restoreBlobStrategy.getComponentQuery(p2RestoreBlobData);
    verify(p2RestoreFacet, times(1)).getComponentQuery(any(), any(), any());
  }
}
