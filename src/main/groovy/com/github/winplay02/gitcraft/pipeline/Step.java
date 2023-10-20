package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.types.AssetsIndex;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class Step {

	protected static final String STEP_RESET = "Reset";

	protected static final String STEP_FETCH_ARTIFACTS = "Fetch Artifacts";

	protected static final String STEP_FETCH_ASSETS = "Fetch Assets";

	protected static final String STEP_FETCH_LIBRARIES = "Fetch Libraries";

	protected static final String STEP_MERGE = "Merge";

	protected static final String STEP_PREPARE_MAPPINGS = "Prepare Mappings";

	protected static final String STEP_REMAP = "Remap";

	protected static final String STEP_DECOMPILE = "Decompile";

	protected static final String STEP_COMMIT = "Commit";

	public enum StepResult implements Comparator<StepResult> {

		NOT_RUN,
		UP_TO_DATE,
		SUCCESS,
		FAILED;

		public static StepResult merge(StepResult... results) {
			return Arrays.stream(results).max(StepResult::compareTo).orElse(NOT_RUN);
		}

		public static StepResult merge(Collection<StepResult> results) {
			return results.stream().max(StepResult::compareTo).orElse(NOT_RUN);
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

	public boolean preconditionsShouldRun(PipelineCache pipelineCache, OrderedVersion mcVersion, MappingFlavour mappingFlavour) {
		return true; // TODO check if version is already in repo, and return false, if so
	}

	public abstract StepResult run(PipelineCache pipelineCache, OrderedVersion mcVersion, MappingFlavour mappingFlavour) throws Exception;

	public static void executePipeline(List<Step> steps, OrderedVersion mcVersion, MappingFlavour mappingFlavour) {
		PipelineCache pipelineCache = new PipelineCache();
		for (Step step : steps) {
			Exception cachedException = null;
			StepResult result;
			try {
				if (step.preconditionsShouldRun(pipelineCache, mcVersion, mappingFlavour)) {
					if (step.ignoresMappings()) {
						MiscHelper.println("Performing step '%s' for version %s...", step.getName(), mcVersion.launcherFriendlyVersionName());
					} else {
						MiscHelper.println("Performing step '%s' for version %s (%s mappings)...", step.getName(), mcVersion.launcherFriendlyVersionName(), mappingFlavour);
					}
					result = step.run(pipelineCache, mcVersion, mappingFlavour);
				} else {
					result = StepResult.NOT_RUN;
				}
			} catch (Exception e) {
				cachedException = e;
				result = StepResult.FAILED;
			}
			if (step.ignoresMappings()) {
				switch (result) {
					case SUCCESS ->
							MiscHelper.println("\tStep '%s' for version %s \u001B[32msucceeded\u001B[0m", step.getName(), mcVersion.launcherFriendlyVersionName());
					case UP_TO_DATE ->
							MiscHelper.println("\tStep '%s' for version %s was \u001B[32malready up-to-date\u001B[0m", step.getName(), mcVersion.launcherFriendlyVersionName());
					case NOT_RUN ->
							MiscHelper.println("Step '%s' for version %s was \u001B[36mnot run\u001B[0m", step.getName(), mcVersion.launcherFriendlyVersionName());
					case FAILED ->
							MiscHelper.println("\tStep '%s' for version %s \u001B[31mfailed\u001B[0m", step.getName(), mcVersion.launcherFriendlyVersionName());
				}
			} else {
				switch (result) {
					case SUCCESS ->
							MiscHelper.println("\tStep '%s' for version %s (%s mappings) \u001B[32msucceeded\u001B[0m", step.getName(), mcVersion.launcherFriendlyVersionName(), mappingFlavour);
					case UP_TO_DATE ->
							MiscHelper.println("\tStep '%s' for version %s (%s mappings) was \u001B[32malready up-to-date\u001B[0m", step.getName(), mcVersion.launcherFriendlyVersionName(), mappingFlavour);
					case NOT_RUN ->
							MiscHelper.println("Step '%s' for version %s (%s mappings) was \u001B[36mnot run\u001B[0m", step.getName(), mcVersion.launcherFriendlyVersionName(), mappingFlavour);
					case FAILED ->
							MiscHelper.println("\tStep '%s' for version %s (%s mappings) \u001B[31mfailed\u001B[0m", step.getName(), mcVersion.launcherFriendlyVersionName(), mappingFlavour);
				}
			}
			if (result == StepResult.FAILED) {
				if (cachedException != null) {
					if (step.ignoresMappings()) {
						MiscHelper.panicBecause(cachedException, "\tStep '%s' for version %s \u001B[31mfailed\u001B[0m", step.getName(), mcVersion.launcherFriendlyVersionName());
					} else {
						MiscHelper.panicBecause(cachedException, "\tStep '%s' for version %s (%s mappings) \u001B[31mfailed\u001B[0m", step.getName(), mcVersion.launcherFriendlyVersionName(), mappingFlavour);
					}
				}
			} else if (result != StepResult.NOT_RUN) {
				pipelineCache.putPath(step, step.getArtifactPath(mcVersion, mappingFlavour).orElse(null));
			}
		}
	}
}
