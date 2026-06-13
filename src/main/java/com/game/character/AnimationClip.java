package com.game.character;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import java.util.ArrayList;
import java.util.List;

public class AnimationClip {

    private final String name;
    private final float duration;
    private boolean looping;
    private final String[] boneNames;
    private final Keyframe[] keyframes;

    public AnimationClip(String name, float duration, boolean looping, String[] boneNames, Keyframe[] keyframes) {
        this.name = name;
        this.duration = duration;
        this.looping = looping;
        this.boneNames = boneNames;
        this.keyframes = keyframes;
    }

    public String getName()          { return name; }
    public float getDuration()       { return duration; }
    public boolean isLooping()       { return looping; }
    public void setLooping(boolean l) { this.looping = l; }
    public String[] getBoneNames()   { return boneNames; }
    public Keyframe[] getKeyframes() { return keyframes; }

    public static class Keyframe {
        public final float time;
        public final Quaternion[] rotations;
        public final Vector3f[] translations;

        Keyframe(float time, int boneCount) {
            this.time = time;
            this.rotations = new Quaternion[boneCount];
            this.translations = new Vector3f[boneCount];
        }
    }

    public static AnimationClip load(String name, String json) {
        return load(name, json, 30f);
    }

    public static AnimationClip load(String name, String json, float fps) {
        String trimmed = json.trim();

        Boolean loopOverride = null;
        if (trimmed.startsWith("{") && trimmed.contains("\"frames\"")) {
            Boolean extracted = extractLoopFlag(trimmed);
            loopOverride = extracted == null ? true : extracted;
            float envelopeFps = extractFps(trimmed);
            if (envelopeFps > 0f) fps = envelopeFps;
        }

        List<int[]> frameMeta = new ArrayList<>();
        List<String> frameStrings = new ArrayList<>();
        parseFrames(json, frameStrings, frameMeta);

        if (frameStrings.isEmpty()) {
            throw new RuntimeException("No frames found in animation: " + name);
        }

        String[] boneNames = null;
        int firstFrameNum = frameMeta.get(0)[0];
        int lastFrameNum = frameMeta.get(frameStrings.size() - 1)[0];

        boolean looping;
        if (loopOverride != null) {
            looping = loopOverride;
        } else {
            looping = frameStrings.size() > 1 && lastFrameNum == firstFrameNum;
        }

        int rawCount = frameStrings.size();
        List<Quaternion[]> allRots = new ArrayList<>(rawCount);
        List<Vector3f[]> allLocs = new ArrayList<>(rawCount);

        for (int i = 0; i < rawCount; i++) {
            List<String> names = new ArrayList<>();
            List<Quaternion> rots = new ArrayList<>();
            List<Vector3f> locs = new ArrayList<>();
            parseBones(frameStrings.get(i), names, rots, locs);

            if (boneNames == null) {
                boneNames = names.toArray(new String[0]);
            }

            allRots.add(rots.toArray(new Quaternion[0]));
            allLocs.add(locs.toArray(new Vector3f[0]));
        }

        int totalBones = boneNames.length;

        List<Keyframe> expanded = new ArrayList<>();
        float currentTime = 0f;

        if (loopOverride != null && looping) {
            expanded.add(new Keyframe(0f, totalBones));
            for (int j = 0; j < totalBones; j++) {
                expanded.get(0).rotations[j] = allRots.get(1)[j];
                expanded.get(0).translations[j] = allLocs.get(1)[j];
            }

            for (int i = 1; i < rawCount - 1; i++) {
                int steps = frameMeta.get(i)[1];
                if (steps <= 0) continue;

                for (int step = 1; step <= steps; step++) {
                    float t = (float) step / steps;
                    currentTime += 1f / fps;

                    Keyframe kf = new Keyframe(currentTime, totalBones);
                    for (int j = 0; j < totalBones; j++) {
                        Quaternion q = new Quaternion();
                        q.slerp(allRots.get(i)[j], allRots.get(i + 1)[j], t);
                        kf.rotations[j] = q;

                        Vector3f s = allLocs.get(i)[j];
                        Vector3f e = allLocs.get(i + 1)[j];
                        kf.translations[j] = new Vector3f(
                            s.x + (e.x - s.x) * t,
                            s.y + (e.y - s.y) * t,
                            s.z + (e.z - s.z) * t
                        );
                    }
                    expanded.add(kf);
                }
            }

            int last = rawCount - 1;
            int loopSteps = frameMeta.get(0)[1];
            for (int step = 1; step <= loopSteps; step++) {
                float t = (float) step / loopSteps;
                currentTime += 1f / fps;

                Keyframe kf = new Keyframe(currentTime, totalBones);
                for (int j = 0; j < totalBones; j++) {
                    Quaternion q = new Quaternion();
                    q.slerp(allRots.get(last)[j], allRots.get(1)[j], t);
                    kf.rotations[j] = q;

                    Vector3f s = allLocs.get(last)[j];
                    Vector3f e = allLocs.get(1)[j];
                    kf.translations[j] = new Vector3f(
                        s.x + (e.x - s.x) * t,
                        s.y + (e.y - s.y) * t,
                        s.z + (e.z - s.z) * t
                    );
                }
                expanded.add(kf);
            }

        } else {
            expanded.add(new Keyframe(0f, totalBones));
            for (int j = 0; j < totalBones; j++) {
                expanded.get(0).rotations[j] = allRots.get(0)[j];
                expanded.get(0).translations[j] = allLocs.get(0)[j];
            }

            for (int i = 0; i < rawCount - 1; i++) {
                int delta = frameMeta.get(i + 1)[1];
                if (delta <= 0) continue;

                for (int step = 1; step <= delta; step++) {
                    float t = (float) step / delta;
                    currentTime += 1f / fps;

                    Keyframe kf = new Keyframe(currentTime, totalBones);
                    for (int j = 0; j < totalBones; j++) {
                        Quaternion q = new Quaternion();
                        q.slerp(allRots.get(i)[j], allRots.get(i + 1)[j], t);
                        kf.rotations[j] = q;

                        Vector3f s = allLocs.get(i)[j];
                        Vector3f e = allLocs.get(i + 1)[j];
                        kf.translations[j] = new Vector3f(
                            s.x + (e.x - s.x) * t,
                            s.y + (e.y - s.y) * t,
                            s.z + (e.z - s.z) * t
                        );
                    }
                    expanded.add(kf);
                }
            }

            if (looping && expanded.size() > 1) {
                currentTime += 1f / fps;
                Keyframe kf = new Keyframe(currentTime, totalBones);
                for (int j = 0; j < totalBones; j++) {
                    kf.rotations[j] = expanded.get(0).rotations[j].clone();
                    kf.translations[j] = expanded.get(0).translations[j].clone();
                }
                expanded.add(kf);
            }
        }

        Keyframe[] keyframes = expanded.toArray(new Keyframe[0]);

        for (Keyframe kf : keyframes) {
            for (int j = 0; j < totalBones; j++) {
                if (kf.rotations[j] != null) kf.rotations[j].normalizeLocal();
            }
        }

        float duration = keyframes.length > 1 ? keyframes[keyframes.length - 1].time : 0f;

        return new AnimationClip(name, duration, looping, boneNames, keyframes);
    }

    private static Boolean extractLoopFlag(String json) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"loop\"\\s*:\\s*(true|false)");
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1).equals("true");
        }
        return null;
    }

    private static float extractFps(String json) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"fps\"\\s*:\\s*([\\d.]+)");
        java.util.regex.Matcher m = p.matcher(json);
        if (m.find()) {
            try {
                return Float.parseFloat(m.group(1));
            } catch (NumberFormatException e) {
                return 0f;
            }
        }
        return 0f;
    }

    private static void parseFrames(String json, List<String> outStrings, List<int[]> outMeta) {
        int idx = json.indexOf('[');
        if (idx < 0) return;

        int depth = 0;
        int objStart = -1;
        for (int i = idx; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') {
                if (depth == 0) objStart = i;
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && objStart >= 0) {
                    String obj = json.substring(objStart, i + 1);
                    int frameNum = extractInt(obj, "frame");
                    int delta = extractInt(obj, "delta");
                    outStrings.add(obj);
                    outMeta.add(new int[]{frameNum, delta});
                    objStart = -1;
                }
            }
        }
    }

    private static void parseBones(String frameStr, List<String> names, List<Quaternion> rots, List<Vector3f> locs) {
        int bonesIdx = frameStr.indexOf("\"bones\"");
        if (bonesIdx < 0) return;

        int colon = frameStr.indexOf(':', bonesIdx);
        int objStart = frameStr.indexOf('{', colon);
        if (objStart < 0) return;

        int depth = 0;
        int objEnd = -1;
        for (int i = objStart; i < frameStr.length(); i++) {
            char c = frameStr.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) { objEnd = i; break; }
            }
        }
        if (objEnd < 0) return;

        String bonesContent = frameStr.substring(objStart + 1, objEnd);

        // Match each bone entry: "boneName": { ... }
        java.util.regex.Pattern entryPattern = java.util.regex.Pattern.compile(
            "\"([\\w.]+)\"\\s*:\\s*\\{([^}]+)\\}"
        );
        java.util.regex.Matcher entryMatcher = entryPattern.matcher(bonesContent);

        // Sub-patterns for rot and loc within a bone entry
        java.util.regex.Pattern rotPattern = java.util.regex.Pattern.compile(
            "\"rot\"\\s*:\\s*\\[([^\\]]+)\\]"
        );
        java.util.regex.Pattern locPattern = java.util.regex.Pattern.compile(
            "\"loc\"\\s*:\\s*\\[([^\\]]+)\\]"
        );

        int count = 0;
        while (entryMatcher.find()) {
            String boneName = entryMatcher.group(1);
            String inner = entryMatcher.group(2);

            java.util.regex.Matcher rotM = rotPattern.matcher(inner);
            java.util.regex.Matcher locM = locPattern.matcher(inner);

            if (!rotM.find() || !locM.find()) continue;

            String[] rotParts = rotM.group(1).split(",");
            String[] locParts = locM.group(1).split(",");

            if (rotParts.length < 4 || locParts.length < 3) continue;

            float w = Float.parseFloat(rotParts[0].trim());
            float x = Float.parseFloat(rotParts[1].trim());
            float y = Float.parseFloat(rotParts[2].trim());
            float z = Float.parseFloat(rotParts[3].trim());

            float lx = Float.parseFloat(locParts[0].trim());
            float ly = Float.parseFloat(locParts[1].trim());
            float lz = Float.parseFloat(locParts[2].trim());

            names.add(boneName);
            rots.add(new Quaternion(x, y, z, w));
            locs.add(new Vector3f(lx, ly, lz));
            count++;
        }
    }

    private static int extractInt(String obj, String key) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + key + "\"\\s*:\\s*(-?\\d+)");
        java.util.regex.Matcher m = p.matcher(obj);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }
}
