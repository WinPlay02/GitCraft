package dex.mcgitmaker;

import com.github.winplay02.MinecraftVersionGraph;
import com.github.winplay02.MiscHelper;
import com.github.winplay02.SerializationHelper;
import dex.mcgitmaker.data.McVersion;
import dex.mcgitmaker.data.outlet.McFabric;
import dex.mcgitmaker.data.outlet.Outlet;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.VersionParsingException;
import net.fabricmc.loader.impl.game.minecraft.McVersionLookup;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Util {
	public static void saveMetadata(Map<String, McVersion> data) throws IOException {
		SerializationHelper.writeAllToPath(GitCraft.METADATA_STORE, SerializationHelper.serialize(data));
	}
}
