package com.github.winplay02.gitcraft.mappings;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.github.winplay02.gitcraft.GitCraft;
import com.github.winplay02.gitcraft.pipeline.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.LazyValue;

import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.tinyremapper.IMappingProvider;

public enum MappingFlavour {
	MOJMAP(GitCraft.MOJANG_MAPPINGS),
	FABRIC_INTERMEDIARY(GitCraft.FABRIC_INTERMEDIARY_MAPPINGS),
	YARN(GitCraft.YARN_MAPPINGS),
	MOJMAP_PARCHMENT(GitCraft.MOJANG_PARCHMENT_MAPPINGS),
	CALAMUS_INTERMEDIARY(GitCraft.CALAMUS_INTERMEDIARY_MAPPINGS),
	FEATHER(GitCraft.FEATHER_MAPPINGS),
	IDENTITY_UNMAPPED(GitCraft.IDENTITY_UNMAPPED);

	private final LazyValue<? extends Mapping> mappingImpl;

	MappingFlavour(LazyValue<? extends Mapping> mapping) {
		this.mappingImpl = mapping;
	}

	@Override
	public String toString() {
		String s = super.toString().toLowerCase(Locale.ROOT);
		if (this == CALAMUS_INTERMEDIARY || this == FEATHER) {
			s += "_gen" + GitCraft.config.ornitheIntermediaryGeneration;
		}
		return s;
	}

	public String getName() {
		return mappingImpl.get().getName();
	}

	public boolean supportsComments() {
		return mappingImpl.get().supportsComments();
	}

	public boolean supportsConstantUnpicking() {
		return mappingImpl.get().supportsConstantUnpicking();
	}

	public boolean supportsMergingPre1_3Versions() {
		return mappingImpl.get().supportsMergingPre1_3Versions();
	}

	public boolean isMappingFileRequired() {
		return mappingImpl.get().isMappingFileRequired();
	}

	public String getSourceNS() {
		return mappingImpl.get().getSourceNS();
	}

	public String getDestinationNS() {
		return mappingImpl.get().getDestinationNS();
	}

	public boolean exists(OrderedVersion mcVersion) {
		return mappingImpl.get().doMappingsExist(mcVersion);
	}

	public boolean exists(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return mappingImpl.get().doMappingsExist(mcVersion, minecraftJar);
	}

	public boolean canBeUsedOn(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return mappingImpl.get().canMappingsBeUsedOn(mcVersion, minecraftJar);
	}

	public StepStatus provide(OrderedVersion mcVersion, MinecraftJar minecraftJar) throws IOException {
		return mappingImpl.get().provideMappings(mcVersion, minecraftJar);
	}

	public Optional<Path> getPath(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return mappingImpl.get().getMappingsPath(mcVersion, minecraftJar);
	}

	public Map<String, Path> getAdditionalInformation(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return mappingImpl.get().getAdditionalMappingInformation(mcVersion, minecraftJar);
	}

	public void visit(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingVisitor visitor) throws IOException {
		mappingImpl.get().visit(mcVersion, minecraftJar, visitor);
	}

	public IMappingProvider getProvider(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return mappingImpl.get().getMappingsProvider(mcVersion, minecraftJar);
	}

	public Path executeCustomLogic(Path previousFile, OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return mappingImpl.get().executeCustomRemappingLogic(previousFile, mcVersion, minecraftJar);
	}
}
