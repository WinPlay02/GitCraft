package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.MinecraftVersionGraph;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.types.AssetsIndex;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;
import com.github.winplay02.gitcraft.util.RepoWrapper;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public abstract class Step {

	protected static final String STEP_RESET = "Reset";

	protected static final String STEP_FETCH_ARTIFACTS = "Fetch Artifacts";

	protected static final String STEP_FETCH_ASSETS = "Fetch Assets";

	protected static final String STEP_FETCH_LIBRARIES = "Fetch Libraries";

	protected static final String STEP_DATAGEN = "Datagen";

	protected static final String STEP_MERGE = "Merge";

	protected static final String STEP_PREPARE_MAPPINGS = "Prepare Mappings";

	protected static final String STEP_REMAP = "Remap";

	protected static final String STEP_UNPICK = "Unpick";

	protected static final String STEP_DECOMPILE = "Decompile";

	protected static final String STEP_COMMIT = "Commit";

	public enum StepResult implements Comparator<StepResult> {

		NOT_RUN,
		UP_TO_DATE,
		SUCCESS,
		FAILED;

		public static StepResult merge(StepResult... results) {
			return Arrays.stream(results).filter(Objects::nonNull).max(StepResult::compareTo).orElse(NOT_RUN);
		}

		public static StepResult merge(Collection<StepResult> results) {
			return results.stream().filter(Objects::nonNull).max(StepResult::compareTo).orElse(NOT_RUN);
		}

		@Override
		public int compare(StepResult o1, StepResult o2) {
			if (o1 == null) {
				o1 = NOT_RUN;
			}
			if (o2 == null) {
				o2 = NOT_RUN;
			}
			return o2.ordinal() - o1.ordinal();
		}
	}

	public static class PipelineCache {
		protected Optional<Boolean> versionAlreadyInRepo = Optional.empty();
		protected AssetsIndex assetsIndexMeta = null;
		protected Map<String, Path> cachedPaths = new HashMap<>();

		protected void putPath(Step step, Path artifactPath) {
			if (artifactPath != null) {
				this.cachedPaths.put(step.getName(), artifactPath);
			}
		}

		protected Path getForKey(String key) {
			return this.cachedPaths.get(key);
		}

		protected void putAssetsIndex(AssetsIndex assetsIndexMeta) {
			this.assetsIndexMeta = assetsIndexMeta;
		}

		protected AssetsIndex getAssetsIndex() {
			return this.assetsIndexMeta;
		}
	}

	public abstract String getName();

	public boolean ignoresMappings() {
		return false;
	}

	public final Optional<Path> getArtifactPath(OrderedVersion mcVersion, MappingFlavour mappingFlavour) {
		return Optional.ofNullable(getInternalArtifactPath(mcVersion, mappingFlavour));
	}

	protected Path getInternalArtifactPath(OrderedVersion mcVersion, MappingFlavour mappingFlavour) {
		return null;
	}

	protected final boolean findVersionRev(OrderedVersion mcVersion, RepoWrapper repo) throws GitAPIException, IOException {
		if (repo.getGit().getRepository().resolve(Constants.HEAD) == null) {
			return false;
		}
		return repo.getGit().log().all().setRevFilter(new CommitMsgFilter(mcVersion.toCommitMessage())).call().iterator().hasNext();
	}

	public boolean preconditionsShouldRun(PipelineCache pipelineCache, OrderedVersion mcVersion, MappingFlavour mappingFlavour, MinecraftVersionGraph versionGraph, RepoWrapper repo) {
		if (repo == null) {
			return true;
		}
		if (pipelineCache.versionAlreadyInRepo.isEmpty()) {
			try {
				pipelineCache.versionAlreadyInRepo = Optional.of(findVersionRev(mcVersion, repo));
			} catch (GitAPIException | IOException e) {
				throw new RuntimeException(e);
			}
		}
		return !pipelineCache.versionAlreadyInRepo.get();
	}

	public abstract StepResult run(PipelineCache pipelineCache, OrderedVersion mcVersion, MappingFlavour mappingFlavour, MinecraftVersionGraph versionGraph, RepoWrapper repo) throws Exception;

	public static void executePipeline(List<Step> steps, OrderedVersion mcVersion, MappingFlavour mappingFlavour, MinecraftVersionGraph versionGraph, RepoWrapper repo) {
		PipelineCache pipelineCache = new PipelineCache();
		for (Step step : steps) {
			Exception cachedException = null;
			StepResult result;
			long timeStart = System.nanoTime();
			try {
				if (step.preconditionsShouldRun(pipelineCache, mcVersion, mappingFlavour, versionGraph, repo)) {
					if (step.ignoresMappings()) {
						MiscHelper.println("Performing step '%s' for version %s...", step.getName(), mcVersion.launcherFriendlyVersionName());
					} else {
						MiscHelper.println("Performing step '%s' for version %s (%s mappings)...", step.getName(), mcVersion.launcherFriendlyVersionName(), mappingFlavour);
					}
					// If mappings are ignored, force step to not use mappings (pass null)
					result = step.run(pipelineCache, mcVersion, step.ignoresMappings() ? null : mappingFlavour, versionGraph, repo);
				} else {
					result = StepResult.NOT_RUN;
				}
			} catch (Exception e) {
				cachedException = e;
				result = StepResult.FAILED;
			}
			long timeEnd = System.nanoTime();
			long delta = timeEnd - timeStart;
			Duration deltaDuration = Duration.ofNanos(delta);
			String timeInfo = String.format("elapsed: %dm %02ds", deltaDuration.toMinutes(), deltaDuration.toSecondsPart());
			if (step.ignoresMappings()) {
				switch (result) {
					case SUCCESS ->
						MiscHelper.println("\tStep '%s' for version %s \u001B[32msucceeded\u001B[0m (%s)", step.getName(), mcVersion.launcherFriendlyVersionName(), timeInfo);
					case UP_TO_DATE ->
						MiscHelper.println("\tStep '%s' for version %s was \u001B[32malready up-to-date\u001B[0m", step.getName(), mcVersion.launcherFriendlyVersionName());
					case NOT_RUN -> {
						if (GitCraft.config.printNotRunSteps) {
							MiscHelper.println("Step '%s' for version %s was \u001B[36mnot run\u001B[0m", step.getName(), mcVersion.launcherFriendlyVersionName());
						}
					}
					case FAILED ->
						MiscHelper.println("\tStep '%s' for version %s \u001B[31mfailed\u001B[0m (%s)", step.getName(), mcVersion.launcherFriendlyVersionName(), timeInfo);
				}
			} else {
				switch (result) {
					case SUCCESS ->
						MiscHelper.println("\tStep '%s' for version %s (%s mappings) \u001B[32msucceeded\u001B[0m (%s)", step.getName(), mcVersion.launcherFriendlyVersionName(), mappingFlavour, timeInfo);
					case UP_TO_DATE ->
						MiscHelper.println("\tStep '%s' for version %s (%s mappings) was \u001B[32malready up-to-date\u001B[0m", step.getName(), mcVersion.launcherFriendlyVersionName(), mappingFlavour);
					case NOT_RUN -> {
						if (GitCraft.config.printNotRunSteps) {
							MiscHelper.println("Step '%s' for version %s (%s mappings) was \u001B[36mnot run\u001B[0m", step.getName(), mcVersion.launcherFriendlyVersionName(), mappingFlavour);
						}
					}
					case FAILED ->
						MiscHelper.println("\tStep '%s' for version %s (%s mappings) \u001B[31mfailed\u001B[0m (%s)", step.getName(), mcVersion.launcherFriendlyVersionName(), mappingFlavour, timeInfo);
				}
			}
			if (result == StepResult.FAILED) {
				if (cachedException != null) {
					if (step.ignoresMappings()) {
						MiscHelper.panicBecause(cachedException, "Step '%s' for version %s \u001B[31mfailed\u001B[0m (%s)", step.getName(), mcVersion.launcherFriendlyVersionName(), timeInfo);
					} else {
						MiscHelper.panicBecause(cachedException, "Step '%s' for version %s (%s mappings) \u001B[31mfailed\u001B[0m (%s)", step.getName(), mcVersion.launcherFriendlyVersionName(), mappingFlavour, timeInfo);
					}
				}
			} else if (result != StepResult.NOT_RUN) {
				pipelineCache.putPath(step, step.getArtifactPath(mcVersion, mappingFlavour).orElse(null));
			}
		}
	}

	public static final class CommitMsgFilter extends RevFilter {
		String msg;

		public CommitMsgFilter(String msg) {
			this.msg = msg;
		}

		@Override
		public boolean include(RevWalk walker, RevCommit c) {
			return Objects.equals(c.getFullMessage(), this.msg);
		}

		@Override
		public RevFilter clone() {
			return new CommitMsgFilter(this.msg);
		}

		@Override
		public String toString() {
			return "MSG_FILTER";
		}
	}

	public static final class CommitRevFilter extends RevFilter {
		ObjectId revId;

		public CommitRevFilter(ObjectId revId) {
			this.revId = revId;
		}

		@Override
		public boolean include(RevWalk walker, RevCommit c) {
			return Objects.equals(c.getId(), this.revId);
		}

		@Override
		public RevFilter clone() {
			return new CommitRevFilter(this.revId);
		}

		@Override
		public String toString() {
			return "MSG_FILTER";
		}
	}
}
