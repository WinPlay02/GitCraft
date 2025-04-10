package com.github.winplay02.gitcraft.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import java.util.Objects;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RepoWrapper;

public record Resetter(Step step, Config config) implements StepWorker {

	@Override
	public StepStatus run(Pipeline pipeline, Context context) throws Exception {
		if (GitCraft.resetVersionGraph.containsVersion(context.minecraftVersion())) { // only reset version artifacts of versions which are specified
			// delete all mc jars other than the initial artifacts
			// artifacts won't ever be different, so those mc jars can stay
			for (Step step : Step.values()) {
				if (step == Step.FETCH_ARTIFACTS || step == Step.UNPACK_ARTIFACTS) {
					continue;
				}
				for (MinecraftJar minecraftJar : MinecraftJar.values()) {
					StepResult file = step.getMinecraftJar(minecraftJar);
					if (file != null) {
						Path path = step.getResultFile(file, context);
						if (Files.exists(path)) {
							Files.delete(path);
						}
					}
				}
			}
		}
		if (context.repository() == null) { // do not refresh repo, when no-repo is set
			return StepStatus.SUCCESS;
		}
		if (isHeadless(context.repository())) {
			return StepStatus.SUCCESS;
		}
		// Always refresh repo, if any refresh flag is set
		// delete all non-main refs that contain this commit (including remotes, tags, ...)
		RevCommit commitToRemove = Committer.findVersionObjectRev(context.minecraftVersion(), context.repository());
		if (commitToRemove != null) {
			checkoutBranch(context.repository(), GitCraft.config.gitMainlineLinearBranch);
			for (Ref ref : getRefsContainingCommit(context.repository(), commitToRemove)) {
				if (!ref.getName().equals(Constants.R_HEADS + GitCraft.config.gitMainlineLinearBranch) && !ref.getName().equals(Constants.HEAD)) {
					deleteRef(context.repository(), ref.getName());
				}
			}
		}
		if (context.versionGraph().getRootVersions().contains(context.minecraftVersion())) { // if root node is about to be refreshed, delete main branch and HEAD
			deleteRef(context.repository(), Constants.HEAD);
			deleteRef(context.repository(), GitCraft.config.gitMainlineLinearBranch);
			createSymbolicHEAD(Constants.R_HEADS + GitCraft.config.gitMainlineLinearBranch, context.repository());
			return StepStatus.SUCCESS;
		}
		if (GitCraft.resetVersionGraph.getRootVersions().contains(context.minecraftVersion())) { // Min-Version reached
			NavigableSet<OrderedVersion> previousVersions = context.versionGraph().getPreviousNodes(context.minecraftVersion());
			if (previousVersions.isEmpty() || previousVersions.stream().noneMatch(GitCraft.versionGraph::isOnMainBranch)) {
				MiscHelper.panic("Previous mainline version for '%s' does not exist, but it is not a root node. This should never happen", context.minecraftVersion());
			}
			OrderedVersion newMainlineRoot = previousVersions.stream().filter(GitCraft.versionGraph::isOnMainBranch).findFirst().orElseThrow();
			RevCommit commit = Committer.findVersionObjectRev(newMainlineRoot, context.repository());
			resetRef(context.repository(), GitCraft.config.gitMainlineLinearBranch, commit);
		}
		return StepStatus.SUCCESS;
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
			Committer.switchHEAD(target_ref, repo);
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
		try (RevWalk walk = new RevWalk(repo.getGit().getRepository())) {
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
					walk.setRevFilter(new Committer.CommitRevFilter(targetCommit.getId()));
					if (walk.iterator().hasNext()) {
						refsContaining.add(ref);
					}
				}
				walk.reset();
			}
			return refsContaining;
		}
	}

	protected void createSymbolicHEAD(String refTarget, RepoWrapper repo) throws IOException {
		RefUpdate refUpdate = repo.getGit().getRepository().getRefDatabase().newUpdate(Constants.HEAD, false);
		RefUpdate.Result result = refUpdate.link(refTarget);
		if (result != RefUpdate.Result.FORCED && result != RefUpdate.Result.NEW) {
			MiscHelper.panic("Unsuccessfully created symbolic HEAD to %s, result was: %s", refTarget, result);
		}
	}
}
