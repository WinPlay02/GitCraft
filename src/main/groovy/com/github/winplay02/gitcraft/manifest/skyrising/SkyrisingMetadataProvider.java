package com.github.winplay02.gitcraft.manifest.skyrising;

import com.github.winplay02.gitcraft.manifest.BaseMetadataProvider;
import com.github.winplay02.gitcraft.manifest.ManifestSource;
import com.github.winplay02.gitcraft.manifest.metadata.VersionDetails;
import com.github.winplay02.gitcraft.manifest.metadata.VersionInfo;
import com.github.winplay02.gitcraft.types.OrderedVersion;
import com.github.winplay02.gitcraft.util.MiscHelper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class SkyrisingMetadataProvider extends BaseMetadataProvider<SkyrisingManifest, SkyrisingManifest.VersionEntry> {

	private final Map<String, VersionDetails> versionDetails = new ConcurrentHashMap<>();

	public SkyrisingMetadataProvider() {
		this("https://skyrising.github.io/mc-versions/version_manifest.json");
	}

	protected SkyrisingMetadataProvider(String manifestUrl) {
		this.addManifestSource(manifestUrl, SkyrisingManifest.class);
	}

	@Override
	public ManifestSource getSource() {
		return ManifestSource.SKYRISING;
	}

	@Override
	public String getName() {
		return "Skyrising Version Metadata (https://skyrising.github.io/mc-versions/)";
	}

	@Override
	public String getInternalName() {
		return "skyrising";
	}

	@Override
	protected void postLoadVersions() {
		this.versionDetails.keySet().removeIf(version -> this.versionsById.get(version) == null);
	}

	@Override
	public int getConcurrentRequestLimit() {
		// derived experimentally
		// reduce this if Skyrising tests fail again or multiple failed downloads occur
		return 4;
	}

	public CompletableFuture<VersionInfo> fetchSpecificManifest(Executor executor, String id, VersionDetails.ManifestEntry manifestEntryPtr) {
		try {
			return this.fetchVersionMetadataFilename(executor, String.format("%s_%s.json", id, manifestEntryPtr.hash()), id, manifestEntryPtr.url(), manifestEntryPtr.hash(), this.manifestMetadata.resolve("manifests"), "version manifest", VersionInfo.class);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	protected CompletableFuture<OrderedVersion> loadVersionFromManifest(Executor executor, SkyrisingManifest.VersionEntry manifestEntry, Path targetDir) throws IOException {
		CompletableFuture<VersionInfo> infoFuture = this.fetchVersionMetadata(executor, manifestEntry.id(), manifestEntry.url(), null, targetDir.resolve("info"), "version info", VersionInfo.class);
		CompletableFuture<VersionDetails> detailsFuture = this.fetchVersionMetadata(executor, manifestEntry.id(), manifestEntry.details(), null, targetDir.resolve("details"), "version details", VersionDetails.class);
		return CompletableFuture.allOf(infoFuture, detailsFuture).thenApply($ -> {
			VersionInfo info = infoFuture.join();
			VersionDetails details = detailsFuture.join();
			this.versionDetails.put(details.id(), details);
			return OrderedVersion.from(info, details.normalizedVersion());
		});
	}

	@Override
	protected void loadVersionsFromRepository(Executor executor, Path dir, Consumer<OrderedVersion> loader) throws IOException {
		Set<VersionInfo> infos = new LinkedHashSet<>();
		Map<String, VersionDetails> detailses = new LinkedHashMap<>();

		try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(dir, f -> Files.isRegularFile(f) && (f.endsWith(".json") || f.endsWith(".zip")))) {
			for (Path file : directoryStream) {
				VersionInfo info = this.loadVersionMetadata(file, VersionInfo.class, file.getFileName().toString());

				// we could check every field but this ought to be enough
				if (info.id() != null && info.assets() != null) {
					infos.add(info);
				} else {
					VersionDetails details = this.loadVersionMetadata(file, VersionDetails.class, file.getFileName().toString());

					// we could check every field but this ought to be enough
					if (details.id() != null && details.normalizedVersion() != null) {
						detailses.put(details.id(), details);
					}
				}
			}
		}

		for (VersionInfo info : infos) {
			VersionDetails details = detailses.remove(info.id());

			if (details != null) {
				this.versionDetails.put(details.id(), details);
				loader.accept(OrderedVersion.from(info, details.normalizedVersion()));
			} else {
				MiscHelper.println("repository contains version info json for %s, but no version details json!", info.id());
			}
		}
		for (VersionDetails details : detailses.values()) {
			MiscHelper.println("repository contains version details json for %s, but no version info json!", details.id());
		}
	}

	@Override
	protected boolean isExistingVersionMetadataValid(SkyrisingManifest.VersionEntry manifestEntry, Path targetDir) throws IOException {
		return this.isExistingVersionMetadataValid(manifestEntry.id(), manifestEntry.url(), null, targetDir.resolve("info"))
			&& this.isExistingVersionMetadataValid(manifestEntry.id(), manifestEntry.details(), null, targetDir.resolve("details"));
	}

	@Override
	public List<OrderedVersion> getParentVersions(OrderedVersion mcVersion) {
		return this.getVersionDetails(mcVersion.launcherFriendlyVersionName()).previous().stream()
			.map(this::getVersionByVersionID)
			.filter(Objects::nonNull)
			.toList();
	}

	private static final Pattern NORMAL_SNAPSHOT_PATTERN = Pattern.compile("(^\\d\\dw\\d\\d[a-z](-\\d+)?$)|(^\\d.\\d+(.\\d+)?(-(pre|rc)((-\\d+|\\d+)(-\\d+)?)?|_[a-z_\\-]+snapshot-\\d+| Pre-Release \\d+)?$)");

	@Override
	public boolean shouldExclude(OrderedVersion mcVersion) {
		// classic and alpha servers don't work well with the version graph right now
		return mcVersion.launcherFriendlyVersionName().startsWith("server-");
	}

	@Override
	public boolean shouldExcludeFromMainBranch(OrderedVersion mcVersion) {
		return super.shouldExcludeFromMainBranch(mcVersion)
			// ensure the main branch goes through 12w32a rather than 1.3.2
			|| Objects.equals(mcVersion.launcherFriendlyVersionName(), "1.3.2")
			|| (mcVersion.isSnapshotOrPending()
				// filter out april fools snapshots and experimental versions,
				// which often have typical ids that do not match normal snapshots
				&& !NORMAL_SNAPSHOT_PATTERN.matcher(mcVersion.launcherFriendlyVersionName()).matches()
				// allow 13w12~ anyway as the changes in it carry over to 1.5.1
				&& !Objects.equals(mcVersion.launcherFriendlyVersionName(), "13w12~"));
	}

	public VersionDetails getVersionDetails(String versionId) {
		return this.versionDetails.get(versionId);
	}
}
