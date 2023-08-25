package dex.mcgitmaker;

import com.github.winplay02.MiscHelper;
import dex.mcgitmaker.data.McMetadata;
import dex.mcgitmaker.data.McVersion;
import dex.mcgitmaker.loom.FileSystemUtil;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Objects;
import java.util.TimeZone;
import java.util.regex.Pattern;
import java.util.stream.Stream;

class RepoManager {
	Git git;
	Path root_path;
	static String MAINLINE_LINEAR_BRANCH = "master";
	static Pattern LINEAR_SNAPSHOT_REGEX = Pattern.compile("(^\\d\\dw\\d\\d[a-z]$)|(^\\d.\\d+(.\\d+)?(-(pre|rc)\\d|_[a-z_\\-]+snapshot-\\d+)$)");

	public RepoManager() throws GitAPIException {
		this(null);
	}

	public RepoManager(Path root_path) throws GitAPIException {
		this.root_path = Objects.requireNonNullElse(root_path, GitCraft.REPO);
		this.git = setupRepo();
	}

	void finish() {
		git.close();
	}

	static boolean isVersionNonLinearSnapshot(McVersion mcVersion) {
		return mcVersion.snapshot && !(RepoManager.LINEAR_SNAPSHOT_REGEX.matcher(mcVersion.version).matches());
	}

	String findBaseForNonLinearVersionString(McVersion mcVersion) {
		switch (mcVersion.loaderVersion) {
			// Combat
			case "1.15-rc.3.combat.4" -> {
				return "1.15-pre3\n\nSemVer: 1.15-rc.3";
			}
			case "1.15.2-rc.2.combat.5" -> {
				return "1.15.2-pre2\n\nSemVer: 1.15.2-rc.2";
			}
			case "1.16.2-beta.3.combat.6" -> {
				return "1.16.2-pre3\n\nSemVer: 1.16.2-beta.3";
			}
			case "1.16.3-combat.7" -> {
				return "1.16.2\n\nSemVer: 1.16.2";
			}
			case "1.16.3-combat.7.b" -> {
				return "1.16_combat-1\n\nSemVer: 1.16.3-combat.7";
			}
			case "1.16.3-combat.7.c" -> {
				return "1.16_combat-2\n\nSemVer: 1.16.3-combat.7.b";
			}
			case "1.16.3-combat.8" -> {
				return "1.16_combat-3\n\nSemVer: 1.16.3-combat.7.c";
			}
			case "1.16.3-combat.8.b" -> {
				return "1.16_combat-4\n\nSemVer: 1.16.3-combat.8";
			}
			case "1.16.3-combat.8.c" -> {
				return "1.16_combat-5\n\nSemVer: 1.16.3-combat.8.b";
			}

			// April
			case "1.16-alpha.20.13.inf" -> {
				return "20w13b\n\nSemVer: 1.16-alpha.20.13.b";
			}
			case "1.19-alpha.22.13.oneblockatatime" -> {
				return "22w13a\n\nSemVer: 1.19-alpha.22.13.a";
			}
			case "1.20-alpha.23.13.ab" -> {
				return "23w13a\n\nSemVer: 1.20-alpha.23.13.a";
			}
			default -> {
				return null;
			}
		}
	}

	RevCommit findBaseForNonLinearVersion(McVersion mcVersion) throws IOException, GitAPIException {
		String msg = findBaseForNonLinearVersionString(mcVersion);
		if (msg == null) {
			return null;
		}
		Iterator<RevCommit> branchPointIter = git.log().all().setRevFilter(new CommitMsgFilter(msg)).call().iterator();
		if (branchPointIter.hasNext()) {
			return branchPointIter.next();
		}
		return null;
	}

	void commitDecompiled(McVersion mcVersion) throws GitAPIException, IOException {
		// add april fools clasification to outlet-database?
		String msg = mcVersion.version + "\n\nSemVer: " + mcVersion.loaderVersion;

		String target_branch = MAINLINE_LINEAR_BRANCH;
		if (git.getRepository().resolve(Constants.HEAD) != null) { // Don't run on empty repo
			target_branch = RepoManager.isVersionNonLinearSnapshot(mcVersion) ? mcVersion.version : MAINLINE_LINEAR_BRANCH;
			if (!Objects.equals(git.getRepository().getBranch(), target_branch)) {
				Ref target_ref = git.getRepository().getRefDatabase().findRef(target_branch);
				if (target_ref == null) {
					RevCommit branchPoint = findBaseForNonLinearVersion(mcVersion);
					if (branchPoint == null) {
						MiscHelper.println("Could not find branching point for non-linear version: %s (%s)", mcVersion.version, mcVersion.loaderVersion);
					}
					git.branchCreate().setStartPoint(branchPoint).setName(target_branch).call();
				} else {
					ObjectId tip_commit = target_ref.getObjectId();
					if (git.log().add(tip_commit).setRevFilter(new CommitMsgFilter(msg)).call().iterator().hasNext()) {
						return;
					}
				}
				git.checkout().setName(target_branch).call();
			} else {
				if (git.log().setRevFilter(new CommitMsgFilter(msg)).call().iterator().hasNext()) {
					return;
				}
			}
		}

		// Run from latest version
		git.reset().setMode(ResetCommand.ResetType.HARD);

		// Clear repo
		for (File it : Objects.requireNonNull(this.root_path.toFile().listFiles())) {
			if (!it.toPath().toString().endsWith(".git")) {
				if (it.isDirectory()) {
					Util.deleteDirectory(it.toPath());
				} else {
					it.delete();
				}
			}
		}

		// Copy decompiled MC to repo directory
		MiscHelper.println("Moving files to repo...");
		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(mcVersion.decompiledMc())) {
			copyLargeDir(fs.get().getPath("."), this.root_path.resolve("minecraft").resolve("src"));
		}

		// Copy assets & data (it makes sense to track them, atleast the data)
		try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(mcVersion.mergedJarPath())) {
			if (GitCraft.config.loadAssets) {
				copyLargeDir(fs.get().getPath("assets"), this.root_path.resolve("minecraft").resolve("resources").resolve("assets"));
			}
			if (GitCraft.config.loadIntegratedDatapack) {
				copyLargeDir(fs.get().getPath("data"), this.root_path.resolve("minecraft").resolve("resources").resolve("data"));
			}
		}

		if (GitCraft.config.loadAssets && GitCraft.config.loadAssetsExtern) {
			McMetadata.copyExternalAssetsToRepo(mcVersion, this.root_path);
		}

		// Make commit
		git.add().addFilepattern(".").setRenormalize(false).call();
		java.util.Date version_date = new java.util.Date(java.time.OffsetDateTime.parse(mcVersion.time).toInstant().toEpochMilli());
		PersonIdent author = new PersonIdent(GitCraft.config.gitUser, GitCraft.config.gitMail, version_date, TimeZone.getTimeZone("UTC"));
		git.commit().setMessage(msg).setAuthor(author).setCommitter(author).setSign(false).call();

		MiscHelper.println("Commited %s to the repository! (Target Branch is %s)", mcVersion.version, target_branch + (RepoManager.isVersionNonLinearSnapshot(mcVersion) ? " (non-linear)" : ""));
	}

	Git setupRepo() throws GitAPIException {
		return Git.init().setDirectory(this.root_path.toFile()).call();
	}

	private static void copyLargeDir(Path source, Path target) {
		try {
			if (Files.isDirectory(source)) {
				if (Files.notExists(target)) {
					Files.createDirectories(target);
				}

				try (Stream<Path> paths = Files.list(source)) {
					paths.forEach(p -> copyLargeDir(p, target.resolve(source.relativize(p).toString())));
				}

			} else {
				Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static final class CommitMsgFilter extends RevFilter {
		String msg;

		CommitMsgFilter(String msg) {
			this.msg = msg;
		}

		@Override
		public boolean include(RevWalk walker, RevCommit c) {
			return Objects.equals(c.getFullMessage(), this.msg);
		}

		@Override
		public RevFilter clone() {
			return this;
		}

		@Override
		public boolean requiresCommitBody() {
			return true;
		}

		@Override
		public String toString() {
			return "MSG FILTER";
		}
	}
}
