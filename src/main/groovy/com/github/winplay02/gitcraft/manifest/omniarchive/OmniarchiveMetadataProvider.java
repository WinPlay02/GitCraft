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
			case "2.0-blue" -> {
				return List.of("1.5.1");
			}
			case "2.0-purple" -> {
				return List.of("1.5.1");
			}
			case "2.0-red" -> {
				return List.of("1.5.1");
			}
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
			//Beta
			case "b1.3-demo" -> {
				return List.of("b1.3_01");
			}
			case "b1.2_02-dev" -> {
				return List.of("b1.2_02");
			}
			case "b1.2_02-launcher" -> {
				return List.of("b1.2_02");
			}
			//Alpha
			case "a1.2.0_02-launcher" -> {
				return List.of("a1.2.0_02");
			}
			case "a1.1.0-101847-launcher" -> {
				return List.of("a1.1.0-101847");
			}
			case "a1.0.14-1659-launcher" -> {
				return List.of("a1.0.14-1659");
			}
			case "a1.0.4-launcher" -> {
				return List.of("a1.0.4");
			}
			//Classic
			case "c0.30-c-1900-renew" -> {
				return List.of("c0.30-c-1900");
			}
			case "c0.0.13a_03-launcher" -> {
				return List.of("c0.0.13a_03");
			}
		}

		return super.getParentVersionIds(versionId);
	}

	private static final Pattern OMNI_NORMAL_SNAPSHOT_PATTERN = Pattern.compile("(^\\d\\dw\\d\\d[a-z](-\\d+)?$)|(^\\d.\\d+(.\\d+)?(-(pre|rc)(\\d+)?(-\\d+)?|-exp\\d+)?$)");

	@Override
	protected Pattern getNormalSnapshotPattern() {
		return OMNI_NORMAL_SNAPSHOT_PATTERN;
	}

	@Override
	public boolean shouldExcludeFromMainBranch(OrderedVersion mcVersion) {
		return super.shouldExcludeFromMainBranch(mcVersion)
			// Exclude all april fools snapshots
			|| mcVersion.isAprilFools()
			// Exclude special versions such as combat experiments and b1.3-demo
			|| (mcVersion.isSpecial()
				// Allow special versions which do not branch out
				&& !Objects.equals(mcVersion.launcherFriendlyVersionName(), "13w12~-1439")
				&& !Objects.equals(mcVersion.launcherFriendlyVersionName(), "1.5-pre-whitelinefix")
				&& !Objects.equals(mcVersion.launcherFriendlyVersionName(), "13w04a-whitelinefix")
				&& !Objects.equals(mcVersion.launcherFriendlyVersionName(), "13w02a-whitetexturefix")
				&& !Objects.equals(mcVersion.launcherFriendlyVersionName(), "1.0.0-tominecon")
				&& !Objects.equals(mcVersion.launcherFriendlyVersionName(), "b1.6-tb3")
				&& !Objects.equals(mcVersion.launcherFriendlyVersionName(), "c0.0.13a-launcher")
				&& !Objects.equals(mcVersion.launcherFriendlyVersionName(), "c0.0.11a-launcher"))
			// Exclude duplicate versions from launcher
			|| Objects.equals(mcVersion.launcherFriendlyVersionName(), "b1.2_02-launcher")
			|| Objects.equals(mcVersion.launcherFriendlyVersionName(), "a1.2.0_02-launcher")
			|| Objects.equals(mcVersion.launcherFriendlyVersionName(), "a1.1.0-101847-launcher")
			|| Objects.equals(mcVersion.launcherFriendlyVersionName(), "a1.0.14-1659-launcher")
			|| Objects.equals(mcVersion.launcherFriendlyVersionName(), "a1.0.4-launcher")
			|| Objects.equals(mcVersion.launcherFriendlyVersionName(), "c0.0.13a_03-launcher");
	}
}
