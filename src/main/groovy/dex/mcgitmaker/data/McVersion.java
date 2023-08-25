package dex.mcgitmaker.data;

import dex.mcgitmaker.GitCraft;
import dex.mcgitmaker.Util;
import dex.mcgitmaker.loom.BundleMetadata;
import dex.mcgitmaker.loom.Decompiler;
import dex.mcgitmaker.loom.Remapper;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.format.ProGuardReader;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.stitch.merge.JarMerger;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.TinyUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;

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

	public Path decompiledMc() throws IOException {
		Path p = Decompiler.decompiledPath(this);
		File f = p.toFile();
		if (!f.exists() || f.length() == 22 /* empty jar */) {
			Decompiler.decompile(this);
		}
		return p;
	}

	public boolean removeDecompiled() throws IOException {
		Path p = Decompiler.decompiledPath(this);
		File f = p.toFile();
		if (f.exists()) {
			Files.delete(p);
			return true;
		}
		return false;
	}

	public Path remappedJar() throws IOException {
		return Remapper.doRemap(this);
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

	public IMappingProvider mappingsProvider() throws IOException {
		return TinyUtils.createTinyMappingProvider(mappingsPath(),
				Util.MappingsNamespace.OFFICIAL.toString(), Util.MappingsNamespace.MOJMAP.toString());
	}

	Path mappingsPath() throws IOException {
		Path mappingsFile = GitCraft.MAPPINGS.resolve(version + ".tiny");

		if (!mappingsFile.toFile().exists()) {
			MemoryMappingTree mappingTree = new MemoryMappingTree();

			// Make official the source namespace
			MappingSourceNsSwitch nsSwitch = new MappingSourceNsSwitch(mappingTree, Util.MappingsNamespace.OFFICIAL.toString());

			try (BufferedReader clientBufferedReader = Files.newBufferedReader(artifacts.clientMappings().fetchArtifact().toPath(), StandardCharsets.UTF_8);
				 BufferedReader serverBufferedReader = Files.newBufferedReader(artifacts.serverMappings().fetchArtifact().toPath(), StandardCharsets.UTF_8)) {
				ProGuardReader.read((Reader) clientBufferedReader, Util.MappingsNamespace.MOJMAP.toString(), Util.MappingsNamespace.OFFICIAL.toString(), nsSwitch);
				ProGuardReader.read((Reader) serverBufferedReader, Util.MappingsNamespace.MOJMAP.toString(), Util.MappingsNamespace.OFFICIAL.toString(), nsSwitch);
			}
			try (MappingWriter w = MappingWriter.create(mappingsFile, MappingFormat.TINY_2)) {
				mappingTree.accept(w);
			}
		}

		return mappingsFile;
	}

	void makeMergedJar() throws IOException {
		System.out.printf("Merging jars... %s", version);
		File client = artifacts.clientJar().fetchArtifact();
		File server2merge = artifacts.serverJar().fetchArtifact();

		BundleMetadata sbm = BundleMetadata.fromJar(server2merge.toPath());
		if (sbm != null) {
			Path minecraftExtractedServerJar = GitCraft.MC_VERSION_STORE.resolve(version).resolve("extracted-server.jar");

			if (sbm.versions.size() != 1) {
				throw new UnsupportedOperationException("Expected only 1 version in META-INF/versions.list, but got %d".formatted(sbm.versions.size()));
			}

			sbm.versions.get(0).unpackEntry(server2merge.toPath(), minecraftExtractedServerJar);
			server2merge = minecraftExtractedServerJar.toFile();
		}

		try (JarMerger jarMerger = new JarMerger(client, server2merge, mergedJarPath().toFile())) {
			jarMerger.enableSyntheticParamsOffset();
			jarMerger.merge();
		}

		mergedJar = mergedJarPath().toString();
	}
}
