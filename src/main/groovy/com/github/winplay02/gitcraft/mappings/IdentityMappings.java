package com.github.winplay02.gitcraft.mappings;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;

import com.github.winplay02.gitcraft.pipeline.StepWorker;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.types.OrderedVersion;

import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.mappingio.MappingVisitor;

public class IdentityMappings extends Mapping {
	@Override
	public String getName() {
		return "Identity (unmapped)";
	}

	@Override
	public boolean isMappingFileRequired() {
		return false;
	}

	@Override
	public String getDestinationNS() {
		return MappingsNamespace.NAMED.toString();
	}

	@Override
	public boolean doMappingsExist(OrderedVersion mcVersion) {
		return true;
	}

	@Override
	public boolean doMappingsExist(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return true;
	}

	@Override
	public boolean canMappingsBeUsedOn(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return true;
	}

	@Override
	public StepStatus provideMappings(StepWorker.Context<OrderedVersion> versionContext, MinecraftJar minecraftJar) throws IOException {
		return StepStatus.SUCCESS;
	}

	@Override
	protected Path getMappingsPathInternal(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return null;
	}

	@Override
	public void visit(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingVisitor visitor) throws IOException {
		visitor.visitNamespaces(getSourceNS(), Collections.emptyList());
	}

	@Override
	public Path executeCustomRemappingLogic(Path previousFile, OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return previousFile;
	}
}
