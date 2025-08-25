package com.github.winplay02.gitcraft.pipeline;

import com.github.winplay02.gitcraft.graph.AbstractVersion;
import com.github.winplay02.gitcraft.graph.AbstractVersionGraph;
import java.util.ArrayList;
import java.util.List;

import com.github.winplay02.gitcraft.exceptions.ExceptionsFlavour;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.nests.NestsFlavour;
import com.github.winplay02.gitcraft.signatures.SignaturesFlavour;
import com.github.winplay02.gitcraft.unpick.UnpickFlavour;
import com.github.winplay02.gitcraft.util.RepoWrapper;

import java.util.concurrent.ExecutorService;

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

	record Config(MappingFlavour mappingFlavour, UnpickFlavour unpickFlavour, ExceptionsFlavour exceptionsFlavour, SignaturesFlavour signaturesFlavour, NestsFlavour nestsFlavour) {

		@Override
		public String toString() {
			List<String> flavours = new ArrayList<>();
			/*if (mappingFlavour != MappingFlavour.IDENTITY_UNMAPPED)*/ flavours.add("mappings: %s".formatted(mappingFlavour)); // prevent empty config information
			if (unpickFlavour != UnpickFlavour.NONE) flavours.add("unpick: %s".formatted(unpickFlavour));
			if (exceptionsFlavour != ExceptionsFlavour.NONE) flavours.add("exceptions: %s".formatted(exceptionsFlavour));
			if (signaturesFlavour != SignaturesFlavour.NONE) flavours.add("signatures: %s".formatted(signaturesFlavour));
			if (nestsFlavour != NestsFlavour.NONE) flavours.add("nests: %s".formatted(nestsFlavour));
			return String.join(", ", flavours);
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
