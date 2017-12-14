<!--

    Sonatype Nexus (TM) Open Source Version
    Copyright (c) 2017-present Sonatype, Inc.
    All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.

    This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
    which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.

    Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
    of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
    Eclipse Foundation. All other trademarks are the property of their respective owners.

-->
## P2 Repositories

### Introduction

[P2](http://www.eclipse.org/equinox/p2/) is a package management format used for Eclipse IDE packages. Although p2 has 
specific support for installing Eclipse and Equinox-based applications, it includes a general-purpose provisioning 
infrastructure that can be used as the basis for provisioning solutions for a wide variety of software applications.

This allows the repository manager to take advantage of the packages in the official Eclipse repositories and other
public P2 repositories without incurring repeated downloads of packages.

### Proxying P2 Repositories

Functionality in this early version of the P2 Proxy has been limited to proxying just specific P2 proxy locations, and 
not composite repositories. If you'd like to see this functionality, get involved and contribute with us :)

You can set up an P2 proxy repository to access a remote repository location, for example to proxy the Eclipse Oxygen
repository at [http://download.eclipse.org/eclipse/updates/4.8milestones/](http://download.eclipse.org/eclipse/updates/4.8milestones/)

To proxy a P2 repository, you simply create a new 'p2 (proxy)' as documented in 
[Repository Management](https://help.sonatype.com/display/NXRM3/Configuration#Configuration-RepositoryManagement) in
details. Minimal configuration steps are:

- Define 'Name'
- Define URL for 'Remote storage' e.g. [http://download.eclipse.org/eclipse/updates/4.8milestones/S-4.8M1-201708022000/](http://download.eclipse.org/eclipse/updates/4.8milestones/S-4.8M1-201708022000/)
- Select a 'Blob store' for 'Storage'

### Configuring P2 in Eclipse

To configure P2 to use Nexus Repository as a Proxy for remote P2 sites, there are full docs [available here](http://help.eclipse.org/oxygen/index.jsp?topic=/org.eclipse.platform.doc.user/tasks/tasks-127.htm) from Eclipse.

To help you out, here's how to set up a site using Eclipse Oxygen on Mac OS X:

- Navigate to: Help > Install New Software
- Click "Add..."
- Define Name for 'Name' e.g. "p2-proxy-name"
- Define URL for 'Location' e.g. [http://nexusUrl:nexusPort/repository/p2-Proxy-Name](http://nexusUrl:nexusPort/repository/p2-Proxy-Name)

After doing this, your P2 Proxy site will be available for Eclipse to cache metadata from, as well as request remote artifacts from. 

NOTE: Eclipse includes P2 download sites by default, you will want to remove these if you intend to proxy solely through Nexus Repository.

### Browsing P2 Repository Packages

You can browse P2 repositories in the user interface inspecting the components and assets and their details, as
described in [Browsing Repositories and Repository Groups](https://help.sonatype.com/display/NXRM3/Browsing+Repositories+and+Repository+Groups).
