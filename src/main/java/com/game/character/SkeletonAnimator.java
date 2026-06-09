package com.game.character;

import com.jme3.anim.Joint;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

public class SkeletonAnimator {

    public enum AnimationType {
        IDLE, WALK, ATTACK, SIDESTEP
    }

    private final HumanoidRig rig;
    private final BasePose basePose;
    private AnimationType currentAnim = AnimationType.IDLE;
    private float timer = 0f;

    private float walkSpeed = 10f;
    private float walkSwing = 0.55f;
    private float walkBob = 0.04f;
    private float idleSwaySpeed = 1.2f;
    private float idleSwayAmount = 0.03f;
    private float attackDuration = 0.5f;
    private float attackSwing = 1.6f;
    private float attackTwist = 0.25f;

    private boolean sidestepLeft = true;
    private float sidestepSpeed = 1.5f;
    private float walkSign = 1f;
    private final Vector3f restRootPos;

    private static final Vector3f AXIS_X = new Vector3f(1f, 0f, 0f);
    private static final Vector3f AXIS_Z = new Vector3f(0f, 0f, 1f);

    public SkeletonAnimator(HumanoidRig rig) {
        this.rig = rig;
        this.basePose = new BasePose(rig.getArmature());
        this.restRootPos = rig.getRoot().getLocalTranslation().clone();
    }

    public void playIdle() {
        if (currentAnim != AnimationType.IDLE) {
            currentAnim = AnimationType.IDLE;
            timer = 0f;
        }
    }

    public void playWalk() {
        if (currentAnim != AnimationType.WALK) {
            currentAnim = AnimationType.WALK;
            timer = 0f;
        }
    }

    public void playAttack() {
        currentAnim = AnimationType.ATTACK;
        timer = 0f;
    }

    public void playSidestep(boolean left) {
        if (currentAnim != AnimationType.SIDESTEP || sidestepLeft != left) {
            boolean wasAlready = currentAnim == AnimationType.SIDESTEP;
            currentAnim = AnimationType.SIDESTEP;
            sidestepLeft = left;
            if (!wasAlready) timer = 0f;
        }
    }

    public void setWalkSign(boolean forward) {
        walkSign = forward ? 1f : -1f;
    }

    public AnimationType getCurrentAnimation() {
        return currentAnim;
    }

    public boolean shouldLockFoot(boolean isLeftFoot) {
        switch (currentAnim) {
            case IDLE:
            case ATTACK:
                return true;
            case WALK: {
                float cycle = timer * walkSpeed;
                float sin = FastMath.sin(cycle) * -walkSign;
                boolean leftIsBehind = sin < 0f;
                return isLeftFoot ? leftIsBehind : !leftIsBehind;
            }
            case SIDESTEP:
                return isLeftFoot ? !sidestepLeft : sidestepLeft;
            default:
                return true;
        }
    }

    public void update(float tpf) {
        timer += tpf;

        basePose.apply(rig);

        switch (currentAnim) {
            case IDLE -> updateIdle(tpf);
            case WALK -> updateWalk(tpf);
            case ATTACK -> updateAttack(tpf);
            case SIDESTEP -> updateSidestep(tpf);
        }
    }

    // ─── IDLE ───────────────────────────────────────────────

    private void updateIdle(float tpf) {
        float breath = FastMath.sin(timer * idleSwaySpeed) * idleSwayAmount;

        rig.getSpine().setLocalRotation(
            rig.getSpine().getLocalRotation().mult(
                new Quaternion().fromAngles(breath * 0.3f, 0f, breath * 0.5f)));

        float armAngle = breath * 0.2f;

        rig.getUpperArmL().setLocalRotation(
            rig.getUpperArmL().getLocalRotation().mult(
                new Quaternion().fromAngleAxis(armAngle, AXIS_Z)));
        rig.getUpperArmR().setLocalRotation(
            rig.getUpperArmR().getLocalRotation().mult(
                new Quaternion().fromAngleAxis(-armAngle, AXIS_Z)));

        rig.getHead().setLocalRotation(
            rig.getHead().getLocalRotation().mult(
                new Quaternion().fromAngleAxis(breath * 0.1f, AXIS_X)));
    }

    // ─── WALK ───────────────────────────────────────────────

    private void updateWalk(float tpf) {
        float cycle = timer * walkSpeed;
        float dir = walkSign < 0f ? 1f : -1f;

        // All paired bones (legs, shins, arms) have mirrored local Z axes.
        // Apply the SAME angle to both sides — the mirroring produces opposite world motion.
        float legAngle = -FastMath.sin(cycle) * walkSwing * dir;
        float armAngle = -legAngle * (dir > 0f ? 1.3f : 0.7f);
        float shinK = FastMath.clamp(-legAngle * (0.40f / walkSwing), 0f, 0.40f);

        rig.getLegL().setLocalRotation(
            rig.getLegL().getLocalRotation().mult(
                new Quaternion().fromAngleAxis(legAngle, AXIS_Z)));
        rig.getLegR().setLocalRotation(
            rig.getLegR().getLocalRotation().mult(
                new Quaternion().fromAngleAxis(legAngle, AXIS_Z)));

        rig.getShinL().setLocalRotation(
            rig.getShinL().getLocalRotation().mult(
                new Quaternion().fromAngleAxis(shinK, AXIS_Z)));
        rig.getShinR().setLocalRotation(
            rig.getShinR().getLocalRotation().mult(
                new Quaternion().fromAngleAxis(shinK, AXIS_Z)));

        rig.getUpperArmL().setLocalRotation(
            rig.getUpperArmL().getLocalRotation().mult(
                new Quaternion().fromAngleAxis(armAngle, AXIS_X)));
        rig.getUpperArmR().setLocalRotation(
            rig.getUpperArmR().getLocalRotation().mult(
                new Quaternion().fromAngleAxis(-armAngle, AXIS_X)));
    }

    // ─── ATTACK ─────────────────────────────────────────────

    private void updateAttack(float tpf) {
        float p = timer / attackDuration;

        if (p >= 1f) {
            timer = 0f;
            currentAnim = AnimationType.IDLE;
            return;
        }

        float swing = FastMath.sin(p * FastMath.PI) * attackSwing;
        float twist = FastMath.sin(p * FastMath.PI) * attackTwist;

        Quaternion rArm = rig.getUpperArmR().getLocalRotation();
        rArm = rArm.mult(new Quaternion().fromAngleAxis(-swing, AXIS_Z));
        rArm = rArm.mult(new Quaternion().fromAngleAxis(twist, AXIS_X));
        rig.getUpperArmR().setLocalRotation(rArm);

        rig.getUpperArmL().setLocalRotation(
            rig.getUpperArmL().getLocalRotation().mult(
                new Quaternion().fromAngleAxis(swing * 0.15f, AXIS_Z)));

        rig.getSpine().setLocalRotation(
            rig.getSpine().getLocalRotation().mult(
                new Quaternion().fromAngleAxis(-twist * 0.3f, AXIS_X)));
        rig.getHead().setLocalRotation(
            rig.getHead().getLocalRotation().mult(
                new Quaternion().fromAngleAxis(twist * 0.2f, AXIS_X)));
    }

    // ─── SIDESTEP ───────────────────────────────────────────

    private void updateSidestep(float tpf) {
        float cycle = timer * sidestepSpeed;
        float t = cycle % 1.0f;

        float phase = t / 0.2f;
        int idx = (int) phase;
        float frac = phase - idx;
        float e = smoothstep(frac);

        float[] rootShiftK = { 0f,    -0.20f,  -0.20f,  -0.10f, 0f,    0f };
        float[] hipTiltK   = { 0f,     0.10f,   0.10f,   0.05f,  0f,    0f };
        float[] thighRAbdK = { 0f,     0f,      0.35f,   0.18f,  0f,    0f };
        float[] shinRK     = { 0f,     0f,      0.25f,   0.15f,  0f,    0f };
        float[] footRK     = { 0f,     0f,     -0.08f,   0f,     0f,    0f };
        float[] shinLK     = { 0f,     0.10f,   0.22f,   0.22f,  0.30f, 0f };
        float[] thighLAbdK = { 0f,     0f,      0f,      0f,    -0.35f, 0f };
        float[] footLK     = { 0f,     0f,      0f,      0f,    -0.08f, 0f };
        float[] footLiftK  = { 0f,     0.35f,   0.45f,   0.10f,  0f,    0f };

        int k = Math.min(idx, 4);

        float rootShift = lerp(rootShiftK[k], rootShiftK[k + 1], e);
        float hipTilt   = lerp(hipTiltK[k],   hipTiltK[k + 1],   e);
        float thighRAbd = lerp(thighRAbdK[k], thighRAbdK[k + 1], e);
        float shinR     = lerp(shinRK[k],     shinRK[k + 1],     e);
        float footR     = lerp(footRK[k],     footRK[k + 1],     e);
        float shinL     = lerp(shinLK[k],     shinLK[k + 1],     e);
        float thighLAbd = lerp(thighLAbdK[k], thighLAbdK[k + 1], e);
        float footL     = lerp(footLK[k],     footLK[k + 1],     e);
        float footLift  = lerp(footLiftK[k],  footLiftK[k + 1],  e);

        if (sidestepLeft) {
            rootShift = -rootShift;
            hipTilt = -hipTilt;
            float tmp = thighLAbd; thighLAbd = thighRAbd; thighRAbd = tmp;
            tmp = shinL; shinL = -shinR; shinR = -tmp;
            tmp = footL; footL = -footR; footR = -tmp;
        }

        if (sidestepLeft) {
            shinL += footLift;
        } else {
            shinR -= footLift;
        }

        float bodyWidth = 0.3f;
        Vector3f rootPos = rig.getRoot().getLocalTranslation();
        rootPos.x = restRootPos.x + rootShift * bodyWidth;
        rig.getRoot().setLocalTranslation(rootPos);

        rig.getHips().setLocalRotation(
            rig.getHips().getLocalRotation().mult(
                new Quaternion().fromAngleAxis(hipTilt, AXIS_X)));

        rig.getLegL().setLocalRotation(
            rig.getLegL().getLocalRotation().mult(
                new Quaternion().fromAngleAxis(thighLAbd, AXIS_Z)));
        rig.getLegR().setLocalRotation(
            rig.getLegR().getLocalRotation().mult(
                new Quaternion().fromAngleAxis(thighRAbd, AXIS_Z)));

        rig.getShinR().setLocalRotation(
            rig.getShinR().getLocalRotation().mult(
                new Quaternion().fromAngleAxis(shinR, AXIS_Z)));
        rig.getFootR().setLocalRotation(
            rig.getFootR().getLocalRotation().mult(
                new Quaternion().fromAngleAxis(footR, AXIS_Z)));

        rig.getShinL().setLocalRotation(
            rig.getShinL().getLocalRotation().mult(
                new Quaternion().fromAngleAxis(shinL, AXIS_Z)));
        rig.getFootL().setLocalRotation(
            rig.getFootL().getLocalRotation().mult(
                new Quaternion().fromAngleAxis(footL, AXIS_Z)));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float smoothstep(float t) {
        return t * t * (3f - 2f * t);
    }
}
