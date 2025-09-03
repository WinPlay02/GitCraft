package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.graph.AbstractVersion;
import com.github.winplay02.gitcraft.graph.AbstractVersionGraph;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;

import com.github.winplay02.gitcraft.exceptions.ExceptionsFlavour;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.nests.NestsFlavour;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.signatures.SignaturesFlavour;
import com.github.winplay02.gitcraft.unpick.UnpickFlavour;
import com.github.winplay02.gitcraft.util.RepoWrapper;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public interface StepWorker<T extends AbstractVersion<T>, S extends StepInput> {

	Config config();

	StepOutput<T> run(Pipeline<T> pipeline, Context<T> context, S input, StepResults<T> results) throws Exception;

	default StepOutput<T> runGeneric(Pipeline<T> pipeline, Context<T> context, StepInput input, StepResults<T> results) throws Exception {
		@SuppressWarnings("unchecked")
		S castInput = (S) input;
		return this.run(pipeline, context, castInput, results);
	}

	default boolean shouldExecute(Pipeline<T> pipeline, Context<T> context) {
		return true;
	}

	record Config(Map<MinecraftJar, String> identifier, MappingFlavour mappingFlavour, UnpickFlavour unpickFlavour, ExceptionsFlavour exceptionsFlavour, SignaturesFlavour signaturesFlavour, NestsFlavour nestsFlavour, boolean lvtPatch, boolean preen) {

		public enum FlavourMatcher {
			MAPPING, UNPICK, EXCEPTIONS, SIGNATURES, NESTS, LVT, PREEN;
		}

		@Override
		public String toString() {
			List<String> flavours = new ArrayList<>();
			/*if (mappingFlavour != MappingFlavour.IDENTITY_UNMAPPED)*/ flavours.add("mappings: %s".formatted(mappingFlavour)); // prevent empty config information
			if (unpickFlavour != UnpickFlavour.NONE) flavours.add("unpick: %s".formatted(unpickFlavour));
			if (exceptionsFlavour != ExceptionsFlavour.NONE) flavours.add("exceptions: %s".formatted(exceptionsFlavour));
			if (signaturesFlavour != SignaturesFlavour.NONE) flavours.add("signatures: %s".formatted(signaturesFlavour));
			if (nestsFlavour != NestsFlavour.NONE) flavours.add("nests: %s".formatted(nestsFlavour));
			if (lvtPatch) flavours.add("lvt-patch");
			if (preen) flavours.add("preen");
			return String.join(", ", flavours);
		}

		public String createArtifactComponentString(MinecraftJar dist, FlavourMatcher... matchingFlavours) {
			Map<String, String> flavours = new LinkedHashMap<>();
			if (matchingFlavours.length != 0) {
				EnumSet<FlavourMatcher> flavoursSet = EnumSet.copyOf(Arrays.stream(matchingFlavours).toList());
				if (flavoursSet.contains(FlavourMatcher.MAPPING)) flavours.put("map", String.valueOf(mappingFlavour));
				if (flavoursSet.contains(FlavourMatcher.UNPICK)) flavours.put("un", String.valueOf(unpickFlavour));
				if (flavoursSet.contains(FlavourMatcher.EXCEPTIONS)) flavours.put("exc", String.valueOf(exceptionsFlavour));
				if (flavoursSet.contains(FlavourMatcher.SIGNATURES)) flavours.put("sig", String.valueOf(signaturesFlavour));
				if (flavoursSet.contains(FlavourMatcher.NESTS)) flavours.put("nests", String.valueOf(nestsFlavour));
				if (flavoursSet.contains(FlavourMatcher.LVT) && this.lvtPatch()) flavours.put("lvt", "patch");
				if (flavoursSet.contains(FlavourMatcher.PREEN) && this.preen()) flavours.put("preen", "1");
			}
			flavours.put("id", this.identifier().get(dist));
			return flavours.entrySet().stream().map(entry -> String.format("%s_%s", entry.getKey(), entry.getValue())).collect(Collectors.joining("-"));
		}
	}

	record Context<T extends AbstractVersion<T>>(RepoWrapper repository, AbstractVersionGraph<T> versionGraph, T targetVersion, ExecutorService executorService) {

		public Context<T> withDifferingVersion(T targetVersion) {
			return new Context<>(repository, versionGraph, targetVersion, executorService);
		}

		@Override
		public String toString() {
			return "version: %s".formatted(targetVersion.friendlyVersion());
		}
	}
}
