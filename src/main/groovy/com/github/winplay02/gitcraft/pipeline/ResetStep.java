package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.MinecraftVersionGraph;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RepoWrapper;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableSet;
import java.util.Objects;

public class ResetStep extends Step {
	@Override
	public String getName() {
		return STEP_RESET;
	}

	@Override
	public boolean preconditionsShouldRun(PipelineCache pipelineCache, OrderedVersion mcVersion, MappingFlavour mappingFlavour, MinecraftVersionGraph versionGraph, RepoWrapper repo) {
		return GitCraft.config.refreshDecompilation && GitCraft.resetVersionGraph.getRootVersions().stream().map(mcVersion::compareTo).max(Comparator.naturalOrder()).orElseThrow() >= 0;
	}

	@Override
	public StepResult run(PipelineCache pipelineCache, OrderedVersion mcVersion, MappingFlavour mappingFlavour, MinecraftVersionGraph versionGraph, RepoWrapper repo) throws Exception {
		if (GitCraft.resetVersionGraph.containsVersion(mcVersion)) { // only reset version artifacts of versions which are specified
			Path remappedPath = GitCraft.STEP_REMAP.getInternalArtifactPath(mcVersion, mappingFlavour);
			Path unpickedPath = GitCraft.STEP_UNPICK.getInternalArtifactPath(mcVersion, mappingFlavour);
			Path decompiledPath = GitCraft.STEP_DECOMPILE.getInternalArtifactPath(mcVersion, mappingFlavour);
			// Datagen does not really need to be reset; The results should never change
			//Path artifactsRoot = GitCraft.STEP_FETCH_ARTIFACTS.getInternalArtifactPath(mcVersion, mappingFlavour);
			//Path datagenSnbtPath = GitCraft.STEP_DATAGEN.getDatagenSNBTArchive(artifactsRoot);
			//Path datagenReportsPath = GitCraft.STEP_DATAGEN.getDatagenReportsArchive(artifactsRoot);
			/*if (Files.exists(datagenSnbtPath)) {
				Files.delete(datagenSnbtPath);
				MiscHelper.println("%s (%s, %s, datagen SNBT) has been deleted", datagenSnbtPath, mcVersion.launcherFriendlyVersionName(), mappingFlavour);
			}
			if (Files.exists(datagenReportsPath)) {
				Files.delete(datagenReportsPath);
				MiscHelper.println("%s (%s, %s, datagen registry reports) has been deleted", datagenReportsPath, mcVersion.launcherFriendlyVersionName(), mappingFlavour);
			}*/
			if (Files.exists(remappedPath)) {
				Files.delete(remappedPath);
				MiscHelper.println("%s (%s, %s, remapped) has been deleted", remappedPath, mcVersion.launcherFriendlyVersionName(), mappingFlavour);
			}
			if (Files.exists(unpickedPath)) {
				Files.delete(unpickedPath);
				MiscHelper.println("%s (%s, %s, unpicked) has been deleted", unpickedPath, mcVersion.launcherFriendlyVersionName(), mappingFlavour);
			}
			if (Files.exists(decompiledPath)) {
				Files.delete(decompiledPath);
				MiscHelper.println("%s (%s, %s, decompiled) has been deleted", decompiledPath, mcVersion.launcherFriendlyVersionName(), mappingFlavour);
			}
		}
		if (repo == null) { // do not refresh repo, when no-repo is set
			return StepResult.SUCCESS;
		}
		if (isHeadless(repo)) {
			return StepResult.SUCCESS;
		}
		// Always refresh repo, if any refresh flag is set
		// delete all non-main refs that contain this commit (including remotes, tags, ...)
		RevCommit commitToRemove = GitCraft.STEP_COMMIT.findVersionObjectRev(mcVersion, repo);
		if (commitToRemove != null) {
			checkoutBranch(repo, GitCraft.config.gitMainlineLinearBranch);
			for (Ref ref : getRefsContainingCommit(repo, commitToRemove)) {
				if (!ref.getName().equals(Constants.R_HEADS + GitCraft.config.gitMainlineLinearBranch) && !ref.getName().equals(Constants.HEAD)) {
					deleteRef(repo, ref.getName());
				}
			}
		}
		if (versionGraph.getRootVersions().contains(mcVersion)) { // if root node is about to be refreshed, delete main branch and HEAD
			deleteRef(repo, Constants.HEAD);
			deleteRef(repo, GitCraft.config.gitMainlineLinearBranch);
			createSymbolicHEAD(Constants.R_HEADS + GitCraft.config.gitMainlineLinearBranch, repo);
			return StepResult.SUCCESS;
		}
		if (GitCraft.resetVersionGraph.getRootVersions().contains(mcVersion)) { // Min-Version reached
			NavigableSet<OrderedVersion> previousVersions = versionGraph.getPreviousNodes(mcVersion);
			if (previousVersions.isEmpty() || previousVersions.stream().noneMatch(GitCraft.versionGraph::isOnMainBranch)) {
				MiscHelper.panic("Previous mainline version for '%s' does not exist, but it is not a root node. This should never happen", mcVersion);
			}
			OrderedVersion newMainlineRoot = previousVersions.stream().filter(GitCraft.versionGraph::isOnMainBranch).findFirst().orElseThrow();
			RevCommit commit = GitCraft.STEP_COMMIT.findVersionObjectRev(newMainlineRoot, repo);
			resetRef(repo, GitCraft.config.gitMainlineLinearBranch, commit);
		}
		return StepResult.SUCCESS;
	}

	protected void deleteRef(RepoWrapper repo, String targetRefName) throws IOException {
		Ref targetRef = repo.getGit().getRepository().getRefDatabase().findRef(targetRefName);
		RefUpdate refUpdate = repo.getGit().getRepository().getRefDatabase().newUpdate(targetRef.getName(), true);
		refUpdate.setForceUpdate(true);
		RefUpdate.Result result = refUpdate.delete();
		if (result != RefUpdate.Result.FORCED) {
			MiscHelper.panic("Unsuccessfully deleted ref %s, result was: %s", targetRefName, result);
		}
	}

	protected void checkoutBranch(RepoWrapper repo, String targetBranch) throws IOException {
		if (!Objects.equals(repo.getGit().getRepository().getBranch(), targetBranch)) {
			Ref target_ref = repo.getGit().getRepository().getRefDatabase().findRef(targetBranch);
			if (target_ref == null) {
				MiscHelper.panic("Could not find target branch '%s'", targetBranch);
			}
			GitCraft.STEP_COMMIT.switchHEAD(target_ref, repo);
		}
	}

	protected void resetRef(RepoWrapper repo, String targetRefName, RevCommit targetCommit) throws IOException {
		Ref targetRef = repo.getGit().getRepository().getRefDatabase().findRef(targetRefName);
		RefUpdate refUpdate = repo.getGit().getRepository().getRefDatabase().newUpdate(targetRef.getName(), true);
		refUpdate.setNewObjectId(targetCommit);
		refUpdate.setForceUpdate(true);
		RefUpdate.Result result = refUpdate.update();
		if (result != RefUpdate.Result.FORCED && result != RefUpdate.Result.NO_CHANGE) {
			MiscHelper.panic("Unsuccessfully changed ref %s to %s, result was: %s", targetRefName, targetCommit.getId(), result);
		}
	}

	protected boolean isHeadless(RepoWrapper repo) throws IOException {
		return repo.getGit().getRepository().resolve(Constants.HEAD) == null;
	}

	protected List<Ref> getRefsContainingCommit(RepoWrapper repo, RevCommit targetCommit) throws IOException {
		List<Ref> refs = repo.getGit().getRepository().getRefDatabase().getRefs();
		List<Ref> refsContaining = new ArrayList<>();
		RevWalk walk = new RevWalk(repo.getGit().getRepository());
		for (Ref ref : refs) {
			if (!ref.isPeeled()) {
				ref = repo.getGit().getRepository().getRefDatabase().peel(ref);
			}

			ObjectId objectId = ref.getPeeledObjectId();
			if (objectId == null)
				objectId = ref.getObjectId();
			RevCommit commit = null;
			try {
				commit = walk.parseCommit(objectId);
			} catch (MissingObjectException | IncorrectObjectTypeException ignored) {
			}
			if (commit != null) {
				walk.markStart(commit);
				walk.setRevFilter(new CommitRevFilter(targetCommit.getId()));
				if (walk.iterator().hasNext()) {
					refsContaining.add(ref);
				}
			}
			walk.reset();
		}
		return refsContaining;
	}

	protected void createSymbolicHEAD(String refTarget, RepoWrapper repo) throws IOException {
		RefUpdate refUpdate = repo.getGit().getRepository().getRefDatabase().newUpdate(Constants.HEAD, false);
		RefUpdate.Result result = refUpdate.link(refTarget);
		if (result != RefUpdate.Result.FORCED && result != RefUpdate.Result.NEW) {
			MiscHelper.panic("Unsuccessfully created symbolic HEAD to %s, result was: %s", refTarget, result);
		}
	}
}
