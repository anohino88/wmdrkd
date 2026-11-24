package io.github.augmentwild.paper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.util.Transformation;

/** A part inherits root displacement while keeping its own bearing, pivot, scale and interpolation. */
final class ArtRig {
    record Part(String model, List<ArtSignature.Frame> frames) {
        Transformation pose(ArtSignature root, Location anchor, ArtSignature.Frame frame) {
            Transformation local = frame.pose(anchor);
            return new Transformation(local.getTranslation().add(root.offsetAt(anchor,frame.tick())),
                    local.getLeftRotation(),local.getScale(),local.getRightRotation());
        }
    }

    static Map<String,List<Part>> load(InputStream data, Map<String,ArtSignature> roots) throws IOException {
        if (data == null) throw new IOException("Missing arcane-parts.tsv");
        Map<String,List<Part>> parts = new LinkedHashMap<>();
        var models = new HashSet<String>();
        try (var reader = new BufferedReader(new InputStreamReader(data,StandardCharsets.UTF_8))) {
            for (String row; (row = reader.readLine()) != null;) {
                if (row.isBlank() || row.startsWith("#")) continue;
                var columns = row.split("\\|",-1);
                if (columns.length != 5 || !columns[0].matches("rig_[a-z_]+_[ab]") || !models.add(columns[0]))
                    throw new IOException("Invalid rig part " + row);
                String id = columns[0].substring(4,columns[0].length() - 2);
                var root = roots.get(id);
                var frames = ArtSignature.parseFrames(columns[4]);
                var rig = parts.computeIfAbsent(id,ignored -> new ArrayList<>(2));
                if (root == null || frames.size() < 4 || frames.getLast().tick() > root.duration() || rig.size() >= 2)
                    throw new IOException("Invalid rig timeline " + id);
                rig.add(new Part(columns[0],frames));
            }
        }
        parts.replaceAll((id,rig) -> List.copyOf(rig));
        return Map.copyOf(parts);
    }
}
