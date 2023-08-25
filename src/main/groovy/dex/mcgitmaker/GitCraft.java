package dex.mcgitmaker;

import com.github.winplay02.GitCraftCli;
import com.github.winplay02.GitCraftConfig;
import com.github.winplay02.MiscHelper;
import dex.mcgitmaker.data.McMetadata;
import dex.mcgitmaker.data.McVersion;
import dex.mcgitmaker.loom.Decompiler;
import net.fabricmc.loader.api.SemanticVersion;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

import net.fabricmc.loader.api.VersionParsingException;
import org.eclipse.jgit.api.errors.GitAPIException;

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
	McMetadata mcMetadata;
	TreeMap<SemanticVersion, McVersion> versions;
	TreeMap<SemanticVersion, McVersion> nonLinearVersions;

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

	GitCraft() throws IOException, VersionParsingException {
		this.mcMetadata = new McMetadata();
		MiscHelper.println("If generated semver is incorrect, it will break the order of the generated repo.\nConsider updating Fabric Loader. (run ./gradlew run --refresh-dependencies)");
		versions = Util.orderVersionMap(mcMetadata.metadata);
		nonLinearVersions = Util.nonLinearVersionList(mcMetadata.metadata);
		MiscHelper.println("Saving updated metadata...");
		Util.saveMetadata(mcMetadata.metadata);
	}

	public static void main(String[] args) throws VersionParsingException, IOException, GitAPIException {
		GitCraft.config = GitCraftCli.handleCliArgs(args);
		if (GitCraft.config == null) {
			return;
		}
		GitCraft gitCraft = new GitCraft();
		RepoManager repoManager;
		if (config.isOnlyVersion()) {
			repoManager = gitCraft.updateRepoOneVersion(config.onlyVersion);
		} else if (config.isMinVersion()) {
			repoManager = gitCraft.updateRepoMinVersion(config.minVersion);
		} else {
			repoManager = gitCraft.updateRepo();
		}
		MiscHelper.println("Repo can be found at: %s", repoManager.root_path.toString());
	}

	void refreshDeleteDecompiledJar(McVersion mcVersion) throws IOException {
		if (mcVersion.removeDecompiled()) {
			MiscHelper.println("$%s.jar has been deleted", mcVersion.version);
			decompileNoRepository(mcVersion);
		}
	}

	void decompileNoRepository(McVersion mcVersion) throws IOException {
		mcVersion.decompiledMc();
		if (config.loadAssets && config.loadAssetsExtern) {
			McMetadata.fetchAssetsOnly(mcVersion);
		}
	}

	McVersion getMinecraftMainlineVersionByName(String version_name) {
		for (McVersion value : versions.values()) {
			if (value.version.equalsIgnoreCase(version_name)) {
				return value;
			}
		}
		return null;
	}

	RepoManager updateRepoMinVersion(String version_name) throws GitAPIException, IOException {
		McVersion mc_version = getMinecraftMainlineVersionByName(version_name);
		if (mc_version == null) {
			MiscHelper.panic("%s is invalid", version_name);
		}
		RepoManager r = null;
		if (!config.noRepo) {
			r = new RepoManager(MAIN_ARTIFACT_STORE.getParent().resolve("minecraft-repo-min-" + version_name));
		}

		boolean decompile_starting_this_version = false;
		for (McVersion value : versions.values()) {
			if (value.version.equalsIgnoreCase(version_name)) {
				decompile_starting_this_version = true;
			}
			if (!decompile_starting_this_version) {
				continue;
			}
			if (config.refreshDecompilation) {
				refreshDeleteDecompiledJar(value);
			}
			if (!config.noRepo) {
				assert r != null;
				r.commitDecompiled(value);
			} else {
				decompileNoRepository(value);
			}
		}
		if (!config.noRepo) {
			assert r != null;
			r.finish();
		}
		return r;
	}

	RepoManager updateRepoOneVersion(String... version_name_list) throws GitAPIException, IOException {
		if (version_name_list.length == 0) {
			MiscHelper.panic("No version provided");
		}
		List<McVersion> mc_versions = new ArrayList<>();
		for (String version_name : version_name_list) {
			McVersion mc_version = getMinecraftMainlineVersionByName(version_name);
			if (mc_version == null) {
				MiscHelper.panic("%s is invalid", version_name);
			}
			mc_versions.add(mc_version);
		}
		mc_versions = Util.orderVersionList(mc_versions);

		RepoManager r = null;
		if (!config.noRepo) {
			String directory_name = "minecraft-repo-" + mc_versions.stream().map((mcv) -> mcv.version).collect(Collectors.joining("-"));
			r = new RepoManager(MAIN_ARTIFACT_STORE.getParent().resolve(directory_name));
		}
		for (McVersion mc_version : mc_versions) {
			if (config.refreshDecompilation) {
				refreshDeleteDecompiledJar(mc_version);
			}
			if (!config.noRepo) {
				assert r != null;
				r.commitDecompiled(mc_version);
			} else {
				decompileNoRepository(mc_version);
			}
		}

		if (!config.noRepo) {
			assert r != null;
			r.finish();
		}
		return r;
	}

	RepoManager updateRepo() throws GitAPIException, IOException {
		RepoManager r = null;
		if (!config.noRepo) {
			r = new RepoManager();
		}
		for (McVersion mcv : versions.values()) {
			if (config.refreshDecompilation) {
				refreshDeleteDecompiledJar(mcv);
			}
			if (!config.noRepo) {
				assert r != null;
				r.commitDecompiled(mcv);
			} else {
				decompileNoRepository(mcv);
			}
		}

		// Only commit non-linear versions after linear versions to find correct branching point
		if (!config.skipNonLinear) {
			for (McVersion mcv : nonLinearVersions.values()) {
				if (config.refreshDecompilation) {
					refreshDeleteDecompiledJar(mcv);
				}
				if (!config.noRepo) {
					assert r != null;
					r.commitDecompiled(mcv);
				} else {
					decompileNoRepository(mcv);
				}
			}
		}
		if (!config.noRepo) {
			assert r != null;
			r.finish();
		}
		return r;
	}
}
