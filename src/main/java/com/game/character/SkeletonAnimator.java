package com.game.character;

import com.jme3.math.Quaternion;
import com.jme3.scene.Spatial;
import java.util.LinkedHashMap;
import java.util.Map;

public class SkeletonAnimator {

    public enum AnimationType {
        IDLE, WALK, WALKBACK, ATTACK, SIDESTEP
    }

    private final HumanoidRig rig;
    private final BasePose basePose;
    private Spatial modelRoot;
    private AnimationManager animManager;
    private AnimationType currentAnim = AnimationType.IDLE;
    private float timer = 0f;
    private boolean enabled = true;
    private float walkSign = 1f;

    private AnimationClip currentClip;
    private float playbackTime;
    private boolean playing;

    private final Quaternion tmpQuat = new Quaternion();
    private final Quaternion tmpResult = new Quaternion();

    private Map<String, Quaternion> realBindPose;
    private boolean bindPoseCaptured;

    private Map<String, Quaternion> crossfadeFrom;
    private float crossfadeTimer;
    private float crossfadeDuration = 8f / 30f;
    private boolean crossfading;

    public SkeletonAnimator(HumanoidRig rig) {
        this(rig, null);
    }

    public SkeletonAnimator(HumanoidRig rig, AnimationManager animManager) {
        this.rig = rig;
        this.basePose = new BasePose(rig.getArmature());
        this.animManager = animManager;
    }

    public void setAnimationManager(AnimationManager am) { this.animManager = am; }
    public void setModelRoot(Spatial model) { this.modelRoot = model; }
    public void setEnabled(boolean e) { this.enabled = e; }

    public void playIdle() {
        if (currentAnim != AnimationType.IDLE) {
            currentAnim = AnimationType.IDLE;
            play("Idle_Breathing");
        }
    }

    public void playWalk() {
        if (currentAnim != AnimationType.WALK) {
            currentAnim = AnimationType.WALK;
            play("Walking");
        }
    }

    public void playWalkBack() {
        if (currentAnim != AnimationType.WALKBACK) {
            currentAnim = AnimationType.WALKBACK;
            play("WalkingBackwards");
        }
    }

    public void playAttack() {
        currentAnim = AnimationType.ATTACK;
        play("Attack");
    }

    public void playSidestep(boolean left) {
        currentAnim = AnimationType.SIDESTEP;
        play(left ? "LeftSideStep" : "RightSideStep");
    }

    public void playFrontSidestep(boolean left) {
        currentAnim = AnimationType.SIDESTEP;
        play(left ? "LeftFrontSideStep" : "RightFrontSideStep");
    }

    public void playBackSidestep(boolean left) {
        currentAnim = AnimationType.SIDESTEP;
        play(left ? "LeftBackSideStep" : "RightBackSideStep");
    }

    private void play(String name) {
        if (animManager == null) {
            System.err.println("SkeletonAnimator: animManager is null, can't play \"" + name + "\"");
            return;
        }
        AnimationClip newClip = animManager.getAnimation(name);

        if (playing && currentClip != null && newClip != null && newClip != currentClip) {
            startCrossfade();
        }

        currentClip = newClip;
        playbackTime = 0f;
        playing = currentClip != null;
        if (!playing) {
            currentAnim = AnimationType.IDLE;
        }
        System.err.println("SkeletonAnimator: play(\"" + name + "\") -> " + (playing ? "OK" : "FAILED (reverting to IDLE)"));
    }

    public void setWalkSign(boolean forward) {
        walkSign = forward ? 1f : -1f;
    }

    public AnimationType getCurrentAnimation() {
        return currentAnim;
    }

    public boolean shouldLockFoot(boolean isLeftFoot) {
        return true;
    }

    private void startCrossfade() {
        com.jme3.anim.Armature arm = getRealArmature();
        crossfadeFrom = new LinkedHashMap<>();
        for (com.jme3.anim.Joint j : arm.getJointList()) {
            crossfadeFrom.put(j.getName(), j.getLocalRotation().clone());
        }
        crossfadeTimer = 0f;
        crossfading = true;
    }

    private void updateCrossfade(float tpf) {
        if (!crossfading) return;
        crossfadeTimer += tpf;
        float t = Math.min(crossfadeTimer / crossfadeDuration, 1f);

        com.jme3.anim.Armature arm = getRealArmature();
        for (com.jme3.anim.Joint j : arm.getJointList()) {
            Quaternion from = crossfadeFrom.get(j.getName());
            if (from == null) continue;
            tmpQuat.slerp(from, j.getLocalRotation(), t);
            tmpQuat.normalizeLocal();
            j.setLocalRotation(tmpQuat);
        }

        if (t >= 1f) {
            crossfading = false;
            crossfadeFrom = null;
        }
    }

    public void update(float tpf) {
        if (!enabled) return;
        timer += tpf;

        if (playing && currentClip != null) {
            updateAnimation(tpf);
        }

        if (crossfading) {
            updateCrossfade(tpf);
        }
    }

    private void updateAnimation(float tpf) {
        playbackTime += tpf;
        AnimationClip.Keyframe[] keyframes = currentClip.getKeyframes();
        int last = keyframes.length - 1;

        float t = Math.min(playbackTime, currentClip.getDuration());

        int seg = 0;
        for (int i = 0; i < last; i++) {
            if (keyframes[i + 1].time > t) { seg = i; break; }
            seg = i;
        }

        float segDur = keyframes[seg + 1].time - keyframes[seg].time;
        float blend = segDur > 0f ? (t - keyframes[seg].time) / segDur : 0f;
        if (blend < 0f) blend = 0f;
        if (blend > 1f) blend = 1f;

        applyInterpolated(seg, blend);

        if (playbackTime >= currentClip.getDuration()) {
            if (currentClip.isLooping()) {
                playbackTime %= currentClip.getDuration();
            } else {
                playing = false;
            }
        }
    }

    private void captureRealBindPose() {
        realBindPose = new LinkedHashMap<>();
        com.jme3.anim.Armature arm = getRealArmature();
        for (com.jme3.anim.Joint j : arm.getJointList()) {
            realBindPose.put(j.getName(), j.getLocalRotation().clone());
        }
        bindPoseCaptured = true;
    }

    private boolean isIdentity(Quaternion q) {
        return Math.abs(q.getW()) > 0.9999f
            && Math.abs(q.getX()) < 0.001f
            && Math.abs(q.getY()) < 0.001f
            && Math.abs(q.getZ()) < 0.001f;
    }

    private static Quaternion blenderToJme(Quaternion q) {
        return new Quaternion(q.getX(), q.getZ(), -q.getY(), q.getW());
    }

    private void applyInterpolated(int idx, float t) {
        if (!bindPoseCaptured) {
            captureRealBindPose();
        }

        AnimationClip.Keyframe kfA = currentClip.getKeyframes()[idx];
        AnimationClip.Keyframe kfB = currentClip.getKeyframes()[idx + 1];
        String[] boneNames = currentClip.getBoneNames();
        com.jme3.anim.Armature armReal = getRealArmature();

        Quaternion storedRootLean = new Quaternion();
        boolean haveRootLean = false;

        // First pass: find root lean
        for (int i = 0; i < boneNames.length; i++) {
            if ("RL_BoneRoot".equals(boneNames[i])) {
                tmpQuat.slerp(kfA.rotations[i], kfB.rotations[i], t);
                tmpQuat.normalizeLocal();
                storedRootLean.set(blenderToJme(tmpQuat));
                haveRootLean = true;
                break;
            }
        }

        // Second pass: apply all bones
        for (int i = 0; i < boneNames.length; i++) {
            boolean isHip = "CC_Base_Hip".equals(boneNames[i]);
            boolean isRoot = "RL_BoneRoot".equals(boneNames[i]);

            if (isRoot) continue;
            if (!isHip && isIdentity(kfA.rotations[i]) && isIdentity(kfB.rotations[i])) continue;

            com.jme3.anim.Joint j = armReal.getJoint(boneNames[i]);
            if (j == null) continue;
            Quaternion bindRot = realBindPose.get(boneNames[i]);
            if (bindRot == null) continue;

            tmpQuat.slerp(kfA.rotations[i], kfB.rotations[i], t);
            tmpQuat.normalizeLocal();

            if (isHip && haveRootLean) {
                tmpResult.set(bindRot).multLocal(storedRootLean).multLocal(tmpQuat);
            } else {
                tmpResult.set(bindRot).multLocal(tmpQuat);
            }
            j.setLocalRotation(tmpResult);
        }
    }

    private com.jme3.anim.Armature getRealArmature() {
        if (modelRoot != null) {
            com.jme3.anim.SkinningControl sc = modelRoot.getControl(com.jme3.anim.SkinningControl.class);
            if (sc != null) return sc.getArmature();
        }
        return rig.getArmature();
    }
}
