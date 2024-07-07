package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.MinecraftVersionGraph;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.meta.AssetsIndexMetadata;
import com.github.winplay02.gitcraft.types.Artifact;
import com.github.winplay02.gitcraft.types.AssetsIndex;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.GitCraftPaths;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RepoWrapper;
import com.github.winplay02.gitcraft.util.SerializationHelper;
import com.google.gson.JsonSyntaxException;
import groovy.lang.Tuple2;
import net.fabricmc.loom.util.FileSystemUtil;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.stream.StreamSupport;

public class CommitStep extends Step {

	@Override
	public String getName() {
		return STEP_COMMIT;
	}

	@Override
	public boolean preconditionsShouldRun(PipelineCache pipelineCache, OrderedVersion mcVersion, MappingFlavour mappingFlavour, MinecraftVersionGraph versionGraph, RepoWrapper repo) {
		return !GitCraft.config.noRepo && super.preconditionsShouldRun(pipelineCache, mcVersion, mappingFlavour, versionGraph, repo);
	}

	@Override
	public StepResult run(PipelineCache pipelineCache, OrderedVersion mcVersion, MappingFlavour mappingFlavour, MinecraftVersionGraph versionGraph, RepoWrapper repo) throws Exception {
		// Check validity of prepared args
		Objects.requireNonNull(repo);
		// Clean First
		MiscHelper.executeTimedStep("Clearing working directory...", () -> this.clearWorkingTree(repo));
		// Switch Branch
		Optional<String> target_branch = switchBranchIfNeeded(mcVersion, versionGraph, repo);
		if (target_branch.isEmpty()) {
			return StepResult.UP_TO_DATE;
		}
		// Copy to repository
		MiscHelper.executeTimedStep("Moving files to repo...", () -> {
			// Copy decompiled MC code to repo directory
			copyCode(pipelineCache, mcVersion, repo);
			// Copy assets & data (it makes sense to track them, atleast the data)
			copyAssets(pipelineCache, mcVersion, repo);
			// External Assets
			copyExternalAssets(pipelineCache, mcVersion, repo);
		});
		// Optionally sort copied JSON files
		if (GitCraft.config.sortJsonObjects) {
			MiscHelper.executeTimedStep("Sorting JSON files...", () -> {
				// Sort them
				sortJSONFiles(repo);
			});
		}
		// Commit
		MiscHelper.executeTimedStep("Committing files to repo...", () -> createCommit(mcVersion, repo));
		MiscHelper.println("Committed %s to the repository! (Target Branch is %s)", mcVersion.launcherFriendlyVersionName(), target_branch.orElseThrow() + (GitCraft.versionGraph.isOnMainBranch(mcVersion) ? "" : " (non-linear)"));

		// Create branch for linear version
		if (GitCraft.config.createVersionBranches && GitCraft.versionGraph.isOnMainBranch(mcVersion)) {
			MiscHelper.executeTimedStep("Creating branch for linear version...", () -> createBranchFromCurrentCommit(mcVersion, repo));
			MiscHelper.println("Created branch for linear version %s", mcVersion.launcherFriendlyVersionName());
		}

		// Create branch for stable linear version
		if (GitCraft.config.createStableVersionBranches && !GitCraft.config.createVersionBranches && !mcVersion.isSnapshotOrPending()) {
			MiscHelper.executeTimedStep("Creating branch for stable linear version...", () -> createBranchFromCurrentCommit(mcVersion, repo));
			MiscHelper.println("Created branch for stable linear version %s", mcVersion.launcherFriendlyVersionName());
		}

		return StepResult.SUCCESS;
	}

	private void clearWorkingTree(RepoWrapper repo) throws IOException {
		for (Path innerPath : MiscHelper.listDirectly(repo.getRootPath())) {
			if (!innerPath.toString().endsWith(".git")) {
				if (Files.isDirectory(innerPath)) {
					MiscHelper.deleteDirectory(innerPath);
				} else {
					Files.deleteIfExists(innerPath);
				}
			}
		}
	}

	protected String getBranchNameForVersion(OrderedVersion mcVersion) {
		OrderedVersion branch = GitCraft.versionGraph.walkToPreviousBranchPoint(mcVersion);
		OrderedVersion root = GitCraft.versionGraph.walkToRoot(mcVersion);
		Set<OrderedVersion> roots = GitCraft.versionGraph.getRootVersions();
		return (branch == null
			? roots.size() == 1 || root == GitCraft.versionGraph.getDeepestRootVersion()
				? GitCraft.config.gitMainlineLinearBranch
				: root.launcherFriendlyVersionName()
			: branch.launcherFriendlyVersionName()).replace(" ", "-");
	}

	private void checkoutNewOrphanBranch(RepoWrapper repo, String target_branch) throws GitAPIException {
		repo.getGit().checkout().setOrphan(true).setName(target_branch).call();
	}

	private Optional<String> switchBranchIfNeeded(OrderedVersion mcVersion, MinecraftVersionGraph versionGraph, RepoWrapper repo) throws IOException, GitAPIException {
		String target_branch;
		if (repo.getGit().getRepository().resolve(Constants.HEAD) != null) { // Don't run on empty repo
			NavigableSet<OrderedVersion> prev_version = new TreeSet<>(versionGraph.getPreviousNodes(mcVersion));
			target_branch = getBranchNameForVersion(mcVersion);
			if (versionGraph.getRootVersions().contains(mcVersion)) {
				if (doesBranchExist(target_branch, repo)) {
					MiscHelper.panic("HEAD is not empty and the target branch already exists, but the current version is the root version, and should be one initial commit");
				}
				checkoutNewOrphanBranch(repo, target_branch);
				return Optional.of(target_branch);
			} else if (prev_version.isEmpty()) {
				MiscHelper.panic("HEAD is not empty, but current version does not have any preceding versions and is not a root version");
			} else {
				checkoutVersionBranch(target_branch, mcVersion, versionGraph, repo);
			}
			if (findVersionRev(mcVersion, repo)) {
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
				mergeHeadRevs.add(findVersionObjectRev(prevVersion, repo));
			}
			writeMERGE_HEAD(mergeHeadRevs, repo);
		} else {
			if (!versionGraph.getRootVersions().contains(mcVersion)) {
				MiscHelper.panic("A non-root version is committed as the root commit to the repository");
			}
			target_branch = GitCraft.config.gitMainlineLinearBranch;
		}
		return Optional.of(target_branch);
	}

	private boolean doesBranchExist(String target_branch, RepoWrapper repo) throws IOException {
		Ref target_ref = repo.getGit().getRepository().getRefDatabase().findRef(target_branch);
		return target_ref != null;
	}

	private void checkoutVersionBranch(String target_branch, OrderedVersion mcVersion, MinecraftVersionGraph versionGraph, RepoWrapper repo) throws IOException, GitAPIException {
		if (!Objects.equals(repo.getGit().getRepository().getBranch(), target_branch)) {
			Ref target_ref = repo.getGit().getRepository().getRefDatabase().findRef(target_branch);
			if (target_ref == null) {
				HashSet<RevCommit> branchPoint = findBaseForNonLinearVersion(mcVersion, versionGraph, repo);
				if (branchPoint.isEmpty()) {
					MiscHelper.panic("Could not find any branching point for non-linear version: %s (%s)", mcVersion.launcherFriendlyVersionName(), mcVersion.semanticVersion());
				}
				target_ref = repo.getGit().branchCreate().setStartPoint(branchPoint.stream().findFirst().orElseThrow()).setName(target_branch).call();
			}
			switchHEAD(target_ref, repo);
		}
	}

	private HashSet<RevCommit> findBaseForNonLinearVersion(OrderedVersion mcVersion, MinecraftVersionGraph versionGraph, RepoWrapper repo) throws IOException, GitAPIException {
		NavigableSet<OrderedVersion> previousVersion = versionGraph.getPreviousNodes(mcVersion);
		if (previousVersion.isEmpty()) {
			MiscHelper.panic("Cannot commit non-linear version %s, no base version was not found", mcVersion.launcherFriendlyVersionName());
		}

		HashSet<RevCommit> resultRevs = new HashSet<>();
		for (OrderedVersion prevVersion : previousVersion) {
			resultRevs.add(findVersionObjectRev(prevVersion, repo));
		}
		return resultRevs;
	}

	protected RevCommit findVersionObjectRev(OrderedVersion mcVersion, RepoWrapper repo) throws GitAPIException, IOException {
		Iterator<RevCommit> iterator = repo.getGit().log().all().setRevFilter(new CommitMsgFilter(mcVersion.toCommitMessage())).call().iterator();
		if (iterator.hasNext()) {
			return iterator.next();
		}
		return null;
	}

	protected void switchHEAD(Ref ref, RepoWrapper repo) throws IOException {
		RefUpdate refUpdate = repo.getGit().getRepository().getRefDatabase().newUpdate(Constants.HEAD, false);
		RefUpdate.Result result = refUpdate.link(ref.getName());
		if (result != RefUpdate.Result.FORCED) {
			MiscHelper.panic("Unsuccessfully changed HEAD to %s, result was: %s", ref, result);
		}
	}

	private void writeMERGE_HEAD(HashSet<RevCommit> commits, RepoWrapper repo) throws IOException {
		if (commits.isEmpty()) {
			repo.getGit().getRepository().writeMergeHeads(null);
		} else {
			repo.getGit().getRepository().writeMergeHeads(commits.stream().toList());
		}
	}

	private void copyCode(PipelineCache pipelineCache, OrderedVersion mcVersion, RepoWrapper repo) throws IOException {
		Path decompiledJarPath = pipelineCache.getForKey(Step.STEP_DECOMPILE);
		if (decompiledJarPath == null) {
			MiscHelper.panic("A decompiled JAR for version %s does not exist", mcVersion.launcherFriendlyVersionName());
		}
		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(decompiledJarPath)) {
			MiscHelper.copyLargeDir(fs.get().getPath("."), repo.getRootPath().resolve("minecraft").resolve("src"));
		}
	}

	private void copyAssets(PipelineCache pipelineCache, OrderedVersion mcVersion, RepoWrapper repo) throws IOException {
		if (GitCraft.config.loadAssets || GitCraft.config.loadIntegratedDatapack) {
			if (mcVersion.hasServerZip()) {
				Path artifactRootPath = pipelineCache.getForKey(Step.STEP_FETCH_ARTIFACTS);
				try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(mcVersion.serverDist().serverZip().resolve(artifactRootPath))) {
					for (Path rootPath : fs.get().getRootDirectories()) {
						MiscHelper.copyLargeDirExcept(rootPath, repo.getRootPath().resolve("server-info"), List.of(rootPath.resolve(RemapStep.SERVER_ZIP_JAR_NAME)));
					}
				}
			}
			Path mergedJarPath = pipelineCache.getForKey(Step.STEP_MERGE);
			if (mergedJarPath == null) { // Client JAR could also work, if merge did not happen
				if (mcVersion.hasClientCode()) {
					Path artifactRootPath = pipelineCache.getForKey(Step.STEP_FETCH_ARTIFACTS);
					mergedJarPath = mcVersion.clientJar().resolve(artifactRootPath);
				}
			}
			if (mergedJarPath != null) {
				try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(mergedJarPath)) {
					if (GitCraft.config.loadAssets) {
						Path assetsSrcPath = fs.get().getPath("assets");
						if (Files.exists(assetsSrcPath)) {
							MiscHelper.copyLargeDir(fs.get().getPath("assets"), repo.getRootPath().resolve("minecraft").resolve("resources").resolve("assets"));
						}
					}
					if (GitCraft.config.loadIntegratedDatapack) {
						Path dataSrcPath = fs.get().getPath("data");
						if (Files.exists(dataSrcPath)) {
							MiscHelper.copyLargeDir(fs.get().getPath("data"), repo.getRootPath().resolve("minecraft").resolve("resources").resolve("data"));
						}
					}
				}
			}
		}
		if (GitCraft.config.loadDatagenRegistry || (GitCraft.config.readableNbt && GitCraft.config.loadIntegratedDatapack)) {
			Path artifactsRootPath = pipelineCache.getForKey(Step.STEP_FETCH_ARTIFACTS);
			if (GitCraft.config.loadDatagenRegistry && Files.exists(GitCraft.STEP_DATAGEN.getDatagenReportsArchive(artifactsRootPath))) {
				try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(GitCraft.STEP_DATAGEN.getDatagenReportsArchive(artifactsRootPath))) {
					MiscHelper.copyLargeDir(fs.getPath("reports"), repo.getRootPath().resolve("minecraft").resolve("resources").resolve("datagen-reports"));
				}
				Tuple2<OrderedVersion, Artifact> experimentalWorldgenPack = GitCraft.STEP_DATAGEN.getExtVanillaWorldgenPack(mcVersion);
				if (experimentalWorldgenPack != null) {
					Path expWorldgenPackPath = experimentalWorldgenPack.getV2().resolve(GitCraft.STEP_FETCH_ARTIFACTS.getInternalArtifactPath(experimentalWorldgenPack.getV1(), null));
					try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(expWorldgenPackPath)) {
						MiscHelper.copyLargeDir(fs.get().getPath("."), repo.getRootPath().resolve("minecraft").resolve("resources").resolve("exp-vanilla-worldgen"));
					}
				}
			}
			if (GitCraft.config.readableNbt && GitCraft.config.loadIntegratedDatapack && Files.exists(GitCraft.STEP_DATAGEN.getDatagenSNBTArchive(artifactsRootPath))) {
				try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(GitCraft.STEP_DATAGEN.getDatagenSNBTArchive(artifactsRootPath))) {
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

	private void copyExternalAssets(PipelineCache pipelineCache, OrderedVersion mcVersion, RepoWrapper repo) throws IOException {
		if (GitCraft.config.loadAssets && GitCraft.config.loadAssetsExtern) {
			AssetsIndex assetsIndex = pipelineCache.getAssetsIndex();
			Path artifactObjectStore = pipelineCache.getForKey(STEP_FETCH_ASSETS);
			if (artifactObjectStore == null) {
				MiscHelper.panic("Assets for version %s do not exist", mcVersion.launcherFriendlyVersionName());
			}
			// Copy Assets
			Path targetRoot = repo.getRootPath().resolve("minecraft").resolve("external-resources").resolve("assets");
			if (GitCraft.config.useHardlinks && GitCraftPaths.ASSETS_OBJECTS.getFileSystem().equals(targetRoot.getFileSystem()) && !GitCraft.config.sortJsonObjects) {
				for (Map.Entry<String, AssetsIndexMetadata.Asset> entry : assetsIndex.assetsIndex().objects().entrySet()) {
					Path sourcePath = GitCraftPaths.ASSETS_OBJECTS.resolve(entry.getValue().hash());
					Path targetPath = targetRoot.resolve(entry.getKey());
					Files.createDirectories(targetPath.getParent());
					Files.createLink(targetPath, sourcePath);
				}
			} else {
				for (Map.Entry<String, AssetsIndexMetadata.Asset> entry : assetsIndex.assetsIndex().objects().entrySet()) {
					Path sourcePath = GitCraftPaths.ASSETS_OBJECTS.resolve(entry.getValue().hash());
					Path targetPath = targetRoot.resolve(entry.getKey());
					Files.createDirectories(targetPath.getParent());
					Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

	private void createCommit(OrderedVersion mcVersion, RepoWrapper repo) throws GitAPIException {
		// Remove removed files from index
		repo.getGit().add().addFilepattern(".").setRenormalize(false).setUpdate(true).call();
		// Stage new files
		repo.getGit().add().addFilepattern(".").setRenormalize(false).call();
		Date version_date = new Date(mcVersion.timestamp().toInstant().toEpochMilli());
		PersonIdent author = new PersonIdent(GitCraft.config.gitUser, GitCraft.config.gitMail, version_date, TimeZone.getTimeZone(mcVersion.timestamp().getZone()));
		repo.getGit().commit().setMessage(mcVersion.toCommitMessage()).setAuthor(author).setCommitter(author).setSign(false).call();
	}

	private void createBranchFromCurrentCommit(OrderedVersion mcVersion, RepoWrapper repo) throws GitAPIException, IOException {
		String branchName = mcVersion.launcherFriendlyVersionName().replace(" ", "-");
		try (RevWalk walk = new RevWalk(repo.getGit().getRepository())) {
			ObjectId commitId = repo.getGit().getRepository().resolve(Constants.HEAD);
			RevCommit commit = walk.parseCommit(commitId);
			repo.getGit().branchCreate().setName(branchName).setStartPoint(commit).call();
		}
	}
}
