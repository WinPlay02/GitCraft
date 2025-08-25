package com.github.winplay02.gitcraft.unpick;

import com.github.winplay02.gitcraft.mappings.MappingFlavour;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.StepWorker;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import daomephsta.unpick.api.ValidatingUnpickV3Visitor;
import daomephsta.unpick.constantmappers.datadriven.parser.v2.UnpickV2Reader;
import daomephsta.unpick.constantmappers.datadriven.parser.v3.UnpickV3Reader;
import daomephsta.unpick.impl.constantmappers.datadriven.data.Data;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public interface Unpick {

	record UnpickContext(Path unpickConstants, Path unpickDefinitions, Path unpickDescription) {
	}

	StepStatus provideUnpick(StepWorker.Context<OrderedVersion> versionContext, MinecraftJar minecraftJar) throws IOException;

	UnpickContext getContext(OrderedVersion targetVersion, MinecraftJar minecraftJar) throws IOException;

	boolean doesUnpickInformationExist(OrderedVersion mcVersion);

	MappingFlavour applicableMappingFlavour(UnpickDescriptionFile unpickDescription);

	boolean supportsUnpickRemapping(UnpickDescriptionFile unpickDescription);

	static boolean validateUnpickDefinitionsV2(Path unpickDefinitionsPath) {
		try (UnpickV2Reader r = new UnpickV2Reader(Files.newInputStream(unpickDefinitionsPath))) {
			r.accept(new UnpickV2Reader.Visitor() { });
			return true;
		} catch (IOException e) {
			return false;
		}
	}

	static boolean validateUnpickDefinitionsV3(Path unpickDefinitionsPath) {
		try (UnpickV3Reader r = new UnpickV3Reader(Files.newBufferedReader(unpickDefinitionsPath))) {
			// TODO validate
			return true;
		} catch (IOException e) {
			return false;
		}
	}
}
