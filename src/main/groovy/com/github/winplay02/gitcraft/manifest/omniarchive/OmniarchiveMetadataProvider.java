package com.github.winplay02.gitcraft.manifest.omniarchive;

import java.util.List;

import com.github.winplay02.gitcraft.manifest.ManifestSource;
import com.github.winplay02.gitcraft.manifest.vanilla.MojangLauncherMetadataProvider;

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
	public List<String> getParentVersionIds(String versionId) {
		switch (versionId) {
			case "3D Shareware v1.34" -> {
				return List.of("19w13b-1653");
			}
		}

		return super.getParentVersionIds(versionId);
	}
}
