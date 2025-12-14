package com.github.winplay02.gitcraft.mappings;

import com.github.winplay02.gitcraft.pipeline.IStepContext;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.mappingio.MappingVisitor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Predicate;

public class MojangOrIdentityMappings extends Mapping {

	protected MojangMappings mojangMappings;
	protected IdentityMappings identityMappings;
	protected Predicate<OrderedVersion> useIdentityCondition;

	public MojangOrIdentityMappings(MojangMappings mojangMappings, IdentityMappings identityMappings, Predicate<OrderedVersion> useIdentityCondition) {
		this.mojangMappings = mojangMappings;
		this.identityMappings = identityMappings;
		this.useIdentityCondition = useIdentityCondition;
	}

	protected boolean useIdentity(OrderedVersion mcVersion) {
		return this.useIdentityCondition.test(mcVersion);
	}

	@Override
	public String getName() {
		return "Mojang Mappings";
	}

	@Override
	public boolean needsPackageFixingForLaunch() {
		return false;
	}

	@Override
	public boolean isMappingFileRequired() {
		return this.identityMappings.isMappingFileRequired() || this.mojangMappings.isMappingFileRequired();
	}

	@Override
	public String getDestinationNS() {
		return MappingsNamespace.NAMED.toString();
	}

	@Override
	public boolean doMappingsExist(OrderedVersion mcVersion) {
		return useIdentity(mcVersion) ? this.identityMappings.doMappingsExist(mcVersion) : this.mojangMappings.doMappingsExist(mcVersion);
	}

	@Override
	public boolean doMappingsExist(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return useIdentity(mcVersion) ? this.identityMappings.doMappingsExist(mcVersion, minecraftJar) : this.mojangMappings.doMappingsExist(mcVersion, minecraftJar);
	}

	@Override
	public boolean canMappingsBeUsedOn(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return useIdentity(mcVersion) ? this.identityMappings.canMappingsBeUsedOn(mcVersion, minecraftJar) : this.mojangMappings.canMappingsBeUsedOn(mcVersion, minecraftJar);
	}

	@Override
	public StepStatus provideMappings(IStepContext<?, OrderedVersion> versionContext, MinecraftJar minecraftJar) throws IOException {
		return useIdentity(versionContext.targetVersion()) ? this.identityMappings.provideMappings(versionContext, minecraftJar) : this.mojangMappings.provideMappings(versionContext, minecraftJar);
	}

	@Override
	protected Path getMappingsPathInternal(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return useIdentity(mcVersion) ? this.identityMappings.getMappingsPathInternal(mcVersion, minecraftJar) : this.mojangMappings.getMappingsPathInternal(mcVersion, minecraftJar);
	}

	@Override
	public void visit(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingVisitor visitor) throws IOException {
		if (useIdentity(mcVersion)) {
			this.identityMappings.visit(mcVersion, minecraftJar, visitor);
		} else {
			this.mojangMappings.visit(mcVersion, minecraftJar, visitor);
		}
	}

	@Override
	public Path executeCustomRemappingLogic(Path previousFile, OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return useIdentity(mcVersion) ? this.identityMappings.executeCustomRemappingLogic(previousFile, mcVersion, minecraftJar) : this.mojangMappings.executeCustomRemappingLogic(previousFile, mcVersion, minecraftJar);
	}
}
