package com.github.winplay02.gitcraft.manifest;

import java.util.Locale;
import java.util.function.Supplier;

import com.github.winplay02.gitcraft.manifest.skyrising.SkyrisingMetadataProvider;
import com.github.winplay02.gitcraft.manifest.ornithe.OrnitheMetadataProvider;
import com.github.winplay02.gitcraft.manifest.vanilla.MojangLauncherMetadataProvider;
import com.github.winplay02.gitcraft.util.LazyValue;

public enum ManifestSource {

	MOJANG(MojangLauncherMetadataProvider::new),
	SKYRISING(SkyrisingMetadataProvider::new),
	ORNITHEMC(OrnitheMetadataProvider::new);

	private final LazyValue<? extends MetadataProvider> metadataProvider;

	private ManifestSource(Supplier<? extends MetadataProvider> metadataProvider) {
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
