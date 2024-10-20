package com.github.winplay02.gitcraft.mappings;

import com.github.winplay02.gitcraft.mappings.yarn.YarnMappings;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.mappingio.MappingVisitor;

import java.io.IOException;
import java.nio.file.Path;

public class MojangPlusYarnMappings extends Mapping {
	protected MojangMappings mojangMappings;
	protected YarnMappings yarnMappings;

	public MojangPlusYarnMappings(MojangMappings mojangMappings, YarnMappings yarnMappings) {
		this.mojangMappings = mojangMappings;
		this.yarnMappings = yarnMappings;
	}

	@Override
	public String getName() {
		return "Mojang+Yarn";
	}

	@Override
	public String getDestinationNS() {
		return MappingsNamespace.NAMED.toString();
	}

	@Override
	public boolean doMappingsExist(OrderedVersion mcVersion) {
		return this.mojangMappings.doMappingsExist(mcVersion) && this.yarnMappings.doMappingsExist(mcVersion);
	}

	@Override
	public boolean doMappingsExist(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return this.mojangMappings.doMappingsExist(mcVersion, minecraftJar) && this.yarnMappings.doMappingsExist(mcVersion, minecraftJar);
	}

	@Override
	public boolean canMappingsBeUsedOn(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return this.mojangMappings.canMappingsBeUsedOn(mcVersion, minecraftJar) && this.yarnMappings.canMappingsBeUsedOn(mcVersion, minecraftJar);
	}

	@Override
	public StepStatus provideMappings(OrderedVersion mcVersion, MinecraftJar minecraftJar) throws IOException {
		// TODO implement this and the two below
		return null;
	}

	@Override
	protected Path getMappingsPathInternal(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return null;
	}

	@Override
	public void visit(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingVisitor visitor) throws IOException {

	}
}
