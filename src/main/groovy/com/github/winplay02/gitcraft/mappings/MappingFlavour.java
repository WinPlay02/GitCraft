package com.github.winplay02.gitcraft.mappings;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import com.github.winplay02.gitcraft.mappings.ornithe.CalamusIntermediaryMappings;
import com.github.winplay02.gitcraft.mappings.ornithe.FeatherMappings;
import com.github.winplay02.gitcraft.mappings.yarn.FabricIntermediaryMappings;
import com.github.winplay02.gitcraft.mappings.yarn.YarnMappings;
import com.github.winplay02.gitcraft.pipeline.MinecraftJar;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.LazyValue;

import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.tinyremapper.IMappingProvider;

public enum MappingFlavour {
	MOJMAP(LazyValue.of(MojangMappings::new)),
	FABRIC_INTERMEDIARY(LazyValue.of(FabricIntermediaryMappings::new)),
	YARN(LazyValue.of(() -> new YarnMappings((FabricIntermediaryMappings) FABRIC_INTERMEDIARY.impl.get()))),
	MOJMAP_PARCHMENT(LazyValue.of(() -> new ParchmentMappings((MojangMappings) MOJMAP.impl.get()))),
	CALAMUS_INTERMEDIARY(LazyValue.of(CalamusIntermediaryMappings::new)),
	FEATHER(LazyValue.of(FeatherMappings::new)),
	IDENTITY_UNMAPPED(LazyValue.of(IdentityMappings::new));

	private final LazyValue<? extends Mapping> impl;

	MappingFlavour(LazyValue<? extends Mapping> mapping) {
		this.impl = mapping;
	}

	@Override
	public String toString() {
		return super.toString().toLowerCase(Locale.ROOT);
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

	public boolean isFileRequired() {
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
