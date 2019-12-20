package org.sonatype.nexus.blobstore.restore.p2.internal;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import com.google.common.collect.ImmutableList;
import org.sonatype.nexus.blobstore.api.BlobStoreManager;
import org.sonatype.nexus.blobstore.restore.BaseRestoreBlobStrategy;
import org.sonatype.nexus.blobstore.restore.RestoreBlobData;
import org.sonatype.nexus.common.hash.HashAlgorithm;
import org.sonatype.nexus.common.log.DryRunPrefix;
import org.sonatype.nexus.common.node.NodeAccess;
import org.sonatype.nexus.repository.Repository;
import org.sonatype.nexus.repository.manager.RepositoryManager;
import org.sonatype.nexus.repository.p2.P2RestoreFacet;
import org.sonatype.nexus.repository.p2.internal.P2Format;
import org.sonatype.nexus.repository.storage.AssetBlob;
import org.sonatype.nexus.repository.storage.Query;

import static com.google.common.base.Preconditions.checkState;
import static org.eclipse.aether.util.StringUtils.isEmpty;
import static org.sonatype.nexus.common.hash.HashAlgorithm.SHA1;

/**
 * @since 0.next
 */
@Named(P2Format.NAME)
@Singleton
public class P2RestoreBlobStrategy
    extends BaseRestoreBlobStrategy<P2RestoreBlobData>
{
  @Inject
  public P2RestoreBlobStrategy(final NodeAccess nodeAccess,
                               final RepositoryManager repositoryManager,
                               final BlobStoreManager blobStoreManager,
                               final DryRunPrefix dryRunPrefix)
  {
    super(nodeAccess, repositoryManager, blobStoreManager, dryRunPrefix);
  }

  @Override
  protected P2RestoreBlobData createRestoreData(final RestoreBlobData restoreBlobData) {
    checkState(!isEmpty(restoreBlobData.getBlobName()), "Blob name cannot be empty");

    return new P2RestoreBlobData(restoreBlobData);
  }

  @Override
  protected boolean canAttemptRestore(@Nonnull final P2RestoreBlobData p2RestoreBlobData) {
    Repository repository = getRepository(p2RestoreBlobData);
    Optional<P2RestoreFacet> p2RestoreFacetFacet = repository.optionalFacet(P2RestoreFacet.class);

    if (!p2RestoreFacetFacet.isPresent()) {
      log.warn("Skipping as R Restore Facet not found on repository: {}", repository.getName());
      return false;
    }
    return true;
  }

  @Override
  protected String getAssetPath(
      @Nonnull final P2RestoreBlobData p2RestoreBlobData)
  {
    return p2RestoreBlobData.getBlobData().getBlobName();
  }

  @Override
  protected boolean assetExists(@Nonnull final P2RestoreBlobData p2RestoreBlobData) throws IOException {
    P2RestoreFacet facet = getRestoreFacet(p2RestoreBlobData);
    return facet.assetExists(getAssetPath(p2RestoreBlobData));
  }

  @Nonnull
  @Override
  protected List<HashAlgorithm> getHashAlgorithms() {
    return ImmutableList.of(SHA1);
  }

  @Override
  protected boolean componentRequired(final P2RestoreBlobData data) {
    P2RestoreFacet facet = getRestoreFacet(data);
    final String path = data.getBlobData().getBlobName();

    return facet.componentRequired(path);
  }

  @Override
    protected Query getComponentQuery(final P2RestoreBlobData data) throws IOException {
    P2RestoreFacet facet = getRestoreFacet(data);
    RestoreBlobData blobData = data.getBlobData();
    Map<String, String> attributes = new HashMap<>();
    try (InputStream inputStream = blobData.getBlob().getInputStream()) {
      //attributes = facet.extractComponentAttributesFromArchive(blobData.getBlobName(), inputStream);
    }

    return facet.getComponentQuery(attributes);
  }

  @Override
  protected Repository getRepository(@Nonnull final P2RestoreBlobData data) {
    return data.getBlobData().getRepository();
  }

  @Override
  protected void createAssetFromBlob(@Nonnull final AssetBlob assetBlob,
                                     @Nonnull final
                                     P2RestoreBlobData p2RestoreBlobData)
      throws IOException
  {
    P2RestoreFacet facet = getRestoreFacet(p2RestoreBlobData);
    final String path = getAssetPath(p2RestoreBlobData);

    facet.restore(assetBlob, path);
  }

  private P2RestoreFacet getRestoreFacet(@Nonnull final P2RestoreBlobData p2RestoreBlobData) {
    final Repository repository = getRepository(p2RestoreBlobData);
    return repository.facet(P2RestoreFacet.class);
  }
}
