package com.github.winplay02.gitcraft.manifest.omniarchive;

import java.util.List;
import java.util.regex.Pattern;

import com.github.winplay02.gitcraft.GitCraftQuirks;
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
	public List<String> getParentVersionIds(String versionId) {
		switch (versionId) {
			// Combat
			case "combat1" -> {
				return List.of("1.14.3-pre4");
			}
			case "combat2" -> {
				return List.of("combat1", "1.14.4");
			}
			case "combat3" -> {
				return List.of("combat2");
			}
			case "combat4" -> {
				return List.of("combat3", "1.15-pre3");
			}
			case "combat5" -> {
				return List.of("combat4", "1.15.2-pre2");
			}
			case "combat6" -> {
				return List.of("combat5", "1.16.2-pre3");
			}
			case "combat7" -> {
				return List.of("combat6", "1.16.2");
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
				return List.of("1.17.1-pre1");
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
			// April
			case "2.0-preview" -> {
				return List.of("1.5.1");
			}
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
			case "23w13a_or_b-0722" -> {
				return List.of("23w13a");
			}
			case "23w13a_or_b-1249" -> {
				return List.of("23w13a_or_b-0722");
			}
			case "24w14potato-0838" -> {
				return List.of("24w12a");
			}
			case "24w14potato-1104" -> {
				return List.of("24w14potato-0838");
			}
			//Beta
			case "b1.3-demo" -> {
				return List.of("b1.3_01");
			}
			case "b1.2_02-dev" -> {
				return List.of("b1.2_02");
			}
			//Classic
			case "c0.30-c-1900-renew" -> {
				return List.of("c0.30-c-1900");
			}
		}

		String launcher_duplicate = this.getLauncherVersionDuplicate(versionId);
		if (launcher_duplicate != null) {
			return List.of(launcher_duplicate);
		}

		return super.getParentVersionIds(versionId);
	}

	private static final Pattern OMNI_NORMAL_SNAPSHOT_PATTERN = Pattern.compile("(^\\d\\dw\\d\\d[a-z](-\\d+)?$)|(^\\d.\\d+(.\\d+)?(-(pre|rc)(\\d+)?(-\\d+)?)?$)");

	@Override
	protected Pattern getNormalSnapshotPattern() {
		return OMNI_NORMAL_SNAPSHOT_PATTERN;
	}

	private static final String LAUNCHER_SUFFIX = "-launcher";

	private String getLauncherVersionDuplicate(String versionId) {
		if (versionId.endsWith(LAUNCHER_SUFFIX)) {
			String potential_duplicate = versionId.substring(0, versionId.length() - LAUNCHER_SUFFIX.length());
			if (this.getVersionsAssumeLoaded().containsKey(potential_duplicate)) {
				return potential_duplicate;
			}
		}
		return null;
	}

	@Override
	public boolean shouldExcludeFromMainBranch(OrderedVersion mcVersion) {
		return super.shouldExcludeFromMainBranch(mcVersion)
			// Exclude all april fools snapshots
			|| mcVersion.isAprilFools()
			// Exclude special versions such as combat experiments and b1.3-demo
			|| (mcVersion.isSpecial()
				// Allow special versions which do not branch out
				&& !GitCraftQuirks.omniarchiveLinearSpecials.contains(mcVersion.launcherFriendlyVersionName()))
			// Exclude duplicate versions from launcher
			|| (this.getLauncherVersionDuplicate(mcVersion.launcherFriendlyVersionName()) != null);
	}
}
