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
# Nexus Repository Manger p2 Format
[![Maven Central](https://img.shields.io/maven-central/v/org.sonatype.nexus.plugins/nexus-repository-p2.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22org.sonatype.nexus.plugins%22%20AND%20a:%22nexus-repository-p2%22) [![CircleCI](https://circleci.com/gh/sonatype-nexus-community/nexus-repository-p2.svg?style=shield)](https://circleci.com/gh/sonatype-nexus-community/nexus-repository-p2) [![Join the chat at https://gitter.im/sonatype/nexus-developers](https://badges.gitter.im/sonatype/nexus-developers.svg)](https://gitter.im/sonatype/nexus-developers?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge) [![DepShield Badge](https://depshield.sonatype.org/badges/sonatype-nexus-community/nexus-repository-p2/depshield.svg)](https://depshield.github.io)

> **Huzzah!** p2 is now part of Nexus Repository Manager. Version 3.20 includes the p2 plugin by default. 
>The plugin source code is now in [nexus-public](https://github.com/sonatype/nexus-public) in [nexus-repository-p2](https://github.com/sonatype/nexus-public/tree/master/plugins/nexus-repository-p2).
> **Filing issues:** Upgrade to the latest version of Nexus Repository Manager 3, to get the latest fixes and improvements, before filing any issues or feature requests at https://issues.sonatype.org/.
> **Upgrading:** If you are using a version prior to 3.20 and upgrade to a newer version you will not be able to install the community plugin. 
>No other changes are required, and your existing data will remain intact.

# Table Of Contents
* [Release notes](https://help.sonatype.com/display/NXRM3/2019+Release+Notes)
* [Developing](#developing)
   * [Requirements](#requirements)
   * [Building](#building)
* [Using p2 with Nexus Repository Manger 3](#using-p2-with-nexus-repository-manager-3)
* [Compatibility with Nexus Repository Manager 3 Versions](#compatibility-with-nexus-repository-manager-3-versions)
* [Features Implemented In This Plugin](#features-implemented-in-this-plugin)
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

If everything checks out, the bundle for p2 should be available in the `target` folder.

#### Build with Docker

`docker build -t nexus-repository-p2:1.0.0 .`

#### Run as a Docker container

`docker run -d -p 8081:8081 --name nexus nexus-repository-p2:1.0.0` 

For further information like how to persist volumes check out [the GitHub repo for our official image](https://github.com/sonatype/docker-nexus3).

The application will now be available from your browser at http://localhost:8081

## Using p2 With Nexus Repository Manager 3

[We have detailed instructions on how to get started here!](https://help.sonatype.com/repomanager3/formats/p2-repositories)

## Compatibility with Nexus Repository Manager 3 Versions

The table below outlines what version of Nexus Repository Manager the plugin was built against

| Plugin Version    | Nexus Repository Manager Version |
|-------------------|----------------------------------|
| v0.0.2            | 3.8.0-02                         |
| v1.0.2 In product | 3.21.0+                          |

If a new version of Nexus Repository Manager is released and the plugin needs changes, a new release will be made, and this
table will be updated to indicate which version of Nexus Repository Manager it will function against. This is done on a time 
available basis, as this is community supported. If you see a new version of Nexus Repository Manager, go ahead and update the
plugin and send us a PR after testing it out!

All released versions can be found [here](https://github.com/sonatype-nexus-community/nexus-repository-p2/releases).

## Features Implemented In This Plugin 

| Feature | Implemented          |
|---------|----------------------|
| Proxy   | :heavy_check_mark: * |
| Hosted  |                      |
| Group   |                      |

NOTE: p2 Proxy does not fully support the following as of yet:

* Old style (non-p2) update sites. These are fairly old, and have not been added at all.

If you'd like it to support the aforementioned sites, please file an issue, or better yet, submit a PR :)

## Installing the plugin

:bangbang: `After Nexus Repository Manager version 3.21, last released plugin's version is available directly in product and there is no need to install it separately` :bangbang:

For older versions there are a range of options for installing the p2 plugin. You'll need to build it first, and
then install the plugin with the options shown below:

### Temporary Install

Installations done via the Karaf console will be wiped out with every restart of Nexus Repository Manager. This is a
good installation path if you are just testing or doing development on the plugin.

* Enable the NXRM console: edit `<nexus_dir>/bin/nexus.vmoptions` and change `karaf.startLocalConsole`  to `true`.

  More details here: [Bundle Development](https://help.sonatype.com/display/NXRM3/Bundle+Development+Overview)

* Run NXRM's console:
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

This will cause the plugin to be loaded with each restart of Nexus Repository Manager. As well, this folder is monitored
by Nexus Repository Manager and the plugin should load within 60 seconds of being copied there if Nexus Repository Manager
is running. You will still need to start the bundle using the karaf commands mentioned in the temporary install.

### (most) Permanent Install

If you are trying to use the p2 plugin permanently, it likely makes more sense to do the following:

* Copy the bundle into `<nexus_dir>/system/org/sonatype/nexus/plugins/nexus-repository-p2/1.0.0/nexus-repository-p2-1.0.0.jar`
* Make the following additions marked with + to `<nexus_dir>/system/org/sonatype/nexus/assemblies/nexus-core-feature/3.x.y/nexus-core-feature-3.x.y-features.xml`

   ```
         <feature prerequisite="false" dependency="false">wrap</feature>
   +     <feature prerequisite="false" dependency="false">nexus-repository-p2</feature>
   ```
   to the `<feature name="nexus-core-feature" description="org.sonatype.nexus.assemblies:nexus-core-feature" version="3.x.y.xy">` section below the last (above is an example, the exact last one may vary).
    
   And
   ```
   + <feature name="nexus-repository-p2" description="org.sonatype.nexus.plugins:nexus-repository-p2" version="1.0.0">
   +     <details>org.sonatype.nexus.plugins:nexus-repository-p2</details>
   +     <bundle>mvn:org.sonatype.nexus.plugins/nexus-repository-p2/1.0.0</bundle>
   + </feature>
    </features>
   ```
   as the last feature.
   
This will cause the plugin to be loaded and started with each startup of Nexus Repository Manager.

## The Fine Print
Starting from version 3.21 the `p2` plugin is supported by Sonatype, but still is a contribution of ours to the open source community (read: you!).

Remember:
* Do file Sonatype support tickets related to p2 support in regard to this plugin

Phew, that was easier than I thought. Last but not least of all:

Have fun creating and using this plugin and the Nexus platform, we are glad to have you here!

## Getting help

Looking to contribute to our code but need some help? There's a few ways to get information:

* p2 repositories Sonatype help documentation [Sonatype Help](https://help.sonatype.com/repomanager3/formats/p2-repositories)
* Chat with us on [Gitter](https://gitter.im/sonatype/nexus-developers)
* Check out the [Nexus3](http://stackoverflow.com/questions/tagged/nexus3) tag on Stack Overflow
* Check out the [Nexus Repository Manager User List](https://groups.google.com/a/glists.sonatype.com/forum/?hl=en#!forum/nexus-users)
