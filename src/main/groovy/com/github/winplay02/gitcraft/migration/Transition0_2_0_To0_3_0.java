package com.github.winplay02.gitcraft.migration;

import com.github.winplay02.gitcraft.LibraryPaths;
import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemRoot;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
import com.github.winplay02.gitcraft.util.MiscHelper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

public class Transition0_2_0_To0_3_0 implements MetadataStoreUpgrade {
	@Override
	public String sourceVersion() {
		return "0.2.0";
	}

	@Override
	public String targetVersion() {
		return "0.3.0";
	}

	@Override
	public void upgrade() throws IOException {
		upgradeExtraVersions();
		upgradeMergedObfuscated();
		upgradeRemappedLikeTree(GitCraftPipelineFilesystemRoot.getRemapped().apply(GitCraftPaths.FILESYSTEM_ROOT), false, false, unpicked -> unpicked ? "unpicked" : "remapped");
		upgradeRemappedLikeTree(GitCraftPipelineFilesystemRoot.getDecompiled().apply(GitCraftPaths.FILESYSTEM_ROOT), true, true, $ -> null);
	}

	private void upgradeExtraVersions() throws IOException {
		Path extraVersionsDir = LibraryPaths.CURRENT_WORKING_DIRECTORY.resolve("extra-versions");
		Path targetExtraVersionsDir = GitCraftPipelineFilesystemRoot.getMcExtraVersionStore().apply(GitCraftPaths.FILESYSTEM_ROOT).resolve("mojang-launcher");
		// Move from artifact-store to lost-and-found
		if (Files.exists(targetExtraVersionsDir) && !MiscHelper.isDirectoryEmpty(targetExtraVersionsDir)) {
			Path lostAndFoundDirectory = this.getLostAndFoundDirectory();
			Files.createDirectories(lostAndFoundDirectory);
			MiscHelper.moveLargeDir(targetExtraVersionsDir, lostAndFoundDirectory.resolve("extra-versions"));
			MiscHelper.println("Existing directory '%s' was moved to lost-and-found", GitCraftPaths.FILESYSTEM_ROOT.getRoot().relativize(targetExtraVersionsDir));
		}
		// Try moving first
		if (Files.exists(extraVersionsDir)) {
			MiscHelper.moveLargeDir(extraVersionsDir, targetExtraVersionsDir);
		}
	}

	private void upgradeMergedObfuscated() throws IOException {
		try (Stream<Path> versionStore = Files.list(GitCraftPipelineFilesystemRoot.getMcVersionStore().apply(GitCraftPaths.FILESYSTEM_ROOT))) {
			for (Path versionPath : versionStore.toList()) {
				Path obfuscatedMergedPath = versionPath.resolve("merged-obfuscated.jar");
				Path newMergedPath = versionPath.resolve("merged.jar");
				if (Files.exists(obfuscatedMergedPath)) {
					if (Files.exists(newMergedPath)) {
						Path lostAndFoundMerged = this.getLostAndFoundDirectory().resolve(GitCraftPaths.FILESYSTEM_ROOT.getRoot().relativize(newMergedPath));
						Files.createDirectories(lostAndFoundMerged.getParent());
						Files.move(newMergedPath, lostAndFoundMerged);
					}
					Files.move(obfuscatedMergedPath, newMergedPath);
				}
			}
		}
	}

	private void upgradeRemappedLikeTree(Path directoryRoot, boolean throwAwayYarn, boolean includeUnpickEvenIfNone, Function<Boolean, String> nameDependentOnUnpick) throws IOException {
		try (Stream<Path> versionStore = Files.list(directoryRoot)) {
			for (Path remappedArtifactPath : versionStore.toList()) {
				if (!Files.isRegularFile(remappedArtifactPath)) {
					continue;
				}
				if (!remappedArtifactPath.getFileName().toString().endsWith(".jar")) {
					// Move non-jars to lost-and-found
					Path lostAndFoundRemapped = this.getLostAndFoundDirectory().resolve(GitCraftPaths.FILESYSTEM_ROOT.getRoot().relativize(remappedArtifactPath));
					Files.createDirectories(lostAndFoundRemapped.getParent());
					Files.move(remappedArtifactPath, lostAndFoundRemapped);
					continue;
				}
				try {
					String fileName = remappedArtifactPath.getFileName().toString();
					String fileNameWithoutExt = fileName.substring(0, fileName.length() - 4);
					String[] fileParts = fileNameWithoutExt.split("-");
					//
					int indexFromEnd = 1;
					String fileNamePart = fileParts[fileParts.length - indexFromEnd];
					String prefix = switch (fileNamePart) {
						case "client", "server" -> {
							indexFromEnd++;
							yield fileNamePart;
						}
						default -> "merged";
					};
					//
					fileNamePart = fileParts[fileParts.length - indexFromEnd];
					boolean unpicked = false;
					switch (fileNamePart) {
						case "unpicked" -> {
							indexFromEnd++;
							unpicked = true;
						}
					};
					String suffix = nameDependentOnUnpick.apply(unpicked);
					//
					fileNamePart = fileParts[fileParts.length - indexFromEnd];
					String mapping = fileNamePart;
					if ("yarn".equalsIgnoreCase(mapping) && throwAwayYarn && !unpicked) {
						throw new IOException(fileName + " should be thrown away");
					}
					String version = String.join("-", Arrays.copyOfRange(fileParts, 0, fileParts.length - indexFromEnd));
					List<String> newFileNameParts = new ArrayList<>(List.of(prefix));
					if (suffix != null) {
						newFileNameParts.add(suffix);
					}
					newFileNameParts.add("map_" + mapping);
					if (unpicked) {
						newFileNameParts.add("un_" + mapping);
					} else if(includeUnpickEvenIfNone) {
						newFileNameParts.add("un_none");
					}
					newFileNameParts.add("exc_none-sig_none");
					String outputFileName = String.join("-", newFileNameParts) + ".jar";

					Path newRemappedPath = directoryRoot.resolve(version).resolve(outputFileName);
					if (Files.exists(remappedArtifactPath)) {
						if (Files.exists(newRemappedPath)) {
							// This time, move artifact to be renamed to lost and found, as the name is very specific
							Path lostAndFoundRemapped = this.getLostAndFoundDirectory().resolve(GitCraftPaths.FILESYSTEM_ROOT.getRoot().relativize(remappedArtifactPath));
							Files.createDirectories(lostAndFoundRemapped.getParent());
							Files.move(remappedArtifactPath, lostAndFoundRemapped);
						} else {
							Files.createDirectories(newRemappedPath.getParent());
							Files.move(remappedArtifactPath, newRemappedPath);
						}
					}
				} catch (IOException e) {
					// If any parsing... fails, just give up and move to lost-and-found
					Path lostAndFoundRemapped = this.getLostAndFoundDirectory().resolve(GitCraftPaths.FILESYSTEM_ROOT.getRoot().relativize(remappedArtifactPath));
					Files.createDirectories(lostAndFoundRemapped.getParent());
					Files.move(remappedArtifactPath, lostAndFoundRemapped);
				}
			}
		}
	}

	@Override
	public List<String> upgradeInfo() {
		return List.of(
			"WARNING: There were breaking changes to existing metadata stores",
			"- intermediate artifacts are now fully qualified, using all steps that modified them",
			"- all artifacts now record (part of) the hash of their source artifact(s)",
			"- multiple meta sources can use the same version id, but referencing different artifacts",
			"- asset indexes now qualify their names even more fully",
			"- unpicking is no longer enabled by default, more information in --help",
			"Other changes:",
			"- new meta sources: skyrising, mojang_historic",
			"- new mappings: Ornithe {calamus, feather}, Mojmap+Yarn",
			"- unpicking (mostly) independent of mappings; support for unpick-v3",
			"- new steps for legacy versions: patching local variable tables, nesting (correct nested classes), apply signature patches (to handle generics), applying exception patches, preening (undo merging of specialized and bridge methods)",
			"- multithreading",
			"- garbage collecting for repositories can now be enabled",
			"- updated dependencies",
			"!!! Most of the metadata store will be unusable after this upgrade. !!!",
			"!!! It is recommended to remove most of the artifact-store. Other data won't be used but will still use storage space. !!!"
		);
	}
}
