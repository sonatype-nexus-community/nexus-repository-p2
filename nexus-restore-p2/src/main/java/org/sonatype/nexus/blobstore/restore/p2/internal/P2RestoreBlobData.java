package org.sonatype.nexus.blobstore.restore.p2.internal;

import org.sonatype.nexus.blobstore.restore.RestoreBlobData;
import org.sonatype.nexus.blobstore.restore.RestoreBlobDataSupport;

/**
 * @since 0.next
 */
public class P2RestoreBlobData
    extends RestoreBlobDataSupport
{
  P2RestoreBlobData(final RestoreBlobData blobData) {
    super(blobData);
  }
}
