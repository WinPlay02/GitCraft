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
			// Unobfuscated
			case "25w45a-unobf" -> {
				return List.of("25w44a");
			}
			case "25w46a-unobf" -> {
				return List.of("25w45a-unobf");
			}
			case "1.21.11-pre1-unobf" -> {
				return List.of("25w46a-unobf");
			}
			case "1.21.11-pre2-unobf" -> {
				return List.of("1.21.11-pre1-unobf");
			}
			case "1.21.11-pre3-unobf" -> {
				return List.of("1.21.11-pre2-unobf");
			}
			case "1.21.11-pre4-unobf" -> {
				return List.of("1.21.11-pre3-unobf");
			}
			case "1.21.11-pre5-unobf" -> {
				return List.of("1.21.11-pre4-unobf");
			}
			case "1.21.11-rc1-unobf" -> {
				return List.of("1.21.11-pre5-unobf");
			}
			case "1.21.11-rc2-unobf" -> {
				return List.of("1.21.11-rc1-unobf");
			}
			case "1.21.11-rc3-unobf" -> {
				return List.of("1.21.11-rc2-unobf");
			}
			case "1.21.11-unobf" -> {
				return List.of("1.21.11-rc3-unobf");
			}
			// April
			case "3D Shareware v1.34" -> {
				return List.of("19w13b+1653");
			}
		}

		return super.getParentVersionIds(versionId);
	}
}
