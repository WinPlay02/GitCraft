# GitCraft
Generates a Git repository of decompiled Minecraft, starting from 1.14.4. For personal use only. Do not share or upload the resulting repository.

To get started, execute `./gradlew run` from the command line, advanced usage (`./gradlew run --args="[Options]"`) is shown below.

By default, integrated assets (e.g. models), external assets (e.g. other languages) and the integrated datapacks are included in the generated repository.
This can be changed by adding the `--no-assets`, `--no-external-assets` and `--no-datapack` parameters.

Artifacts are stored in the current working directory:
- Metadata, mappings, assets and other temporary files go into `artifact-store`.
- The generated Git repository with MC's source code goes into `minecraft-repo`,  `minecraft-repo-<version>` or  `minecraft-repo-min-<version>`.
- The decompiled code is stored in separate JARs inside the `artifact-store/decompiled` directory, to not write thousands of files directly onto the file system
- To decompile versions not provided by mojang directly, put the meta files into `extra-versions` and they will be picked up

If only a specific version should be decompiled and versioned or a version range should be decompiled, provide the `--only-version=<version>` or `--min-version=<version>` parameters. `<version>` should be a (human readable) version name, e.g. `23w14a` or `1.20.1`.

To disabled versioning entirely (and only decompiled), specify `--no-repo`.

To disable special versions (e.g. april fools or combat snapshots), specify `--skip-nonlinear`.

Powered by:
- [Quiltflower](https://github.com/QuiltMC/quiltflower)
- [Fabric Loader](https://github.com/FabricMC/fabric-loader)
- [Mapping-IO](https://github.com/FabricMC/mapping-io)
- [Tiny Remapper](https://github.com/FabricMC/tiny-remapper)
- Mojang's generous mapping files (Mojmap)

## Help / Usage

```
Usage: gradlew run --args="[Options]"
Options:
  -h, --help                 Displays this help screen
      --min-version=<version>
                             Specify the min version to decompile. Each
                               following (mainline) version will be decompiled
                               afterwards. The repository will be stored in
                               minecraft-repo-min-<version>. The normal
                               repository (minecraft-repo) will not be touched.
                               Implies --skip-nonlinear
      --no-assets            Disables assets versioning (includes external
                               assets)
      --no-datapack          Disables data (integrated datapack) versioning
      --no-external-assets   Disables assets versioning for assets not included
                               inside "minecraft".jar (e.g. other languages).
                               Has no effect if --no-assets is specified
      --no-repo              Does not create a repository for versioning, only
                               decompiles the provided (or all) version(s)
      --no-verify            Disables checksum verification
      --only-version=<version>
                             Specify the only version to decompile. The
                               repository be stored in
                               minecraft-repo-<version>. The normal repository
                               (minecraft-repo) will not be touched.
                               --only-version will take precedence over
                               --min-version. Implies --skip-nonlinear
      --skip-nonlinear       Skips non-linear (e.g. April Fools, Combat
                               Snapshots, ...) versions
If you want to decompile versions which are not part of the default minecraft
meta, put the JSON files of these versions (e.g. 1_16_combat-0.json)
into the "extra-versions" directory
```