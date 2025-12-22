# GitCraft-Next

## License

GitCraft-Next Copyright (C) 2023 - 2025 WinPlay02 and contributors

This Source Code Form is subject to the terms of the Mozilla Public
License, v. 2.0. If a copy of the MPL was not distributed with this
file, You can obtain one at https://mozilla.org/MPL/2.0/.

## Contributors

- [Kas-tle](https://github.com/Kas-tle)
- [A11v1r15](https://github.com/A11v1r15)
- [SpaceWalkerRS](https://github.com/SpaceWalkerRS)
- [Shnupbups](https://github.com/Shnupbups)
- [0x189D7997](https://github.com/0x189D7997)

## Discussion

- [Discord](https://discord.gg/bK7MFZAbXj)
- [Changelog](changelog.md)

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
The combination of mojmaps and yarn mappings `mojmap_yarn` (like used in Paper) is also available, which uses mojmaps as a base and includes comments and additionally function parameter names from yarn.
For legacy versions, there are {`calamus_intermediary`, `feather`} mappings from OrnitheMC.
For just comparing changes without using mappings, `identity_unmapped` can be used.

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
- [Skyrising Minecraft Version Manifest Collection](https://skyrising.github.io/mc-versions)
- {Libraries, Tools, Mappings} of [OrnitheMC](https://github.com/OrnitheMC)
- [Omniarchive](https://omniarchive.uk/)

## Help / Usage

```
Usage: gradlew run --args="[Options]"
Options:
      --create-stable-version-branches
                             Creates a separate branch for each stable linear
                               version. This may be useful for quickly
                               switching between multiple versions.
      --create-version-branches
                             Creates a separate branch for each version,
                               including linear versions. This may be useful
                               for quickly switching between multiple versions.
      --exceptions=<exceptions>
                             Specifies the exceptions patches used to patch
                               throws clauses into method declarations. None is
                               selected by default. Possible values are: raven,
                               none
      --exclude-version[=<version>[,<version>]...]
                             Specify version(s) to exclude from decompilation.
                               The exclusion info will be added to the
                               repository name. The normal repository will not
                               be touched.
      --fallback-mappings[=<mapping>[,<mapping>]...]
                             If the primary mapping fails, these mappings are
                               tried (in given order). By default none is tried
                               as a fallback. Possible values are: mojmap,
                               fabric_intermediary, yarn, mojmap_parchment,
                               calamus_intermediary, feather,
                               identity_unmapped, mojmap_yarn
      --fallback-unpick[=<mapping>[,<mapping>]...]
                             If the primary unpick information fails, these are
                               tried (in given order). By default this list is
                               empty. Possible values are: none, yarn, feather
  -h, --help                 Displays this help screen
      --manifest-source=<manifestsrc>
                             Specifies the manifest source used to fetch the
                               available versions, the mapping to semantic
                               versions and the dependencies between versions.
                               The Minecraft Launcher Meta (from Mojang) is
                               selected by default. Possible values are:
                               mojang, skyrising, ornithemc, omniarchive,
                               mojang_historic
      --mappings=<mapping>   Specifies the mappings used to decompile the
                               source tree. Mojmaps are selected by default.
                               Possible values are: mojmap,
                               fabric_intermediary, yarn, mojmap_parchment,
                               calamus_intermediary, feather,
                               identity_unmapped, mojmap_yarn
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
      --nests=<nests>        Specifies the nests used to patch inner classes.
                               None is selected by default. Possible values
                               are: ornithe_nests, none
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
      --no-single-side-versions-on-main-branch
                             Forces versions with missing sides (like Beta 1.2
                               _02 or 1.0.1) onto side branches instead of the
                               main branch
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
      --ornithe-intermediary-generation=<generation>
                             Specifies which generation of Ornithe intermediary
                               to use for Ornithe's mapping flavours
      --override-repo-target=<path>
                             Changes the location of the target repository, as
                               repo names may get quite long and unintuitive.
                               If not used carefully, this can lead to
                               repositories with unwanted mixed mappings or
                               straight up refuse to work as some versions in
                               the target repository may be missing.
      --patch-lvt            Generates local variable tables of the Minecraft
                               jars for versions where they were stripped
                               during obfuscation.
      --preening-enabled     Undo merging of specialized and bridge methods.
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
      --repo-gc              Perform a garbage collection pass on the
                               repository after the run. This will probably
                               speed up any subsequent operation on the repo (e.
                               g. viewing diffs).
      --signatures=<signatures>
                             Specifies the signatures patches used to patch
                               generics into class, field, and method
                               declarations. None is selected by default.
                               Possible values are: sparrow, none
      --skip-nonlinear       Skips non-linear (e.g. April Fools, Combat
                               Snapshots, ...) versions completely
      --sort-json            Sorts JSON objects contained in JSON files (e.g.
                               models, language files, ...) in natural order.
                               This is disabled by default as it modifies
                               original data.
      --unpick=<unpick>      Specifies the unpick information used to unpick
                               constants in the source tree. None is selected
                               by default. Possible values are: none, yarn,
                               feather
If you want to decompile versions which are not part of the default minecraft
meta, put the JSON files of these versions (e.g. 1_16_combat-0.json) into the
"extra-versions" directory
```

## Performance
- Windows filesystem implementation degrades performance very much (especially the commit step is affected by this)
- Windows defender will also intercept every file I/O operation, which causes additional slowdown

## Version Manifest Source
- The manifest provider source is changeable, `ManifestProvider` needs to be extended.
- By default, the version manifest information is fetched from [Mojang](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) (`mojang`)
- Known extra versions are fetched from mojang or other sources (like archive.org).
- For more accurate asset versioning and a more complete set of versions, the [Skyrising Version Manifest Collection](https://skyrising.github.io/mc-versions) (`skyrising` or `ornithemc` for the [OrnitheMC flavored variant](https://ornithemc.net/mc-versions)) can be used
- There is also the `mojang_historic` manifest provider, which aims to use most accurate asset indexes to the ones that were present at the time of release of these versions. The [Skyrising Version Manifest Collection](https://skyrising.github.io/mc-versions) is used to obtain older manifest versions and improve on the data from mojang.

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

## Unpick
- all versions of the (fabric) unpick format are supported
- all versions of yarn unpick can be used on any mapping
  - e.g. mojmaps can be used as a primary mapping, and yarn unpick can be applied on top; both settings are independent of each other

## Notes about Parchment
- Parchment supports only release versions of minecraft
- Supported minecraft versions start at `1.16.5`, but not every release version since then is available
- Only "release"-builds of parchment are supported at this time. No nightlies/snapshots are used.

## Helpful git commands

- To remove everything except generated repositories and extra-versions `git clean -d -f -e extra-versions -x`. Cleans meta files and artifacts. If a decompilation is needed, it needs to be done again.
- To bundle a repository, use `git bundle create repo.bundle --all` inside the repository working directory
