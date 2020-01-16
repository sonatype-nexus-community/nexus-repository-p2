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
package org.sonatype.nexus.repository.p2.upgrade;

import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.sonatype.nexus.common.upgrade.DependsOn;
import org.sonatype.nexus.common.upgrade.Upgrades;
import org.sonatype.nexus.orient.DatabaseInstance;
import org.sonatype.nexus.orient.DatabaseInstanceNames;
import org.sonatype.nexus.orient.DatabaseUpgradeSupport;
import org.sonatype.nexus.orient.OIndexNameBuilder;

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.db.record.OIdentifiable;
import com.orientechnologies.orient.core.index.OIndex;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.OCommandSQL;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Upgrade step to set {@code name} for p2 assets.
 *
 * @since 0.next
 */
@Named
@Singleton
@Upgrades(model = P2Model.NAME, from = "1.0", to = "1.1")
@DependsOn(model = DatabaseInstanceNames.COMPONENT, version = "1.0", checkpoint = true)
@DependsOn(model = DatabaseInstanceNames.CONFIG, version = "1.0")
public class P2Upgrade_1_1
    extends DatabaseUpgradeSupport
{
  private static final String SELECT_P2_REPOSITORIES =
      "select from repository where recipe_name in ['p2-proxy']";

  private static final String REMOVE_UNNECESSARY_SLASH_FROM_ASSET_NAME =
      "update asset set name = name.subString(0, 1).replace('/', '').append(name.subString(1, name.length())) where bucket=?";

  private static final String P_REPOSITORY_NAME = "repository_name";

  private static final String I_REPOSITORY_NAME = new OIndexNameBuilder()
      .type("bucket")
      .property(P_REPOSITORY_NAME)
      .build();

  private final Provider<DatabaseInstance> configDatabaseInstance;

  private final Provider<DatabaseInstance> componentDatabaseInstance;

  @Inject
  public P2Upgrade_1_1(
      @Named(DatabaseInstanceNames.CONFIG) final Provider<DatabaseInstance> configDatabaseInstance,
      @Named(DatabaseInstanceNames.COMPONENT) final Provider<DatabaseInstance> componentDatabaseInstance)
  {
    this.configDatabaseInstance = checkNotNull(configDatabaseInstance);
    this.componentDatabaseInstance = checkNotNull(componentDatabaseInstance);
  }

  @Override
  public void apply() {
    if (hasSchemaClass(configDatabaseInstance, "repository") &&
        hasSchemaClass(componentDatabaseInstance, "asset")) {
      updateP2AssetNames();
    }
  }

  private void updateP2AssetNames() {
    List<String> p2RepositoryNames;
    try (ODatabaseDocumentTx db = configDatabaseInstance.get().connect()) {
      p2RepositoryNames = db.<List<ODocument>>query(new OSQLSynchQuery<ODocument>(SELECT_P2_REPOSITORIES)).stream()
          .map(d -> (String) d.field(P_REPOSITORY_NAME))
          .collect(Collectors.toList());
    }
    if (!p2RepositoryNames.isEmpty()) {
      OCommandSQL updateAssetCommand = new OCommandSQL(REMOVE_UNNECESSARY_SLASH_FROM_ASSET_NAME);
      try (ODatabaseDocumentTx db = componentDatabaseInstance.get().connect()) {
        OIndex<?> bucketIdx = db.getMetadata().getIndexManager().getIndex(I_REPOSITORY_NAME);
        p2RepositoryNames.forEach(repositoryName -> {
          OIdentifiable bucket = (OIdentifiable) bucketIdx.get(repositoryName);
          if (bucket == null) {
            log.warn("Unable to find bucket for {}", repositoryName);
          }
          else {
            int updates = db.command(updateAssetCommand).execute(bucket.getIdentity());
            if (updates > 0) {
              log.info("Updated {} p2 asset(s) names in repository {}: ", updates, repositoryName);
            }
          }
        });
      }
    }
  }
}
