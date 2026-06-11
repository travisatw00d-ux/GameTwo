package com.game.character;

import com.jme3.math.Quaternion;
import com.jme3.scene.Spatial;
import java.util.LinkedHashMap;
import java.util.Map;

public class SkeletonAnimator {

    public enum AnimationType {
        IDLE, WALK, ATTACK, SIDESTEP
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
    private int keyA;
    private boolean playing;

    private final Quaternion tmpQuat = new Quaternion();
    private final Quaternion tmpResult = new Quaternion();

    private Map<String, Quaternion> realBindPose;
    private boolean bindPoseCaptured;

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
            play("Idle");
        }
    }

    public void playWalk() {
        if (currentAnim != AnimationType.WALK) {
            currentAnim = AnimationType.WALK;
            play("Walking");
        }
    }

    public void playAttack() {
        currentAnim = AnimationType.ATTACK;
        play("Attack");
    }

    public void playSidestep(boolean left) {
        currentAnim = AnimationType.SIDESTEP;
        play("Sidestep");
    }

    private void play(String name) {
        if (animManager == null) {
            System.err.println("SkeletonAnimator: animManager is null, can't play \"" + name + "\"");
            return;
        }
        currentClip = animManager.getAnimation(name);
        playbackTime = 0f;
        keyA = 0;
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

    public void update(float tpf) {
        if (!enabled) return;
        timer += tpf;

        if (playing && currentClip != null) {
            updateAnimation(tpf);
        } else {
            // basePose.apply(rig);  // TEST: see if idle visual comes from real armature GLB default
        }
    }

    private void updateAnimation(float tpf) {
        playbackTime += tpf;
        AnimationClip.Keyframe[] keyframes = currentClip.getKeyframes();

        if (playbackTime >= currentClip.getDuration()) {
            if (currentClip.isLooping()) {
                playbackTime %= currentClip.getDuration();
                keyA = 0;
            } else {
                playing = false;
                applyKeyframe(keyframes.length - 1);
                return;
            }
        }

        float t = playbackTime;
        int last = keyframes.length - 1;

        while (keyA < last - 1 && keyframes[keyA + 1].time <= t) {
            keyA++;
        }
        while (keyA > 0 && keyframes[keyA].time > t) {
            keyA--;
        }

        AnimationClip.Keyframe kfA = keyframes[keyA];
        AnimationClip.Keyframe kfB = keyframes[keyA + 1];
        float segDur = kfB.time - kfA.time;
        float blend = segDur > 0f ? (t - kfA.time) / segDur : 0f;
        if (blend < 0f) blend = 0f;
        if (blend > 1f) blend = 1f;

        applyInterpolated(keyA, blend);
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

    private void applyInterpolated(int idx, float t) {
        if (!bindPoseCaptured) {
            captureRealBindPose();
        }

        AnimationClip.Keyframe kfA = currentClip.getKeyframes()[idx];
        AnimationClip.Keyframe kfB = currentClip.getKeyframes()[idx + 1];
        String[] boneNames = currentClip.getBoneNames();
        com.jme3.anim.Armature armReal = getRealArmature();

        for (int i = 0; i < boneNames.length; i++) {
            if (isIdentity(kfA.rotations[i])) continue;

            com.jme3.anim.Joint j = armReal.getJoint(boneNames[i]);
            if (j == null) continue;
            Quaternion bindRot = realBindPose.get(boneNames[i]);
            if (bindRot == null) continue;

            tmpQuat.slerp(kfA.rotations[i], kfB.rotations[i], t);
            
            String name = boneNames[i];
            if (name.equals("CC_Base_L_Upperarm") || name.equals("CC_Base_R_Upperarm")) {
                // Undo blenderToJme axis swap for upper arm — raw Blender delta
                tmpResult.set(bindRot).multLocal(
                    new Quaternion(tmpQuat.getX(), -tmpQuat.getZ(), tmpQuat.getY(), tmpQuat.getW()));
            } else {
                tmpResult.set(bindRot).multLocal(tmpQuat);
            }
            j.setLocalRotation(tmpResult);
        }
    }

    private void applyKeyframe(int idx) {
        if (!bindPoseCaptured) {
            captureRealBindPose();
        }

        AnimationClip.Keyframe kf = currentClip.getKeyframes()[idx];
        String[] boneNames = currentClip.getBoneNames();
        com.jme3.anim.Armature armReal = getRealArmature();

        for (int i = 0; i < boneNames.length; i++) {
            if (isIdentity(kf.rotations[i])) continue;

            com.jme3.anim.Joint j = armReal.getJoint(boneNames[i]);
            if (j == null) continue;
            Quaternion bindRot = realBindPose.get(boneNames[i]);
            if (bindRot == null) continue;

            String name = boneNames[i];
            if (name.equals("CC_Base_L_Upperarm") || name.equals("CC_Base_R_Upperarm")) {
                tmpResult.set(bindRot).multLocal(
                    new Quaternion(kf.rotations[i].getX(), -kf.rotations[i].getZ(), kf.rotations[i].getY(), kf.rotations[i].getW()));
            } else {
                tmpResult.set(bindRot).multLocal(kf.rotations[i]);
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
