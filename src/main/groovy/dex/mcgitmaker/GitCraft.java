package dex.mcgitmaker;

import com.github.winplay02.GitCraftCli;
import com.github.winplay02.GitCraftConfig;
import com.github.winplay02.MappingHelper;
import com.github.winplay02.MinecraftVersionGraph;
import com.github.winplay02.MiscHelper;
import dex.mcgitmaker.data.McMetadata;
import dex.mcgitmaker.data.McVersion;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class GitCraft {

	public static final Path CURRENT_WORKING_DIRECTORY;

	static {
		try {
			CURRENT_WORKING_DIRECTORY = Paths.get(new File(".").getCanonicalPath());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static final Path MAIN_ARTIFACT_STORE = CURRENT_WORKING_DIRECTORY.resolve("artifact-store");

	public static final Path DECOMPILED_WORKINGS = MAIN_ARTIFACT_STORE.resolve("decompiled");
	public static final Path MAPPINGS = MAIN_ARTIFACT_STORE.resolve("mappings");
	public static final Path REPO = MAIN_ARTIFACT_STORE.getParent().resolve("minecraft-repo");
	public static final Path MC_VERSION_STORE = MAIN_ARTIFACT_STORE.resolve("mc-versions");
	public static final Path LIBRARY_STORE = MAIN_ARTIFACT_STORE.resolve("libraries");
	public static final Path METADATA_STORE = MAIN_ARTIFACT_STORE.resolve("metadata.json");
	public static final Path REMAPPED = MAIN_ARTIFACT_STORE.resolve("remapped-mc");
	public static final Path ASSETS_INDEX = MAIN_ARTIFACT_STORE.resolve("assets-index");
	public static final Path ASSETS_OBJECTS = MAIN_ARTIFACT_STORE.resolve("assets-objects");

	public static final Path REMOTE_CACHE = CURRENT_WORKING_DIRECTORY.resolve("remote-cache");
	public static final Path META_CACHE = REMOTE_CACHE.resolve("meta-cache");

	public static final Path SOURCE_EXTRA_VERSIONS = CURRENT_WORKING_DIRECTORY.resolve("extra-versions");

	public static GitCraftConfig config = null;
	MinecraftVersionGraph versionGraph = null;
	McMetadata mcMetadata;

	static {
		MAIN_ARTIFACT_STORE.toFile().mkdirs();
		DECOMPILED_WORKINGS.toFile().mkdirs();
		MAPPINGS.toFile().mkdirs();
		REPO.toFile().mkdirs();
		MC_VERSION_STORE.toFile().mkdirs();
		LIBRARY_STORE.toFile().mkdirs();
		REMAPPED.toFile().mkdirs();
		ASSETS_INDEX.toFile().mkdirs();
		ASSETS_OBJECTS.toFile().mkdirs();

		REMOTE_CACHE.toFile().mkdirs();
		META_CACHE.toFile().mkdirs();

		SOURCE_EXTRA_VERSIONS.toFile().mkdirs();
	}

	GitCraft() throws IOException {
		this.mcMetadata = new McMetadata();
		MiscHelper.println("If generated semver is incorrect, it will break the order of the generated repo.\nConsider updating Fabric Loader. (run ./gradlew run --refresh-dependencies)");
		versionGraph = MinecraftVersionGraph.createFromMetadata(mcMetadata.metadata);
		MiscHelper.println("Saving updated metadata...");
		Util.saveMetadata(mcMetadata.metadata);
		MiscHelper.println("Decompiler log output is suppressed!");
	}

	public static void main(String[] args) throws IOException, GitAPIException {
		GitCraft.config = GitCraftCli.handleCliArgs(args);
		if (GitCraft.config == null) {
			return;
		}
		GitCraft gitCraft = new GitCraft();
		gitCraft.versionGraph = gitCraft.versionGraph.filterMapping(GitCraft.config.usedMapping);
		if (config.isOnlyVersion()) {
			if (config.onlyVersion.length == 0) {
				MiscHelper.panic("No version provided");
			}
			McVersion[] mc_versions = new McVersion[config.onlyVersion.length];
			for (int i = 0; i < mc_versions.length; ++i) {
				mc_versions[i] = gitCraft.versionGraph.getMinecraftMainlineVersionByName(config.onlyVersion[i]);
				if (mc_versions[i] == null) {
					MiscHelper.panic("%s is invalid", config.onlyVersion[i]);
				}
			}
			gitCraft.versionGraph = gitCraft.versionGraph.filterOnlyVersion(mc_versions);
		} else if (config.isMinVersion()) {
			McVersion mc_version = gitCraft.versionGraph.getMinecraftMainlineVersionByName(config.minVersion);
			if (mc_version == null) {
				MiscHelper.panic("%s is invalid", config.minVersion);
			}
			gitCraft.versionGraph = gitCraft.versionGraph.filterMinVersion(mc_version);
		}

		if (config.skipNonLinear) {
			gitCraft.versionGraph = gitCraft.versionGraph.filterMainlineVersions();
		}

		RepoManager repoManager = gitCraft.updateRepo();
		MiscHelper.println("Repo can be found at: %s", repoManager.root_path.toString());
	}

	void refreshDeleteDecompiledJar(McVersion mcVersion, MappingHelper.MappingFlavour mappingFlavour) throws IOException {
		if (mcVersion.removeDecompiled(mappingFlavour)) {
			MiscHelper.println("$%s.jar has been deleted", mcVersion.version);
			decompileNoRepository(mcVersion, mappingFlavour);
		}
	}

	void decompileNoRepository(McVersion mcVersion, MappingHelper.MappingFlavour mappingFlavour) throws IOException {
		mcVersion.decompiledMc(mappingFlavour);
		if (config.loadAssets && config.loadAssetsExtern) {
			McMetadata.fetchAssetsOnly(mcVersion);
		}
	}

	RepoManager updateRepo() throws GitAPIException, IOException {
		RepoManager r = null;
		if (!config.noRepo) {
			String identifier = versionGraph.repoTagsIdentifier(GitCraft.config.usedMapping);
			r = new RepoManager(this.versionGraph, identifier.isEmpty() ? null : MAIN_ARTIFACT_STORE.getParent().resolve(String.format("minecraft-repo-%s", identifier)));
		}
		for (McVersion mcv : versionGraph) {
			if (config.refreshDecompilation) {
				refreshDeleteDecompiledJar(mcv, GitCraft.config.usedMapping);
			}
			if (!config.noRepo) {
				assert r != null;
				r.commitDecompiled(mcv, GitCraft.config.usedMapping);
			} else {
				decompileNoRepository(mcv, GitCraft.config.usedMapping);
			}
		}
		if (!config.noRepo) {
			assert r != null;
			r.close();
		}
		return r;
	}
}
