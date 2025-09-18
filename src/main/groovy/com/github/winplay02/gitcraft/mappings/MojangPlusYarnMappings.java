package com.github.winplay02.gitcraft.mappings;

import com.github.winplay02.gitcraft.mappings.yarn.YarnMappings;
import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemRoot;
import com.github.winplay02.gitcraft.pipeline.GitCraftPipelineFilesystemStorage;
import com.github.winplay02.gitcraft.pipeline.IStepContext;
import com.github.winplay02.gitcraft.pipeline.StepStatus;
import com.github.winplay02.gitcraft.pipeline.key.MinecraftJar;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import net.fabricmc.loom.api.mappings.layered.MappingsNamespace;
import net.fabricmc.mappingio.MappedElementKind;
import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor;
import net.fabricmc.mappingio.adapter.MappingDstNsReorder;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.format.tiny.Tiny2FileReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
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
	public boolean supportsComments() {
		return this.mojangMappings.supportsComments() || this.yarnMappings.supportsComments();
	}

	@Override
	public boolean needsPackageFixingForLaunch() {
		return false;
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
	public StepStatus provideMappings(IStepContext<?, OrderedVersion> versionContext, MinecraftJar minecraftJar) throws IOException {
		// fabric yarn is provided for the merged jar; so only allow merged
		if (minecraftJar != MinecraftJar.MERGED) {
			return StepStatus.NOT_RUN;
		}

		Path mappingsPath = getMappingsPathInternal(versionContext.targetVersion(), minecraftJar);
		if (Files.exists(mappingsPath) && validateMappings(mappingsPath)) {
			return StepStatus.UP_TO_DATE;
		}
		Files.deleteIfExists(mappingsPath);

		StepStatus status = StepStatus.merge(
			this.mojangMappings.provideMappings(versionContext, MinecraftJar.CLIENT),
			this.mojangMappings.provideMappings(versionContext, MinecraftJar.SERVER),
			this.yarnMappings.provideMappings(versionContext, MinecraftJar.MERGED)
		);
		if (status.hasRun()) {
			try (MappingWriter w = MappingWriter.create(mappingsPath, MappingFormat.TINY_2_FILE)) {
				createdMergedMappings(versionContext.targetVersion(), w);
			}
			status = StepStatus.merge(status, StepStatus.SUCCESS);
		}
		return status;
	}

	@Override
	protected Path getMappingsPathInternal(OrderedVersion mcVersion, MinecraftJar minecraftJar) {
		return GitCraftPipelineFilesystemRoot.getMappings().apply(GitCraftPipelineFilesystemStorage.DEFAULT.get().rootFilesystem()).resolve("%s-moj-yarn.tiny".formatted(mcVersion.launcherFriendlyVersionName()));
	}

	@Override
	public void visit(OrderedVersion mcVersion, MinecraftJar minecraftJar, MappingVisitor visitor) throws IOException {
		Path path = getMappingsPathInternal(mcVersion, MinecraftJar.MERGED);
		try (BufferedReader br = Files.newBufferedReader(path)) {
			Tiny2FileReader.read(br, visitor);
		}
	}

	private void createdMergedMappings(OrderedVersion mcVersion, MappingVisitor visitor) throws IOException {
		MemoryMappingTree mojmaps = new MemoryMappingTree();
		MemoryMappingTree yarn = new MemoryMappingTree();
		this.mojangMappings.visit(mcVersion, MinecraftJar.MERGED, mojmaps);
		this.yarnMappings.visit(mcVersion, MinecraftJar.MERGED, new MappingSourceNsSwitch(new MappingDstNsReorder(yarn, mojmaps.getDstNamespaces()), mojmaps.getSrcNamespace()));
		yarn.accept(new MojmapYarnMerger(mojmaps));
		mojmaps.accept(visitor);
	}

	private static class MojmapYarnMerger extends ForwardingMappingVisitor {

		protected MojmapYarnMerger(MappingVisitor next) {
			super(next);
		}

		@Override
		public void visitDstName(MappedElementKind targetKind, int namespace, String name) throws IOException {
			switch (targetKind) {
				case CLASS, FIELD, METHOD -> {
					// don't care, use mojmaps
				}
				case METHOD_ARG, METHOD_VAR -> super.visitDstName(targetKind, namespace, name);
			}
		}

		@Override
		public void visitDstDesc(MappedElementKind targetKind, int namespace, String desc) throws IOException {
			switch (targetKind) {
				case CLASS, FIELD, METHOD -> {
					// don't care, use mojmaps
				}
				case METHOD_ARG, METHOD_VAR -> super.visitDstDesc(targetKind, namespace, desc);
			}
		}
	}
}
