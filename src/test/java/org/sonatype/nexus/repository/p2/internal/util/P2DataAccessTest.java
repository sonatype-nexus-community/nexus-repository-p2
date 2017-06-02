/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */

package org.sonatype.nexus.repository.p2.internal.util;

import java.util.List;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.storage.Asset;
import org.sonatype.nexus.repository.storage.Bucket;
import org.sonatype.nexus.repository.storage.Component;
import org.sonatype.nexus.repository.storage.StorageTx;

import com.google.common.collect.ImmutableList;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA1;

/**
 * Test for {@link P2DataAccess}
 */
public class P2DataAccessTest
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

  P2DataAccess underTest;

  @Before
  public void setUp() throws Exception {
    underTest = new P2DataAccess();
  }

  @Test
  public void immutableListIsSha1() {
    assertThat(P2DataAccess.HASH_ALGORITHMS, is(equalTo(ImmutableList.of(SHA1))));
  }

  @Test
  public void findComponent() throws Exception {
    List<Component> list = ImmutableList.of(component);
    when(tx.findComponents(any(), any()))
        .thenReturn(list);

    assertThat(underTest.findComponent(tx, repository, "test", "test"), is(equalTo(component)));
  }

  @Test
  public void findAsset() throws Exception {
    when(tx.findAssetWithProperty(any(), any(), any(Bucket.class)))
        .thenReturn(asset);

    assertThat(underTest.findAsset(tx, bucket, assetName), is(equalTo(asset)));
  }

  @Test
  public void saveAsset() throws Exception {
  }

  @Test
  public void saveAsset1() throws Exception {
  }

  @Test
  public void toContent() throws Exception {
  }
}
