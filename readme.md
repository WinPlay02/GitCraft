# GitCraft
Generates a Git repository of decompiled Minecraft, starting from 1.14.4. For personal use only. Do not share or upload the resulting repository.

To get started, execute `./gradlew run` from the command line.

Artifacts are stored in the current working directory:
- Metadata, mappings and other temporary files go into `artifact-store`.
- The generated Git repository with MC's source code goes into `minecraft-repo`.

Powered by:
- [Quiltflower](https://github.com/QuiltMC/quiltflower)
- [Fabric Loader](https://github.com/FabricMC/fabric-loader)
- [Mapping-IO](https://github.com/FabricMC/mapping-io)
- [Tiny Remapper](https://github.com/FabricMC/tiny-remapper)
- Mojang's generous mapping files (Mojmap)
