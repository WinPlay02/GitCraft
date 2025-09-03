# Changelog

## 0.3

#### Breaking changes

- intermediate artifacts are now fully qualified, using all steps that modified them
- all artifacts now record (part of) the hash of their source artifact(s)
- multiple meta sources can use the same version id, but referencing different artifacts
- asset indexes now qualify their names even more fully

> [!WARNING]
> Unpicking is no longer enabled by default

> [!NOTE]
> **More information in --help**

#### Other changes

- new meta sources: skyrising, mojang_historic
- new mappings: Ornithe {calamus, feather}, Mojmap+Yarn
- unpicking (mostly) independent of mappings; support for unpick-v3
- new steps for legacy versions: patching local variable tables, nesting (correct nested classes), apply signature patches (to handle generics), applying exception patches, preening (undo merging of specialized and bridge methods)
- multithreading
- garbage collecting for repositories can now be enabled (and is enabled by default)
- updated dependencies

> [!CAUTION]
> Most of the metadata store will be unusable after this upgrade.
> It is recommended to remove most of the artifact-store. Redundant data won't be used but will still use storage space.

## 0.2

- More known versions are automatically downloaded (like experimental snapshots)
- Commits may now be merges of multiple previous versions
- Datagen is executed by default (registry reports, NBT -> SNBT)
- Vanilla worldgen datapack is now downloaded for versions, where there are no other ways of obtaining these files
- Comments are enabled for yarn generation
- Constant unpicking is now done for yarn generation

> [!NOTE]
> **More information in --help**
