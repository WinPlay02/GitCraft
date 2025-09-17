package com.github.winplay02.gitcraft.manifest.omniarchive;

import java.util.List;

import com.github.winplay02.gitcraft.manifest.ManifestSource;
import com.github.winplay02.gitcraft.manifest.vanilla.MojangLauncherMetadataProvider;
import com.github.winplay02.gitcraft.types.OrderedVersion;

public class OmniarchiveMetadataProvider extends MojangLauncherMetadataProvider {

	public OmniarchiveMetadataProvider() {
		super("https://meta.omniarchive.uk/v1/manifest.json");
	}

	@Override
	public ManifestSource getSource() {
		return ManifestSource.OMNIARCHIVE;
	}

	@Override
	public String getName() {
		return "Omniarchive Version Metadata (https://omniarchive.uk/)";
	}

	@Override
	public String getInternalName() {
		return "omniarchive";
	}

	@Override
	public List<String> getParentVersion(OrderedVersion mcVersion) {
		switch (mcVersion.semanticVersion()) {
		case "1.14-alpha.19.13.shareware" -> {
			return List.of("1.14-alpha.19.13.b+1653");
		}
		}

		return super.getParentVersion(mcVersion);
	}
}
