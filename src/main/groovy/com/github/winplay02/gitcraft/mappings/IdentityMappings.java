package com.github.winplay02.gitcraft.mappings;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import com.github.winplay02.gitcraft.pipeline.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.types.OrderedVersion;

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
		return this.getSourceNS();
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
	public StepStatus provideMappings(OrderedVersion mcVersion, MinecraftJar minecraftJar) throws IOException {
		return StepStatus.SUCCESS;
	}

	@Override
	protected Path getMappingsPathInternal(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return null;
	}

	@Override
	public void visit(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingVisitor visitor) throws IOException {
		visitor.visitNamespaces(getSourceNS(), List.of(getDestinationNS()));
	}

	@Override
	public Path executeCustomRemappingLogic(Path previousFile, OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return previousFile;
	}
}
