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
			// Combat
			case "combat1" -> {
				return List.of("1.14.3-pre4");
			}
			case "combat2" -> {
				return List.of("1.14.4", "combat1");
			}
			case "combat3" -> {
				return List.of("combat2");
			}
			case "combat4" -> {
				return List.of("1.15-pre3", "combat3");
			}
			case "combat5" -> {
				return List.of("1.15.2-pre2", "combat4");
			}
			case "combat6" -> {
				return List.of("1.16.2-pre3", "combat5");
			}
			case "combat7" -> {
				return List.of("1.16.2", "combat6");
			}
			case "combat7b" -> {
				return List.of("combat7");
			}
			case "combat7c" -> {
				return List.of("combat7b");
			}
			case "combat8" -> {
				return List.of("combat7c");
			}
			case "combat8b" -> {
				return List.of("combat8");
			}
			case "combat8c" -> {
				return List.of("combat8b");
			}
			// Experimental 1.18
			case "1.18-exp1" -> {
				return List.of("1.17.1");
			}
			case "1.18-exp2" -> {
				return List.of("1.18-exp1");
			}
			case "1.18-exp3" -> {
				return List.of("1.18-exp2");
			}
			case "1.18-exp4" -> {
				return List.of("1.18-exp3");
			}
			case "1.18-exp5" -> {
				return List.of("1.18-exp4");
			}
			case "1.18-exp6" -> {
				return List.of("1.18-exp5");
			}
			case "1.18-exp7" -> {
				return List.of("1.18-exp6");
			}
			case "21w37a" -> {
				return List.of("1.17.1", "1.18-exp7");
			}
			// Experimental 1.19
			case "1.19-exp1" -> {
				return List.of("1.18.1");
			}
			case "22w11a" -> {
				return List.of("1.18.2", "1.19-exp1");
			}
			// April
			case "3D Shareware v1.34" -> {
				return List.of("19w13b-1653");
			}
			case "23w13a_or_b-1249" -> {
				return List.of("23w13a_or_b-0722", "23w13a");
			}
			case "23w13a_or_b-0722" -> {
				return List.of("23w13a");
			}
			case "24w14potato-1104" -> {
				return List.of("24w14potato-0838", "24w12a");
			}
			case "24w14potato-0838" -> {
				return List.of("24w12a");
			}
		}

		return super.getParentVersionIds(versionId);
	}
}
