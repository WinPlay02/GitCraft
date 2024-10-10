package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.NavigableSet;

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
		if (context.repository().isHeadless()) {
			return StepStatus.SUCCESS;
		}
		// Always refresh repo, if any refresh flag is set
		// delete all non-main refs that contain this commit (including remotes, tags, ...)
		RevCommit commitToRemove = context.repository().findVersionObjectRev(context.minecraftVersion());
		if (commitToRemove != null) {
			context.repository().checkoutBranch(GitCraft.config.gitMainlineLinearBranch);
			for (Ref ref : context.repository().getRefsContainingCommit(commitToRemove)) {
				if (!ref.getName().equals(Constants.R_HEADS + GitCraft.config.gitMainlineLinearBranch) && !ref.getName().equals(Constants.HEAD)) {
					context.repository().deleteRef(ref.getName());
				}
			}
		}
		if (context.versionGraph().getRootVersions().contains(context.minecraftVersion())) { // if root node is about to be refreshed, delete main branch and HEAD
			context.repository().deleteRef(Constants.HEAD);
			context.repository().deleteRef(GitCraft.config.gitMainlineLinearBranch);
			context.repository().createSymbolicHEAD(Constants.R_HEADS + GitCraft.config.gitMainlineLinearBranch);
			return StepStatus.SUCCESS;
		}
		if (GitCraft.resetVersionGraph.getRootVersions().contains(context.minecraftVersion())) { // Min-Version reached
			NavigableSet<OrderedVersion> previousVersions = context.versionGraph().getPreviousNodes(context.minecraftVersion());
			if (previousVersions.isEmpty() || previousVersions.stream().noneMatch(GitCraft.versionGraph::isOnMainBranch)) {
				MiscHelper.panic("Previous mainline version for '%s' does not exist, but it is not a root node. This should never happen", context.minecraftVersion());
			}
			OrderedVersion newMainlineRoot = previousVersions.stream().filter(GitCraft.versionGraph::isOnMainBranch).findFirst().orElseThrow();
			RevCommit commit = context.repository().findVersionObjectRev(newMainlineRoot);
			context.repository().resetRef(GitCraft.config.gitMainlineLinearBranch, commit);
		}
		return StepStatus.SUCCESS;
	}
}
