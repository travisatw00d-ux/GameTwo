package com.game.character;

import com.jme3.anim.Joint;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SideStepPoseApplier {

    private static final Set<String> WHITELIST = Set.of(
        "Hips",
        "Leg.L", "Shin.L", "Foot.L",
        "Leg.R", "Shin.R"
    );

    private static final float IDENTITY_THRESHOLD = 0.9999f;
    private static final Quaternion IDENTITY = new Quaternion();

    private static class SeqStep {
        final String from, to;
        final float dur;
        SeqStep(String from, String to, float dur) {
            this.from = from;
            this.to = to;
            this.dur = dur;
        }
    }

    private static final List<SeqStep> SEQUENCE = List.of(
        new SeqStep(null,     "lift",  0.067f),
        new SeqStep("lift",   "lift2", 0.1f),
        new SeqStep("lift2",  "lift3", 0.1f),
        new SeqStep("lift3",  "plant", 0.2f),
        new SeqStep("plant",  "lift",  0.3f)
    );

    private final HumanoidRig rig;
    private final Map<String, Quaternion> restPoseRot = new LinkedHashMap<>();
    private final Map<String, Map<String, Quaternion>> poses = new LinkedHashMap<>();
    private boolean restPoseCaptured = false;

    private boolean active = false;
    private int stepIdx = 0;
    private float stepT = 0f;
    private final Map<String, Quaternion> deltaFrom = new HashMap<>();

    public SideStepPoseApplier(HumanoidRig rig) {
        this.rig = rig;
    }

    public void captureRestPose() {
        restPoseRot.clear();
        for (Joint j : rig.getArmature().getJointList()) {
            restPoseRot.put(j.getName(), j.getLocalRotation().clone());
        }
        restPoseCaptured = true;
        System.out.println("SideStepPoseApplier: captured rest pose for "
            + restPoseRot.size() + " joints");
    }

    public void addPose(String name, String json) {
        Map<String, Quaternion> boneDeltas = new LinkedHashMap<>();

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\"([\\w.]+)\"\\s*:\\s*\\{\\s*\"rot\"\\s*:\\s*\\[([^\\]]+)\\]\\s*,\\s*\"loc\"\\s*:\\s*\\[([^\\]]+)\\]"
        );
        java.util.regex.Matcher matcher = pattern.matcher(json);

        while (matcher.find()) {
            String boneName = matcher.group(1);
            if (!WHITELIST.contains(boneName)) continue;

            String[] rotParts = matcher.group(2).split(",");
            float w = Float.parseFloat(rotParts[0].trim());
            float x = Float.parseFloat(rotParts[1].trim());
            float y = Float.parseFloat(rotParts[2].trim());
            float z = Float.parseFloat(rotParts[3].trim());

            if (Math.abs(w) >= IDENTITY_THRESHOLD &&
                Math.abs(x) < 0.001f && Math.abs(y) < 0.001f && Math.abs(z) < 0.001f) {
                continue;
            }

            boneDeltas.put(boneName, blenderToJme(new Quaternion(x, y, z, w)));
        }

        poses.put(name, boneDeltas);
        System.out.println("SideStepPoseApplier: added pose \"" + name
            + "\" with " + boneDeltas.size() + " deltas");
    }

    public void startSequence() {
        if (!restPoseCaptured) return;

        deltaFrom.clear();
        for (String bone : WHITELIST) {
            Joint j = rig.get(bone);
            if (j == null) continue;
            Quaternion rest = restPoseRot.get(bone);
            if (rest == null) continue;
            deltaFrom.put(bone, rest.inverse().mult(j.getLocalRotation()));
        }

        active = true;
        stepIdx = 0;
        stepT = 0f;
    }

    public void stopSequence() {
        active = false;
    }

    public boolean isActive() {
        return active;
    }

    public void update(float tpf) {
        if (!active || !restPoseCaptured) return;

        if (stepIdx >= SEQUENCE.size()) {
            stepIdx = 1;
        }

        stepT += tpf;
        SeqStep step = SEQUENCE.get(stepIdx);
        float t = Math.min(stepT / step.dur, 1f);
        float smoothT = smoothstep(t);

        Map<String, Quaternion> targetPose = step.to != null ? poses.get(step.to) : null;

        for (String bone : WHITELIST) {
            Joint j = rig.get(bone);
            if (j == null) continue;

            Quaternion rest = restPoseRot.get(bone);
            if (rest == null) continue;

            Quaternion from = deltaFrom.get(bone);
            if (from == null) from = IDENTITY;

            Quaternion to = getTargetDelta(targetPose, bone);

            j.setLocalRotation(rest.mult(slerp(from, to, smoothT)));
        }

        if (t >= 1f) {
            if (targetPose != null) {
                for (String bone : WHITELIST) {
                    deltaFrom.put(bone, getTargetDelta(targetPose, bone));
                }
            } else {
                for (String bone : WHITELIST) {
                    deltaFrom.put(bone, IDENTITY);
                }
            }
            stepIdx++;
            stepT = 0f;
        }
    }

    private Quaternion getTargetDelta(Map<String, Quaternion> targetPose, String bone) {
        if (targetPose == null) return IDENTITY;
        Quaternion tgt = targetPose.get(bone);
        return tgt != null ? tgt : IDENTITY;
    }

    private static float smoothstep(float t) {
        return t * t * (3f - 2f * t);
    }

    private static Quaternion slerp(Quaternion q1, Quaternion q2, float t) {
        float dot = q1.getX() * q2.getX() + q1.getY() * q2.getY()
                  + q1.getZ() * q2.getZ() + q1.getW() * q2.getW();
        if (dot < 0f) {
            q2 = new Quaternion(-q2.getX(), -q2.getY(), -q2.getZ(), -q2.getW());
            dot = -dot;
        }
        if (dot > 0.9995f) {
            float inv = 1f - t;
            Quaternion r = new Quaternion(
                inv * q1.getX() + t * q2.getX(),
                inv * q1.getY() + t * q2.getY(),
                inv * q1.getZ() + t * q2.getZ(),
                inv * q1.getW() + t * q2.getW()
            );
            r.normalizeLocal();
            return r;
        }
        float theta = FastMath.acos(dot);
        float sinTheta = FastMath.sin(theta);
        float w1 = FastMath.sin((1f - t) * theta) / sinTheta;
        float w2 = FastMath.sin(t * theta) / sinTheta;
        return new Quaternion(
            w1 * q1.getX() + w2 * q2.getX(),
            w1 * q1.getY() + w2 * q2.getY(),
            w1 * q1.getZ() + w2 * q2.getZ(),
            w1 * q1.getW() + w2 * q2.getW()
        );
    }

    private static Quaternion blenderToJme(Quaternion q) {
        return new Quaternion(q.getX(), q.getZ(), -q.getY(), q.getW());
    }
}
