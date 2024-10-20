package com.github.winplay02.gitcraft.manifest;

import com.github.winplay02.gitcraft.manifest.historic.HistoricMojangLauncherMetadataProvider;
import com.github.winplay02.gitcraft.manifest.skyrising.SkyrisingMetadataProvider;
import com.github.winplay02.gitcraft.manifest.vanilla.MojangLauncherMetadataProvider;
import com.github.winplay02.gitcraft.util.LazyValue;

import java.util.Locale;
import java.util.function.Supplier;

public enum ManifestSource {

	MOJANG(MojangLauncherMetadataProvider::new),
	SKYRISING(SkyrisingMetadataProvider::new),
	MOJANG_HISTORIC(() -> new HistoricMojangLauncherMetadataProvider((MojangLauncherMetadataProvider) MOJANG.getMetadataProvider(), (SkyrisingMetadataProvider) SKYRISING.getMetadataProvider()));

	private final LazyValue<? extends MetadataProvider> metadataProvider;

	ManifestSource(Supplier<? extends MetadataProvider> metadataProvider) {
		this.metadataProvider = LazyValue.of(metadataProvider);
	}

	public MetadataProvider getMetadataProvider() {
		return this.metadataProvider.get();
	}

	@Override
	public String toString() {
		return super.toString().toLowerCase(Locale.ROOT);
	}
}
