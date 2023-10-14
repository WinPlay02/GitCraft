package dex.mcgitmaker.data;

import com.github.winplay02.gitcraft.util.MappingHelper;
import com.github.winplay02.gitcraft.util.MiscHelper;
import dex.mcgitmaker.GitCraft;
import dex.mcgitmaker.loom.Decompiler;
import dex.mcgitmaker.loom.FileSystemUtil;
import net.fabricmc.loom.configuration.providers.BundleMetadata;
import net.fabricmc.stitch.merge.JarMerger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Objects;

public class McVersion {

	public McVersion(String version, String loaderVersion, boolean snapshot, boolean hasMappings, int javaVersion, McArtifacts artifacts, Collection<Artifact> libraries, String mainClass, String mergedJar, String time, String assets_index) {
		this.version = version;
		this.loaderVersion = loaderVersion;
		this.snapshot = snapshot;
		this.hasMappings = hasMappings;
		this.javaVersion = javaVersion;
		this.artifacts = artifacts;
		this.libraries = libraries;
		this.mainClass = mainClass;
		this.mergedJar = mergedJar;
		this.time = time;
		this.assets_index = assets_index;
	}

	public String version; // MC version string from launcher
	public String loaderVersion; // Version from Fabric loader
	public boolean snapshot; // If the version is a release
	public boolean hasMappings; // If the version has mappings provided
	public int javaVersion = 8;
	public McArtifacts artifacts;
	public Collection<Artifact> libraries; // The libraries for this version
	public String mainClass;
	public String mergedJar; // merged client and server

	public String time;
	public String assets_index;

	public Path decompiledMc(MappingHelper.MappingFlavour mappingFlavour) throws IOException {
		Path p = Decompiler.decompiledPath(this, mappingFlavour);
		File f = p.toFile();
		if (!f.exists() || f.length() == 22 /* empty jar */) {
			Decompiler.decompile(this, mappingFlavour);
		}
		return p;
	}

	public boolean removeDecompiled(MappingHelper.MappingFlavour mappingFlavour) throws IOException {
		Path p = Decompiler.decompiledPath(this, mappingFlavour);
		File f = p.toFile();
		if (f.exists()) {
			Files.delete(p);
			return true;
		}
		return false;
	}

	public Path mergedJarPath() {
		return mergedJar == null ? GitCraft.MC_VERSION_STORE.resolve(version).resolve("merged-jar.jar") : Paths.get(mergedJar);
	}

	public File merged() throws IOException {
		Path p = mergedJarPath();
		File f = p.toFile();
		if (!f.exists()) {
			makeMergedJar();
		}
		return f;
	}

	private void unpackJarEntry(BundleMetadata.Entry entry, Path jar, Path dest) throws IOException {
		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(jar); InputStream is = Files.newInputStream(fs.get().getPath(entry.path()))) {
			Files.copy(is, dest, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	void makeMergedJar() throws IOException {
		MiscHelper.println("Merging jars... %s", version);
		File client = artifacts.clientJar().fetchArtifact();
		File server2merge = artifacts.serverJar().fetchArtifact();

		BundleMetadata sbm = BundleMetadata.fromJar(server2merge.toPath());
		if (sbm != null) {
			Path minecraftExtractedServerJar = GitCraft.MC_VERSION_STORE.resolve(version).resolve("extracted-server.jar");

			if (sbm.versions().size() != 1) {
				throw new UnsupportedOperationException("Expected only 1 version in META-INF/versions.list, but got %d".formatted(sbm.versions().size()));
			}

			unpackJarEntry(sbm.versions().get(0), server2merge.toPath(), minecraftExtractedServerJar);
			server2merge = minecraftExtractedServerJar.toFile();
		}

		try (JarMerger jarMerger = new JarMerger(client, server2merge, mergedJarPath().toFile())) {
			jarMerger.enableSyntheticParamsOffset();
			jarMerger.merge();
		}

		mergedJar = mergedJarPath().toString();
	}

	public String toCommitMessage() {
		return this.version + "\n\nSemVer: " + this.loaderVersion;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		McVersion mcVersion = (McVersion) o;
		return snapshot == mcVersion.snapshot && hasMappings == mcVersion.hasMappings && javaVersion == mcVersion.javaVersion && Objects.equals(version, mcVersion.version) && Objects.equals(loaderVersion, mcVersion.loaderVersion) && Objects.equals(artifacts, mcVersion.artifacts) && Objects.equals(libraries, mcVersion.libraries) && Objects.equals(mainClass, mcVersion.mainClass) && Objects.equals(time, mcVersion.time) && Objects.equals(assets_index, mcVersion.assets_index);
	}

	@Override
	public int hashCode() {
		return Objects.hash(version, loaderVersion, snapshot, hasMappings, javaVersion, artifacts, libraries, mainClass, time, assets_index);
	}
}
