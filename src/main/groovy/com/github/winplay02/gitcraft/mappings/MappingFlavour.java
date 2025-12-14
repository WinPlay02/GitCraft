package com.github.winplay02.gitcraft.mappings;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import com.github.winplay02.gitcraft.mappings.ornithe.CalamusIntermediaryMappings;
import com.github.winplay02.gitcraft.mappings.ornithe.FeatherMappings;
import com.github.winplay02.gitcraft.mappings.yarn.FabricIntermediaryMappings;
import com.github.winplay02.gitcraft.mappings.yarn.YarnMappings;
import com.github.winplay02.gitcraft.pipeline.IStepContext;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.LazyValue;

import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.tinyremapper.IMappingProvider;

public enum MappingFlavour {
	IDENTITY_UNMAPPED(IdentityMappings::new),
	MOJMAP_STRICT(MojangMappings::new),
	MOJMAP(() -> new MojangOrIdentityMappings((MojangMappings) MOJMAP_STRICT.impl.get(), (IdentityMappings) IDENTITY_UNMAPPED.impl.get(), OrderedVersion::isNotObfuscated)),
	FABRIC_INTERMEDIARY(FabricIntermediaryMappings::new),
	YARN(() -> new YarnMappings((FabricIntermediaryMappings) FABRIC_INTERMEDIARY.impl.get())),
	MOJMAP_PARCHMENT(() -> new ParchmentMappings((MojangMappings) MOJMAP_STRICT.impl.get())),
	CALAMUS_INTERMEDIARY(CalamusIntermediaryMappings::new),
	FEATHER(FeatherMappings::new),
	MOJMAP_YARN(() -> new MojangPlusYarnMappings((MojangMappings) MOJMAP_STRICT.impl.get(), (YarnMappings) YARN.impl.get()));

	private final LazyValue<? extends Mapping> impl;

	MappingFlavour(Supplier<? extends Mapping> mapping) {
		this.impl = LazyValue.of(mapping);
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

	public boolean needsPackageFixingForLaunch() {
		return impl.get().needsPackageFixingForLaunch();
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

	public StepStatus provide(IStepContext<?, OrderedVersion> versionContext, MinecraftJar minecraftJar) throws IOException, URISyntaxException, InterruptedException {
		return impl.get().provideMappings(versionContext, minecraftJar);
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

	protected Mapping getImpl() {
		return impl.get();
	}
}
