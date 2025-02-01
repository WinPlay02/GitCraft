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

	private final LazyValue<? extends Mapping> impl;

	MappingFlavour(LazyValue<? extends Mapping> mapping) {
		this.impl = mapping;
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
		return impl.get().getName();
	}

	public boolean supportsComments() {
		return impl.get().supportsComments();
	}

	public boolean supportsConstantUnpicking() {
		return impl.get().supportsConstantUnpicking();
	}

	public boolean supportsMergingPre1_3Versions() {
		return impl.get().supportsMergingPre1_3Versions();
	}

	public boolean isMappingFileRequired() {
		return impl.get().isMappingFileRequired();
	}

	public String getSourceNS() {
		return impl.get().getSourceNS();
	}

	public String getDestinationNS() {
		return impl.get().getDestinationNS();
	}

	public boolean exists(OrderedVersion mcVersion) {
		return impl.get().doMappingsExist(mcVersion);
	}

	public boolean exists(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return impl.get().doMappingsExist(mcVersion, minecraftJar);
	}

	public boolean canBeUsedOn(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return impl.get().canMappingsBeUsedOn(mcVersion, minecraftJar);
	}

	public StepStatus provide(OrderedVersion mcVersion, MinecraftJar minecraftJar) throws IOException {
		return impl.get().provideMappings(mcVersion, minecraftJar);
	}

	public Optional<Path> getPath(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return impl.get().getMappingsPath(mcVersion, minecraftJar);
	}

	public Map<String, Path> getAdditionalInformation(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return impl.get().getAdditionalMappingInformation(mcVersion, minecraftJar);
	}

	public void visit(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingVisitor visitor) throws IOException {
		impl.get().visit(mcVersion, minecraftJar, visitor);
	}

	public IMappingProvider getProvider(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return impl.get().getMappingsProvider(mcVersion, minecraftJar);
	}

	public Path executeCustomLogic(Path previousFile, OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return impl.get().executeCustomRemappingLogic(previousFile, mcVersion, minecraftJar);
	}
}
