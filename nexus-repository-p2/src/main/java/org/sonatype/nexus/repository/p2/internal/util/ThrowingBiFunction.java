package org.sonatype.nexus.repository.p2.internal.util;

@FunctionalInterface
public interface ThrowingBiFunction<T, U, R>
{
  R apply(T t, U u) throws Exception;
}