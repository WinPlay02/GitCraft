package com.github.winplay02.gitcraft.manifest;

import com.github.winplay02.gitcraft.manifest.historic.HistoricMojangLauncherMetadataProvider;
import com.github.winplay02.gitcraft.manifest.omniarchive.OmniarchiveMetadataProvider;
import com.github.winplay02.gitcraft.manifest.skyrising.SkyrisingMetadataProvider;
import com.github.winplay02.gitcraft.manifest.ornithe.OrnitheMetadataProvider;
import com.github.winplay02.gitcraft.manifest.vanilla.MojangLauncherMetadataProvider;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.LazyValue;

import java.util.Locale;
import java.util.function.Supplier;

public enum ManifestSource {

	MOJANG(MojangLauncherMetadataProvider::new),
	SKYRISING(SkyrisingMetadataProvider::new),
	ORNITHEMC(OrnitheMetadataProvider::new),
	OMNIARCHIVE(OmniarchiveMetadataProvider::new),
	MOJANG_HISTORIC(() -> new HistoricMojangLauncherMetadataProvider((MojangLauncherMetadataProvider) MOJANG.getMetadataProvider(), (SkyrisingMetadataProvider) SKYRISING.getMetadataProvider()));

	private final LazyValue<? extends MetadataProvider<OrderedVersion>> metadataProvider;

	ManifestSource(Supplier<? extends MetadataProvider<OrderedVersion>> metadataProvider) {
		this.metadataProvider = LazyValue.of(metadataProvider);
	}

	public MetadataProvider<OrderedVersion> getMetadataProvider() {
		return this.metadataProvider.get();
	}

	@Override
	public String toString() {
		return super.toString().toLowerCase(Locale.ROOT);
	}
}
