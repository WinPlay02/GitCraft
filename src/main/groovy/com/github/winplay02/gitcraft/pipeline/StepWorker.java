package com.github.winplay02.gitcraft.pipeline;

import java.util.ArrayList;
import java.util.List;

import com.github.winplay02.gitcraft.MinecraftVersionGraph;
import com.github.winplay02.gitcraft.exceptions.ExceptionsFlavour;
import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.nests.NestsFlavour;
import com.github.winplay02.gitcraft.signatures.SignaturesFlavour;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.RepoWrapper;

public interface StepWorker {

	Step step();

	Config config();

	StepStatus run(Pipeline pipeline, Context context) throws Exception;

	public record Config(ExceptionsFlavour exceptionsFlavour, SignaturesFlavour signaturesFlavour, MappingFlavour mappingFlavour, NestsFlavour nestsFlavour) {

		@Override
		public String toString() {
			List<String> flavours = new ArrayList<>();
			if (exceptionsFlavour != ExceptionsFlavour.NONE) flavours.add("exceptions: %s".formatted(exceptionsFlavour));
			if (signaturesFlavour != SignaturesFlavour.NONE) flavours.add("signatures: %s".formatted(signaturesFlavour));
			if (mappingFlavour != MappingFlavour.IDENTITY_UNMAPPED) flavours.add("mappings: %s".formatted(mappingFlavour));
			if (nestsFlavour != NestsFlavour.NONE) flavours.add("nests: %s".formatted(nestsFlavour));
			return String.join(", ", flavours);
		}
	}

	public record Context(RepoWrapper repository, MinecraftVersionGraph versionGraph, OrderedVersion minecraftVersion) {

		@Override
		public String toString() {
			return "version: %s".formatted(minecraftVersion.launcherFriendlyVersionName());
		}
	}
}
