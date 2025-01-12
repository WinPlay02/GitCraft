# GitCraft-Next

## License

GitCraft-Next Copyright (C) 2023 - 2025 WinPlay02 and contributors

This Source Code Form is subject to the terms of the Mozilla Public
License, v. 2.0. If a copy of the MPL was not distributed with this
file, You can obtain one at https://mozilla.org/MPL/2.0/.

## Contributors

- @Kas-tle
- @A11v1r15
- @SpaceWalkerRS

## General

Generates a Git repository of decompiled Minecraft. For personal use only. Do not share or upload the resulting repository.

To get started, execute `./gradlew run` from the command line, advanced usage (`./gradlew run --args="[Options]"`) is shown below.

By default, integrated assets (e.g. models), external assets (e.g. other languages) and the integrated datapacks are included in the generated repository.
This can be changed by adding the `--no-assets`, `--no-external-assets` and `--no-datapack` parameters.

Artifacts are stored in the current working directory:
- Metadata, mappings, assets and other temporary files go into `artifact-store`.
- The generated Git repository with MC's source code goes into `minecraft-repo`,  `minecraft-repo-<version>` or  `minecraft-repo-min-<version>`.
- The decompiled code is stored in separate JARs inside the `artifact-store/decompiled` directory, to not write thousands of files directly onto the file system
- To decompile versions not provided by Mojang directly, put the meta files into `extra-versions` and they will be picked up

If only a specific version should be decompiled and versioned or a version range should be decompiled, provide the `--only-version=<version>`, `--min-version=<version>` or `--max-version=<version>` parameters. `<version>` should be a (human-readable) version name, e.g. `23w14a` or `1.20.1`.
To exclude only a specific version (or multiple versions) use `--exclude-version`.

To disabled versioning entirely (and only decompile), specify `--no-repo`.

To disable special versions (e.g. april fools or combat snapshots), specify `--skip-nonlinear`.
To disable either snapshots or stable releases, use either `--only-stable` or `--only-snapshot`.

To use other mappings than mojmaps, specify `--mappings=<mapping>`. Supported mappings are `mojmap`, `mojmap_parchment`, `fabric_intermediary` and `yarn`.

Fallback mappings can be used with `--fallback-mappings`. For example `mojmap` could be used as a fallback to a `mojmap_parchment` mapping, as not every version of minecraft is available.

If a specific target directory should be used, instead of the default generated repository name, use `--override-repo-target`.

Powered by:
- [Vineflower](https://github.com/Vineflower/vineflower)
- [Fabric Loader](https://github.com/FabricMC/fabric-loader)
- [Mapping-IO](https://github.com/FabricMC/mapping-io)
- [Tiny Remapper](https://github.com/FabricMC/tiny-remapper)
- [Fabric Unpick](https://github.com/FabricMC/unpick)
- Mojang's generous mapping files (Mojmap)
- [Loom](https://github.com/FabricMC/fabric-loom)
- [Yarn](https://github.com/FabricMC/yarn)
- [Fabric Intermediary Mappings](https://github.com/FabricMC/intermediary)
- [Parchment](https://github.com/ParchmentMC/Parchment)

## Help / Usage

```
Usage: gradlew run --args="[Options]"
Options:
      --create-version-branches
                             Creates a separate branch for each version,
                               including linear versions. This may be useful
                               for quickly switching between multiple versions.
      --create-stable-version-branches
                             Creates a separate branch for each stable linear 
                               versions. This may be useful for quickly switching
                               between multiple versions.
      --exclude-version[=<version>[,<version>]...]
                             Specify version(s) to exclude from decompilation.
                               The exclusion info will be added to the
                               repository name. The normal repository will not
                               be touched.
      --fallback-mappings[=<mapping>[,<mapping>]...]
                             If the primary mapping fails, these mappings are
                               tried (in given order). By default none is tried
                               as a fallback. Possible values are: mojmap,
                               fabric_intermediary, yarn, mojmap_parchment
  -h, --help                 Displays this help screen
      --mappings=<mapping>   Specifies the mappings used to decompile the
                               source tree. Mojmaps are selected by default.
                               Possible values are: mojmap,
                               fabric_intermediary, yarn, mojmap_parchment
      --max-version=<version>
                             Specify the max. version to decompile. Every
                               version before (and including) the specified
                               will be decompiled afterwards, non-linear
                               versions are still committed to separate
                               branches. The repository will be stored in
                               minecraft-repo-max-<version>. The normal
                               repository will not be touched.
      --min-version=<version>
                             Specify the min. version to decompile. Each
                               following version will be decompiled afterwards,
                               non-linear versions are still committed to
                               separate branches. The repository will be stored
                               in minecraft-repo-min-<version>. The normal
                               repository will not be touched.
      --no-assets            Disables assets versioning (includes external
                               assets)
      --no-datagen-report    Disables datagen for versioning reports (like
                               blocks, and other registries)
      --no-datagen-snbt      Disables datagen for converting NBT files (like
                               structures) to SNBT files. If "no-datapack" is
                               set, this flag is automatically set.
      --no-datapack          Disables data (integrated datapack) versioning
      --no-external-assets   Disables assets versioning for assets not included
                               inside "minecraft".jar (e.g. other languages).
                               Has no effect if --no-assets is specified
      --no-repo              Prevents the creation/modification of a repository
                               for versioning, only decompiles the provided (or
                               all) version(s)
      --no-verify            Disables checksum verification
      --only-snapshot        Only decompiles snapshots (includes pending and
                               non-linear, if not otherwise specified).
      --only-stable          Only decompiles stable releases.
      --only-version[=<version>[,<version>]...]
                             Specify the only version(s) to decompile. The
                               repository be stored in
                               minecraft-repo-<version>-<version>-.... The
                               normal repository will not be touched.
                               --only-version will take precedence over
                               --min-version.
      --override-repo-target=<path>
                             Changes the location of the target repository, as
                               repo names may get quite long and unintuitive.
                               If not used carefully, this can lead to
                               repositories with unwanted mixed mappings or
                               straight up refuse to work as some versions in
                               the target repository may be missing.
      --refresh              Refreshes the decompilation by deleting old
                               decompiled artifacts and restarting. This may be
                               useful, if the decompiler has been updated or
                               new mappings exist. The repository will get
                               updated (existing commits will get deleted, and
                               new ones will be inserted). This will cause
                               local branches to diverge from remote branches,
                               if any exist.
      --refresh-max-version=<version>
                             Restricts the max. refreshed version to the one
                               provided. This options will cause the git
                               repository to refresh.
      --refresh-min-version=<version>
                             Restricts the min. refreshed version to the one
                               provided. This options will cause the git
                               repository to refresh.
      --refresh-only-version[=<version>[,<version>]...]
                             Restricts the refreshed versions to the ones
                               provided. This options will cause the git
                               repository to refresh.
      --skip-nonlinear       Skips non-linear (e.g. April Fools, Combat
                               Snapshots, ...) versions completely
      --sort-json            Sorts JSON objects contained in JSON files (e.g.
                               models, language files, ...) in natural order.
                               This is disabled by default as it modifies
                               original data.
If you want to decompile versions which are not part of the default minecraft
meta, put the JSON files of these versions (e.g. 1_16_combat-0.json) into the
"extra-versions" directory
```

## Performance
- Windows filesystem implementation degrades performance very much (especially the commit step is affected by this)
- Windows defender will also intercept every file I/O operation, which causes additional slowdown

## Version Manifest Source
- The manifest provider source is changeable, `ManifestProvider` needs to be extended.
- By default, the version manifest information is fetched from [Mojang](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json)
- Known extra versions are fetched from mojang or other sources (like archive.org).

## Fork / Changes
- This repository was originally forked from [dexman545/GitCraft](https://github.com/dexman545/GitCraft)
- More mappings are supported: Yarn, Parchment, (Fabric Intermediary), Mojmaps
- Assets versioning is supported
- Dynamic loading of meta files
- complex version graphs
- Datagen information is included
- Most of the code was rewritten to allow these changes

## Notes about Yarn
- Some versions can not (yet) be remapped with yarn
  - Some versions of yarn are completely broken, so they are skipped. Affected versions: `19w13a`, `19w13b`, `19w14a`, `19w14b`
- Other quirks, which were successfully worked around:
  - Some build of yarn are broken, so older ones are used instead. Affected versions: `19w04b`, `19w08a`, `19w12b`
  - Older versions of yarn don't exist in merged form. They are merged with intermediary mappings. Affected versions: `< 20w09a`
  - Older versions of yarn only exist with switched namespaces. They were switched back to their correct namespaces. Affected versions: `< 1.14.3`
    - This seems to be only happening with "newer" v2 builds, not the old tiny-v1 builds
  - One version of yarn exists in maven, but does not exist in meta.fabricmc.net. Affected version: `1.14.2 Pre-Release 1`
  - Some combat snapshots are located in a non-standard-path (on maven.fabricmc.net and on meta.fabricmc.net). Affected versions: `1.15_combat-6`, `1.16_combat-0`
- Version `1.16_combat-1`, `1.16_combat-2`, `1.16_combat-4`, `1.16_combat-5`, `1.16_combat-6` do not exist at all
- Javadoc comments and constant unpicking is supported

## Notes about Parchment
- Parchment supports only release versions of minecraft
- Supported minecraft versions start at `1.16.5`, but not every release version since then is available
- Only "release"-builds of parchment are supported at this time. No nightlies/snapshots are used.

## Helpful git commands

- To remove everything except generated repositories and extra-versions `git clean -d -f -e extra-versions -x`. Cleans meta files and artifacts. If a decompilation is needed, it needs to be done again.
- To bundle a repository, use `git bundle create repo.bundle --all` inside the repository working directory
