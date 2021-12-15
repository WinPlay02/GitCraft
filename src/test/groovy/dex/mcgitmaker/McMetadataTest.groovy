package dex.mcgitmaker

import dex.mcgitmaker.data.McVersion
import dex.mcgitmaker.loom.Decompiler
import net.fabricmc.loader.api.SemanticVersion

class McMetadataTest {
    static void main(String[] args) {
        def x = new GitCraft()
        def v = x.versions.get(SemanticVersion.parse('1.18'))

        //x.merged() // has to be called or it endlessly tries to merge them

        //Decompiler.decompile(v)
        x.decompileAllVersions(false)

        //keyset is in ascending order, good
    }
}