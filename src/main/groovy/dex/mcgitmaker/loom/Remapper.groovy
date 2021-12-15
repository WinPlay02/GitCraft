package dex.mcgitmaker.loom

import dex.mcgitmaker.GitCraft
import dex.mcgitmaker.data.McVersion
import net.fabricmc.tinyremapper.OutputConsumerPath
import net.fabricmc.tinyremapper.TinyRemapper

import java.nio.file.Path
import java.util.regex.Pattern

class Remapper {
    private static final Pattern MC_LV_PATTERN = Pattern.compile('\\$\\$\\d+')

    static Path doRemap(McVersion mcVersion) {
        def output = GitCraft.REMAPPED.resolve(mcVersion.version + '.jar')

        if (!output.toFile().exists()) {
            def remapper = TinyRemapper.newRemapper()
                    .renameInvalidLocals(true)
                    .rebuildSourceFilenames(true)
                    .invalidLvNamePattern(MC_LV_PATTERN)
                    .inferNameFromSameLvIndex(true)
                    .withMappings(mcVersion.mappingsProvider())
                    .fixPackageAccess(true)
                    .threads(Runtime.getRuntime().availableProcessors() - 3)
                    .build()

            remapper.readInputs(mcVersion.merged().toPath())

            try (OutputConsumerPath consumer = new OutputConsumerPath.Builder(output).build()) {
                remapper.apply(consumer, remapper.createInputTag())
            }

            remapper.finish()
        }

        return output
    }

}
