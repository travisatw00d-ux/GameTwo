package com.game.character;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import java.util.ArrayList;
import java.util.List;

public class AnimationClip {

    private final String name;
    private final float duration;
    private final boolean looping;
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
        List<int[]> frameMeta = new ArrayList<>();
        List<String> frameStrings = new ArrayList<>();
        parseFrames(json, frameStrings, frameMeta);

        if (frameStrings.isEmpty()) {
            throw new RuntimeException("No frames found in animation: " + name);
        }

        String[] boneNames = null;
        int firstFrameNum = frameMeta.get(0)[0];
        int lastFrameNum = frameMeta.get(frameStrings.size() - 1)[0];
        boolean looping = frameStrings.size() > 1 && lastFrameNum == firstFrameNum;

        int frameCount = looping ? frameStrings.size() - 1 : frameStrings.size();
        Keyframe[] keyframes = new Keyframe[frameCount];

        for (int i = 0; i < frameCount; i++) {
            int[] meta = frameMeta.get(i);
            int frameNum = meta[0];

            // Parse bones from this frame string
            String frameStr = frameStrings.get(i);
            List<String> boneNamesList = new ArrayList<>();
            List<Quaternion> rots = new ArrayList<>();
            List<Vector3f> locs = new ArrayList<>();
            parseBones(frameStr, boneNamesList, rots, locs);

            if (boneNames == null) {
                boneNames = boneNamesList.toArray(new String[0]);
            }

            float frameTime = (frameNum - firstFrameNum) / fps;

            Keyframe kf = new Keyframe(Math.max(0f, frameTime), boneNames.length);
            for (int j = 0; j < boneNames.length; j++) {
                kf.rotations[j] = rots.get(j);
                kf.translations[j] = locs.get(j);
            }
            keyframes[i] = kf;
        }

        float duration = frameCount > 1 ? keyframes[frameCount - 1].time : 0f;

        return new AnimationClip(name, duration, looping, boneNames, keyframes);
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
