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
# Nexus Repository P2 Format

[![Build Status](https://travis-ci.org/sonatype-nexus-community/nexus-repository-p2.svg?branch=master)](https://travis-ci.org/sonatype-nexus-community/nexus-repository-p2) [![Join the chat at https://gitter.im/sonatype/nexus-developers](https://badges.gitter.im/sonatype/nexus-developers.svg)](https://gitter.im/sonatype/nexus-developers?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

# Table Of Contents
* [Developing](#developing)
   * [Requirements](#requirements)
   * [Building](#building)
* [Using P2 with Nexus Repository Manger 3](#using-p2-with-nexus-repository-manager-3)
* [Installing the plugin](#installing-the-plugin)
   * [Temporary Install](#temporary-install)
   * [(more) Permanent Install](#more-permanent-install)
   * [(most) Permament Install](#most-permanent-install)
* [The Fine Print](#the-fine-print)
* [Getting Help](#getting-help)

## Developing

### Requirements

* [Apache Maven 3.3.3+](https://maven.apache.org/install.html)
* [Java 8+](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
* Network access to https://repository.sonatype.org/content/groups/sonatype-public-grid

Also, there is a good amount of information available at [Bundle Development](https://help.sonatype.com/display/NXRM3/Bundle+Development)

### Building

To build the project and generate the bundle use Maven

    mvn clean package

If everything checks out, the bundle for P2 should be available in the `target` folder

#### Build with Docker

 The dockerfile needs the created jar in the target folder and is using it. This can be accomplished by building the artifact first or doing it within the execution of the docker build:

    mvn clean package dockerfile:build

#### Run as a Docker container

`docker run -d -p 8081:8081 --name nexus nexus-repository-p2:1.0.0` 

For further information like how to persist volumes check out [the GitHub repo for our official image](https://github.com/sonatype/docker-nexus3).

The application will now be available from your browser at http://localhost:8081

## Using P2 With Nexus Repository Manager 3

[We have detailed instructions on how to get started here!](docs/P2_USER_DOCUMENTATION.md)

NOTE: This is an early version of P2 Proxy and does not fully support the following as of yet:

* [Composite Repositories](https://help.eclipse.org/neon/index.jsp?topic=%2Forg.eclipse.platform.doc.isv%2Fguide%2Fp2_composite_repositories.htm)
  * This is implemented but likely will fail if the composite files reference absolute paths rather than relative paths
* Old style (non-P2) update sites. The code that accomplished this can be found [here](https://github.com/sonatype/nexus-public/blob/nexus-2.x/plugins/p2/nexus-p2-repository-plugin/src/main/java/org/sonatype/nexus/plugins/p2/repository/proxy/P2ProxyRepositoryImpl.java)
  * These are fairly old, and have not been added at all

If you'd like it to support the aforementioned sites, please file an issue, or better yet, submit a PR :)

## Installing the plugin

There are a range of options for installing the P2 plugin. You'll need to build it first, and
then install the plugin with the options shown below:

### Temporary Install

Installations done via the Karaf console will be wiped out with every restart of Nexus Repository. This is a
good installation path if you are just testing or doing development on the plugin.

* Enable Nexus console: edit `<nexus_dir>/bin/nexus.vmoptions` and change `karaf.startLocalConsole`  to `true`.

  More details here: [Bundle Development](https://help.sonatype.com/display/NXRM3/Bundle+Development+Overview)

* Run Nexus' console:
  ```
  # sudo su - nexus
  $ cd <nexus_dir>/bin
  $ ./nexus run
  > bundle:install file:///tmp/nexus-repository-p2-1.0.0.jar
  > bundle:list
  ```
  (look for org.sonatype.nexus.plugins:nexus-repository-p2 ID, should be the last one)
  ```
  > bundle:start <org.sonatype.nexus.plugins:nexus-repository-p2 ID>
  ```

### (more) Permanent Install

For more permanent installs of the nexus-repository-p2 plugin, follow these instructions:

* Copy the bundle (nexus-repository-p2-1.0.0.jar) into <nexus_dir>/deploy

This will cause the plugin to be loaded with each restart of Nexus Repository. As well, this folder is monitored
by Nexus Repository and the plugin should load within 60 seconds of being copied there if Nexus Repository
is running. You will still need to start the bundle using the karaf commands mentioned in the temporary install.

### (most) Permanent Install

If you are trying to use the P2 plugin permanently, it likely makes more sense to do the following:

* Copy the bundle into `<nexus_dir>/system/org/sonatype/nexus/plugins/nexus-repository-p2/1.0.0/nexus-repository-p2-1.0.0.jar`
* Make the following additions marked with + to `<nexus_dir>/system/org/sonatype/nexus/assemblies/nexus-core-feature/3.x.y/nexus-core-feature-3.x.y-features.xml`

   ```
         <feature prerequisite="false" dependency="false">nexus-repository-rubygems</feature>
   +     <feature prerequisite="false" dependency="false">nexus-repository-p2</feature>
         <feature prerequisite="false" dependency="false">nexus-repository-gitlfs</feature>
     </feature>
   ```
   And
   ```
   + <feature name="nexus-repository-p2" description="org.sonatype.nexus.plugins:nexus-repository-p2" version="1.0.0">
   +     <details>org.sonatype.nexus.plugins:nexus-repository-p2</details>
   +     <bundle>mvn:org.sonatype.nexus.plugins/nexus-repository-p2/1.0.0</bundle>
   + </feature>
    </features>
   ```
This will cause the plugin to be loaded and started with each startup of Nexus Repository.

## The Fine Print

It is worth noting that this is **NOT SUPPORTED** by Sonatype, and is a contribution of ours
to the open source community (read: you!)

Remember:

* Use this contribution at the risk tolerance that you have
* Do NOT file Sonatype support tickets related to P2 support in regard to this plugin
* DO file issues here on GitHub, so that the community can pitch in

Phew, that was easier than I thought. Last but not least of all:

Have fun creating and using this plugin and the Nexus platform, we are glad to have you here!

## Getting help

Looking to contribute to our code but need some help? There's a few ways to get information:

* Chat with us on [Gitter](https://gitter.im/sonatype/nexus-developers)
* Check out the [Nexus3](http://stackoverflow.com/questions/tagged/nexus3) tag on Stack Overflow
* Check out the [Nexus Repository User List](https://groups.google.com/a/glists.sonatype.com/forum/?hl=en#!forum/nexus-users)
