package com.github.winplay02.gitcraft.manifest.historic;

import com.github.winplay02.gitcraft.manifest.BaseMetadataProvider;
import com.github.winplay02.gitcraft.manifest.ManifestSource;
import com.github.winplay02.gitcraft.manifest.metadata.VersionDetails;
import com.github.winplay02.gitcraft.manifest.skyrising.SkyrisingMetadataProvider;
import com.github.winplay02.gitcraft.manifest.vanilla.MojangLauncherManifest;
import com.github.winplay02.gitcraft.manifest.vanilla.MojangLauncherMetadataProvider;
import com.github.winplay02.gitcraft.types.OrderedVersion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class HistoricMojangLauncherMetadataProvider extends BaseMetadataProvider<MojangLauncherManifest, MojangLauncherManifest.VersionEntry> {
	private final MojangLauncherMetadataProvider mojangLauncherMetadataProvider;
	private final SkyrisingMetadataProvider skyrisingMetadataProvider;

	public HistoricMojangLauncherMetadataProvider(MojangLauncherMetadataProvider mojangLauncherMetadataProvider, SkyrisingMetadataProvider skyrisingMetadataProvider) {
		this.mojangLauncherMetadataProvider = mojangLauncherMetadataProvider;
		this.skyrisingMetadataProvider = skyrisingMetadataProvider;
	}

	@Override
	protected CompletableFuture<OrderedVersion> loadVersionFromManifest(Executor executor, MojangLauncherManifest.VersionEntry manifestEntry, Path targetDir) throws IOException {
		OrderedVersion mojangLauncherVersion = this.mojangLauncherMetadataProvider.getVersionByVersionID(manifestEntry.id());
		VersionDetails skyrisingVersion = this.skyrisingMetadataProvider.getVersionDetails(manifestEntry.id());
		for (VersionDetails.ManifestEntry skyrisingManifestEntry : skyrisingVersion.manifests()) {

		}
		// TODO
		return null;
	}

	@Override
	protected void loadVersionsFromRepository(Executor executor, Path dir, Consumer<OrderedVersion> loader) throws IOException {
	}

	@Override
	protected boolean isExistingVersionMetadataValid(MojangLauncherManifest.VersionEntry manifestEntry, Path targetDir) throws IOException {
		return false;
	}

	@Override
	public ManifestSource getSource() {
		return ManifestSource.MOJANG_HISTORIC;
	}

	@Override
	public String getName() {
		return "Historic Mojang Launcher Metadata";
	}

	@Override
	public String getInternalName() {
		return "historic-mojang";
	}

	@Override
	public List<String> getParentVersion(OrderedVersion mcVersion) {
		return this.mojangLauncherMetadataProvider.getParentVersion(mcVersion);
	}
}
