package org.sonatype.nexus.repository.p2;

import java.io.IOException;

import org.sonatype.nexus.blobstore.api.Blob;
import org.sonatype.nexus.repository.Facet;
import org.sonatype.nexus.repository.storage.AssetBlob;
import org.sonatype.nexus.repository.storage.Query;

/**
 * @since 0.next
 */
@Facet.Exposed
public interface P2RestoreFacet
    extends Facet
{
  void restore(final AssetBlob assetBlob, final String path) throws IOException;

  boolean assetExists(final String path);

  Query getComponentQuery(final Blob blob, final String blobName, final String blobStoreName) throws IOException;

  boolean componentRequired(final String name);
}
