package com.github.winplay02.gitcraft.manifest.ornithe;
import com.github.winplay02.gitcraft.manifest.ManifestSource;
import com.github.winplay02.gitcraft.manifest.skyrising.SkyrisingMetadataProvider;

public class OrnitheMetadataProvider extends SkyrisingMetadataProvider {

	public OrnitheMetadataProvider() {
		super("https://ornithemc.net/mc-versions/version_manifest.json");
	}

	@Override
	public ManifestSource getSource() {
		return ManifestSource.ORNITHEMC;
	}

	@Override
	public String getName() {
		return "OrnitheMC Version Metadata (https://ornithemc.github.io/mc-versions/)";
	}

	@Override
	public String getInternalName() {
		return "ornithemc";
	}
}
