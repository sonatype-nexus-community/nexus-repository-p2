package org.sonatype.nexus.repository.p2.internal.exception;

public class InvalidMetadataException extends Exception
{
  public InvalidMetadataException() {
    super("Could not get attributes from jar");
  }
}
