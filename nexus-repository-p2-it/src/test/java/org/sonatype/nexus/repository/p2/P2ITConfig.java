package org.sonatype.nexus.repository.p2;

import org.sonatype.nexus.pax.exam.NexusPaxExamSupport;
import org.sonatype.nexus.testsuite.testsupport.NexusITSupport;

import org.ops4j.pax.exam.Option;

import static org.ops4j.pax.exam.CoreOptions.systemProperty;
import static org.sonatype.nexus.pax.exam.NexusPaxExamSupport.nexusFeature;

public class P2ITConfig
{
  public static Option[] configureP2Base() {
    return NexusPaxExamSupport.options(
        NexusITSupport.configureNexusBase(),
        nexusFeature("org.sonatype.nexus.plugins", "nexus-repository-p2"),
        systemProperty("nexus-exclude-features").value("nexus-cma-community")
    );
  }
}
