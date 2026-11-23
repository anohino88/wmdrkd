package io.github.augmentwild.paper;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Local item transforms shared with the offline Blender keyframe renderer. */
record ArtSignature(String shape, String sound, List<Frame> frames) {
    record Frame(int tick, float right, float up, float forward, float yaw, float pitch, float roll,
                 float width, float height, float depth) {
        Transformation pose(Location at) {
            float bearing = (float) Math.toRadians(-at.getYaw());
            Vector3f offset = new Vector3f(right,up,forward).rotateY(bearing);
            Quaternionf rotation = new Quaternionf().rotateY(bearing + radians(yaw)).rotateX(radians(pitch)).rotateZ(radians(roll));
            return new Transformation(offset,rotation,new Vector3f(width,height,depth),new Quaternionf());
        }

        private static float radians(float degrees) { return (float) Math.toRadians(degrees); }
    }

    ArtSignature along(float distance, float climb) {
        return new ArtSignature(shape,sound,frames.stream().map(frame -> {
            float t = Math.clamp((frame.forward() + .23f) / .68f,0,1);
            if (frame.tick() >= 8) t = 1;
            return new Frame(frame.tick(),frame.right(),frame.up()+climb*t,distance*t,
                    frame.yaw(),frame.pitch(),frame.roll(),frame.width(),frame.height(),frame.depth());
        }).toList());
    }

    int duration() { return frames.getLast().tick(); }

    static Map<String, ArtSignature> load(InputStream input) throws java.io.IOException {
        if (input == null) throw new java.io.IOException("Missing art-signatures.tsv");
        Map<String, ArtSignature> result = new LinkedHashMap<>();
        try (var reader = new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) {
                if (line.isBlank() || line.startsWith("#")) continue;
                var column = line.split("\\|",-1);
                if (column.length != 4 || !column[0].matches("[a-z_]+") || !column[1].matches("[a-z_]+")
                        || !column[2].matches("[a-z_]+")) throw new java.io.IOException("Invalid art signature " + line);
                List<Frame> frames = new ArrayList<>();
                int prior = -1;
                for (String key : column[3].split(";")) {
                    var number = key.split(",");
                    if (number.length != 10) throw new java.io.IOException("Invalid art keyframe " + key);
                    int tick = Integer.parseInt(number[0]);
                    float[] pose = new float[9];
                    for (int i = 0; i < pose.length; i++) {
                        pose[i] = Float.parseFloat(number[i+1]);
                        if (!Float.isFinite(pose[i])) throw new java.io.IOException("Invalid art keyframe " + key);
                    }
                    if ((frames.isEmpty() && tick != 0) || tick <= prior || tick > 30
                            || pose[6] <= 0 || pose[7] <= 0 || pose[8] <= 0) {
                        throw new java.io.IOException("Invalid art keyframe " + key);
                    }
                    frames.add(new Frame(tick,pose[0],pose[1],pose[2],pose[3],pose[4],pose[5],pose[6],pose[7],pose[8]));
                    prior = tick;
                }
                if (frames.size() < 4 || result.putIfAbsent(column[0],new ArtSignature(column[1],column[2],List.copyOf(frames))) != null)
                    throw new java.io.IOException("Invalid art sequence " + column[0]);
            }
        } catch (NumberFormatException invalid) {
            throw new java.io.IOException("Invalid art keyframe number",invalid);
        }
        return Map.copyOf(result);
    }
}
