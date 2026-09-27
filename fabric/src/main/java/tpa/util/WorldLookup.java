package tpa.util;

import tpa.TpaMod;

import java.util.List;
import java.util.stream.StreamSupport;

public final class WorldLookup {

    public static List<String> getWorldIds() {
        return StreamSupport.stream(TpaMod.SERVER.getAllLevels().spliterator(), false)
                .map(level -> level.dimension().identifier().toString())
                .collect(java.util.stream.Collectors.toList());
    }
}
