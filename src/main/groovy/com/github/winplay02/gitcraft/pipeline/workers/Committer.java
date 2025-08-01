package com.github.winplay02.gitcraft.pipeline.workers;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.Library;
import com.github.winplay02.gitcraft.graph.AbstractVersionGraph;
import com.github.winplay02.gitcraft.meta.AssetsIndexMetadata;
import com.github.winplay02.gitcraft.pipeline.Pipeline;
import com.github.winplay02.gitcraft.pipeline.StepInput;
import com.github.winplay02.gitcraft.pipeline.StepOutput;
import com.github.winplay02.gitcraft.pipeline.StepResults;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.StepWorker;
import com.github.winplay02.gitcraft.pipeline.key.StorageKey;
import com.github.winplay02.gitcraft.types.AssetsIndex;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RepoWrapper;
import com.github.winplay02.gitcraft.util.SerializationHelper;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loom.util.FileSystemUtil;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.stream.StreamSupport;

public record Committer(StepWorker.Config config) implements StepWorker<OrderedVersion, Committer.Inputs> {

	@Override
	public StepOutput<OrderedVersion> run(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, Committer.Inputs input, StepResults<OrderedVersion> results) throws Exception {
		if (GitCraft.getTransientApplicationConfiguration().noRepo()) {
			return StepOutput.ofEmptyResultSet(StepStatus.NOT_RUN);
		}
		// Check validity of prepared args
		Objects.requireNonNull(context.repository());
		// Clean First
		MiscHelper.executeTimedStep("Clearing working directory...", context.repository()::clearWorkingTree);
		// Switch Branch
		Optional<String> target_branch = switchBranchIfNeeded(context.targetVersion(), context.versionGraph(), context.repository());
		if (target_branch.isEmpty()) {
			return StepOutput.ofEmptyResultSet(StepStatus.UP_TO_DATE);
		}
		// Copy to repository
		MiscHelper.executeTimedStep("Moving files to repo...", () -> {
			// Copy decompiled MC code to repo directory
			copyCode(pipeline, context, input);
			// Copy assets & data (it makes sense to track them, atleast the data)
			copyAssets(pipeline, context, input);
			// External Assets
			copyExternalAssets(pipeline, context, input);
		});
		// Optionally sort copied JSON files
		if (GitCraft.getDataConfiguration().sortJsonObjects()) {
			MiscHelper.executeTimedStep("Sorting JSON files...", () -> {
				// Sort them
				sortJSONFiles(context.repository());
			});
		}
		// Commit
		MiscHelper.executeTimedStep("Committing files to repo...", () -> createCommit(context.targetVersion(), context.repository()));
		MiscHelper.println("Committed %s to the repository! (Target Branch is %s)", context.targetVersion().launcherFriendlyVersionName(), target_branch.orElseThrow() + (GitCraft.versionGraph.isOnMainBranch(context.targetVersion()) ? "" : " (non-linear)"));

		// Create branch for linear version
		if (GitCraft.getRepositoryConfiguration().createVersionBranches() && GitCraft.versionGraph.isOnMainBranch(context.targetVersion())) {
			MiscHelper.executeTimedStep("Creating branch for linear version...", () -> createBranchFromCurrentCommit(context.targetVersion(), context.repository()));
			MiscHelper.println("Created branch for linear version %s", context.targetVersion().launcherFriendlyVersionName());
		}

		// Create branch for stable linear version
		if (GitCraft.getRepositoryConfiguration().createStableVersionBranches() && !GitCraft.getRepositoryConfiguration().createVersionBranches() && !context.targetVersion().isSnapshotOrPending()) {
			MiscHelper.executeTimedStep("Creating branch for stable linear version...", () -> createBranchFromCurrentCommit(context.targetVersion(), context.repository()));
			MiscHelper.println("Created branch for stable linear version %s", context.targetVersion().launcherFriendlyVersionName());
		}

		return StepOutput.ofEmptyResultSet(StepStatus.SUCCESS);
	}

	public record Inputs(Optional<StorageKey> decompiledMerged,
						 Optional<StorageKey> decompiledClientOnly,
						 Optional<StorageKey> decompiledServerOnly,
						 Optional<StorageKey> serverZip,
						 Optional<StorageKey> assetsDataJar,
						 Optional<StorageKey> datagenArtifactsReportsJar,
						 Optional<StorageKey> datagenExperimentalVanillaDatapack,
						 Optional<StorageKey> datagenArtifactsSnbtJar,
						 Optional<StorageKey> assetsIndexPath,
						 Optional<StorageKey> assetsObjectStore
	) implements StepInput {
	}

	private String getBranchNameForVersion(OrderedVersion mcVersion) {
		OrderedVersion branch = GitCraft.versionGraph.walkToPreviousBranchPoint(mcVersion);
		OrderedVersion root = GitCraft.versionGraph.walkToRoot(mcVersion);
		Set<OrderedVersion> roots = GitCraft.versionGraph.getRootVersions();
		return (branch == null
			? roots.size() == 1 || root == GitCraft.versionGraph.getDeepestRootVersion()
			? GitCraft.getRepositoryConfiguration().gitMainlineLinearBranch()
			: root.launcherFriendlyVersionName()
			: branch.launcherFriendlyVersionName()).replace(" ", "-");
	}

	private Optional<String> switchBranchIfNeeded(OrderedVersion mcVersion, AbstractVersionGraph<OrderedVersion> versionGraph, RepoWrapper repo) throws IOException, GitAPIException {
		String target_branch;
		if (repo.getGit().getRepository().resolve(Constants.HEAD) != null) { // Don't run on empty repo
			NavigableSet<OrderedVersion> prev_version = new TreeSet<>(versionGraph.getPreviousVertices(mcVersion));
			target_branch = getBranchNameForVersion(mcVersion);
			if (versionGraph.getRootVersions().contains(mcVersion)) {
				if (repo.doesBranchExist(target_branch)) {
					MiscHelper.panic("HEAD is not empty and the target branch already exists, but the current version is the root version, and should be one initial commit");
				}
				repo.checkoutNewOrphanBranch(target_branch);
				return Optional.of(target_branch);
			} else if (prev_version.isEmpty()) {
				MiscHelper.panic("HEAD is not empty, but current version does not have any preceding versions and is not a root version");
			} else {
				checkoutVersionBranch(target_branch, mcVersion, versionGraph, repo);
			}
			if (repo.existsRevWithCommitMessage(mcVersion.toCommitMessage())) {
				MiscHelper.println("Version %s already exists in repo, skipping", mcVersion.launcherFriendlyVersionName());
				return Optional.empty();
			}
			Optional<RevCommit> tip_commit = StreamSupport.stream(repo.getGit().log().setMaxCount(1).call().spliterator(), false).findFirst();
			if (tip_commit.isEmpty()) {
				MiscHelper.panic("HEAD is not empty, but a root commit can not be found");
			}
			Optional<OrderedVersion> branchedPrevVersion = prev_version.stream().filter(anyPrevVersion -> Objects.equals(tip_commit.get().getFullMessage(), anyPrevVersion.toCommitMessage())).findAny();
			if (branchedPrevVersion.isEmpty()) {
				MiscHelper.panic("This repository is wrongly ordered. Please remove the unordered commits or delete the entire repository");
			}
			prev_version.remove(branchedPrevVersion.orElseThrow());
			// Write MERGE_HEAD
			HashSet<RevCommit> mergeHeadRevs = new HashSet<>();
			for (OrderedVersion prevVersion : prev_version) {
				mergeHeadRevs.add(repo.findRevByCommitMessage(prevVersion.toCommitMessage()));
			}
			repo.writeMERGE_HEAD(mergeHeadRevs);
		} else {
			if (!versionGraph.getRootVersions().contains(mcVersion)) {
				MiscHelper.panic("A non-root version is committed as the root commit to the repository");
			}
			target_branch = GitCraft.getRepositoryConfiguration().gitMainlineLinearBranch();
		}
		return Optional.of(target_branch);
	}

	private void checkoutVersionBranch(String target_branch, OrderedVersion mcVersion, AbstractVersionGraph<OrderedVersion> versionGraph, RepoWrapper repo) throws IOException, GitAPIException {
		if (!Objects.equals(repo.getGit().getRepository().getBranch(), target_branch)) {
			Ref target_ref = repo.getGit().getRepository().getRefDatabase().findRef(target_branch);
			if (target_ref == null) {
				HashSet<RevCommit> branchPoint = findBaseForNonLinearVersion(mcVersion, versionGraph, repo);
				if (branchPoint.isEmpty()) {
					MiscHelper.panic("Could not find any branching point for non-linear version: %s (%s)", mcVersion.launcherFriendlyVersionName(), mcVersion.semanticVersion());
				}
				target_ref = repo.getGit().branchCreate().setStartPoint(branchPoint.stream().findFirst().orElseThrow()).setName(target_branch).call();
			}
			repo.switchHEAD(target_ref);
		}
	}

	private HashSet<RevCommit> findBaseForNonLinearVersion(OrderedVersion mcVersion, AbstractVersionGraph<OrderedVersion> versionGraph, RepoWrapper repo) throws IOException, GitAPIException {
		NavigableSet<OrderedVersion> previousVersion = versionGraph.getPreviousVertices(mcVersion);
		if (previousVersion.isEmpty()) {
			MiscHelper.panic("Cannot commit non-linear version %s, no base version was not found", mcVersion.launcherFriendlyVersionName());
		}

		HashSet<RevCommit> resultRevs = new HashSet<>();
		for (OrderedVersion prevVersion : previousVersion) {
			resultRevs.add(repo.findRevByCommitMessage(prevVersion.toCommitMessage()));
		}
		return resultRevs;
	}

	private void copyCode(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, Committer.Inputs input) throws IOException {
		RepoWrapper repo = context.repository();
		if (input.decompiledMerged().isPresent()) {
			try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(pipeline.getStoragePath(input.decompiledMerged().orElseThrow(), context))) {
				MiscHelper.copyLargeDir(fs.get().getPath("."), repo.getRootPath().resolve("minecraft").resolve("src"));
			}
			return;
		}
		if (input.decompiledClientOnly().isPresent()) {
			try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(pipeline.getStoragePath(input.decompiledClientOnly().orElseThrow(), context))) {
				MiscHelper.copyLargeDir(fs.get().getPath("."), repo.getRootPath().resolve("minecraft").resolve("client"));
			}
		}
		if (input.decompiledServerOnly().isPresent()) {
			try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(pipeline.getStoragePath(input.decompiledServerOnly().orElseThrow(), context))) {
				MiscHelper.copyLargeDir(fs.get().getPath("."), repo.getRootPath().resolve("minecraft").resolve("server"));
			}
		}
		if (input.decompiledClientOnly().isEmpty() && input.decompiledServerOnly().isEmpty()) {
			MiscHelper.panic("A decompiled JAR for version %s does not exist", context.targetVersion().launcherFriendlyVersionName());
		}
	}

	private void copyAssets(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, Committer.Inputs input) throws IOException {
		RepoWrapper repo = context.repository();
		if (GitCraft.getDataConfiguration().loadAssets() || GitCraft.getDataConfiguration().loadIntegratedDatapack()) {
			if (input.serverZip().isPresent()) {
				Path artifactRootPath = pipeline.getStoragePath(input.serverZip().orElseThrow(), context);

				try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(artifactRootPath)) {
					for (Path rootPath : fs.get().getRootDirectories()) {
						MiscHelper.copyLargeDirExcept(rootPath, repo.getRootPath().resolve("server-info"), List.of(rootPath.resolve(ArtifactsUnpacker.SERVER_ZIP_JAR_NAME)));
					}
				}
			}
			if (input.assetsDataJar().isPresent()) {
				try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(pipeline.getStoragePath(input.assetsDataJar().orElseThrow(), context))) {
					if (GitCraft.getDataConfiguration().loadAssets()) {
						Path assetsSrcPath = fs.get().getPath("assets");
						if (Files.exists(assetsSrcPath)) {
							MiscHelper.copyLargeDir(fs.get().getPath("assets"), repo.getRootPath().resolve("minecraft").resolve("resources").resolve("assets"));
						} else {
							// Copy old (unstructured) assets
							for (Path rootPath : fs.get().getRootDirectories()) {
								MiscHelper.copyLargeDirExceptNoFileExt(rootPath, repo.getRootPath().resolve("minecraft").resolve("resources").resolve("assets"), List.of(rootPath.resolve("META-INF")), Set.of("class"));
							}
						}
					}
					if (GitCraft.getDataConfiguration().loadIntegratedDatapack()) {
						Path dataSrcPath = fs.get().getPath("data");
						if (Files.exists(dataSrcPath)) {
							MiscHelper.copyLargeDir(fs.get().getPath("data"), repo.getRootPath().resolve("minecraft").resolve("resources").resolve("data"));
						}
					}
				}
			}
		}
		if (GitCraft.getDataConfiguration().loadDatagenRegistry() || (GitCraft.getDataConfiguration().readableNbt() && GitCraft.getDataConfiguration().loadIntegratedDatapack())) {
			if (GitCraft.getDataConfiguration().loadDatagenRegistry() && input.datagenArtifactsReportsJar().isPresent()) {
				Path datagenReportsArchive = pipeline.getStoragePath(input.datagenArtifactsReportsJar().orElseThrow(), context);
				try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(datagenReportsArchive)) {
					MiscHelper.copyLargeDir(fs.getPath("reports"), repo.getRootPath().resolve("minecraft").resolve("resources").resolve("datagen-reports"));
				}
				if (input.datagenExperimentalVanillaDatapack().isPresent()) {
					Path experimentalWorldgenPackPath = pipeline.getStoragePath(input.datagenExperimentalVanillaDatapack().orElseThrow(), context);
					try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(experimentalWorldgenPackPath)) {
						MiscHelper.copyLargeDir(fs.get().getPath("."), repo.getRootPath().resolve("minecraft").resolve("resources").resolve("exp-vanilla-worldgen"));
					}
				}
			}
			if (GitCraft.getDataConfiguration().readableNbt() && GitCraft.getDataConfiguration().loadIntegratedDatapack() && input.datagenArtifactsSnbtJar().isPresent()) {
				Path datagenSnbtArchive = pipeline.getStoragePath(input.datagenArtifactsSnbtJar().orElseThrow(), context);
				try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(datagenSnbtArchive)) {
					MiscHelper.copyLargeDir(fs.getPath("data"), repo.getRootPath().resolve("minecraft").resolve("resources").resolve("datagen-snbt"));
				}
			}
		}
	}

	private void sortJSONFiles(final RepoWrapper repo) throws IOException {
		final List<Path> jsonFiles = MiscHelper.listRecursivelyFilteredExtension(repo.getRootPath(), ".json");
		for (final Path jsonFile : jsonFiles) {
			try {
				SerializationHelper.sortJSONFile(jsonFile);
			} catch (final JsonSyntaxException e) {
				MiscHelper.println("WARNING: File %s cannot be sorted, skipping...", jsonFile);
			}
		}
	}

	private void copyExternalAssets(Pipeline<OrderedVersion> pipeline, Context<OrderedVersion> context, Committer.Inputs input) throws IOException {
		if (GitCraft.getDataConfiguration().loadAssets() && GitCraft.getDataConfiguration().loadAssetsExtern()) {
			if (input.assetsIndexPath().isEmpty() || input.assetsObjectStore().isEmpty()) {
				MiscHelper.panic("Assets for version %s do not exist", context.targetVersion().launcherFriendlyVersionName());
			}
			Path assetsIndexPath = pipeline.getStoragePath(input.assetsIndexPath().orElseThrow(), context);
			Path artifactObjectStore = pipeline.getStoragePath(input.assetsObjectStore().orElseThrow(), context);

			AssetsIndex assetsIndex = AssetsIndex.from(SerializationHelper.deserialize(SerializationHelper.fetchAllFromPath(assetsIndexPath), AssetsIndexMetadata.class));
			// Copy Assets
			Path targetRoot = context.repository().getRootPath().resolve("minecraft").resolve("external-resources").resolve("assets");
			if (Library.CONF_GLOBAL.useHardlinks() && artifactObjectStore.getFileSystem().equals(targetRoot.getFileSystem()) && !GitCraft.getDataConfiguration().sortJsonObjects()) {
				for (Map.Entry<String, AssetsIndexMetadata.Asset> entry : assetsIndex.assetsIndex().objects().entrySet()) {
					Path sourcePath = artifactObjectStore.resolve(entry.getValue().hash());
					Path targetPath = targetRoot.resolve(entry.getKey());
					Files.createDirectories(targetPath.getParent());
					Files.createLink(targetPath, sourcePath);
				}
			} else {
				for (Map.Entry<String, AssetsIndexMetadata.Asset> entry : assetsIndex.assetsIndex().objects().entrySet()) {
					Path sourcePath = artifactObjectStore.resolve(entry.getValue().hash());
					Path targetPath = targetRoot.resolve(entry.getKey());
					Files.createDirectories(targetPath.getParent());
					Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

	private void createCommit(OrderedVersion mcVersion, RepoWrapper repo) throws GitAPIException {
		repo.createCommitUsingAllChanges(GitCraft.getRepositoryConfiguration().gitUser(), GitCraft.getRepositoryConfiguration().gitMail(), new Date(Objects.requireNonNull(mcVersion.timestamp()).toInstant().toEpochMilli()), TimeZone.getTimeZone(Objects.requireNonNull(mcVersion.timestamp()).getZone()), mcVersion.toCommitMessage());
	}

	private void createBranchFromCurrentCommit(OrderedVersion mcVersion, RepoWrapper repo) throws GitAPIException, IOException {
		repo.createBranchFromCurrentCommit(mcVersion.launcherFriendlyVersionName().replace(" ", "-"));
	}
}
