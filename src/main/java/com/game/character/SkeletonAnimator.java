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
    private boolean firstFrame = true;

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

        if (firstFrame) {
            firstFrame = false;
            rig.getArmature().update();

            String[] armJoints = {"Clavicle.L", "Upper_Arm.L", "Lower_Arm.L", "Hand.L",
                                  "Clavicle.R", "Upper_Arm.R", "Lower_Arm.R", "Hand.R"};
            Vector3f[] pos = new Vector3f[8];
            for (int i = 0; i < armJoints.length; i++) {
                Joint j = rig.get(armJoints[i]);
                pos[i] = j != null ? j.getModelTransform().getTranslation(new Vector3f()) : Vector3f.ZERO;
            }

            System.out.println("[ARM_CHAIN] L: Clavicle=" + pos[0]
                + " UpperArm=" + pos[1] + " LowerArm=" + pos[2] + " Hand=" + pos[3]);
            System.out.println("[ARM_CHAIN] R: Clavicle=" + pos[4]
                + " UpperArm=" + pos[5] + " LowerArm=" + pos[6] + " Hand=" + pos[7]);

            Vector3f ueL = pos[2].subtract(pos[1]).normalizeLocal();
            Vector3f ehL = pos[3].subtract(pos[2]).normalizeLocal();
            Vector3f ueR = pos[6].subtract(pos[5]).normalizeLocal();
            Vector3f ehR = pos[7].subtract(pos[6]).normalizeLocal();

            System.out.println("[ARM_CHAIN] L: UpperArm\u2192Elbow=" + ueL + " Elbow\u2192Hand=" + ehL);
            System.out.println("[ARM_CHAIN] R: UpperArm\u2192Elbow=" + ueR + " Elbow\u2192Hand=" + ehR);
        }

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

        boolean forward = walkSign < 0f;
        float dir = forward ? -1f : 1f;

        float leftLeg  = FastMath.sin(cycle) * walkSwing * dir;
        float rightLeg = FastMath.sin(cycle + FastMath.PI) * walkSwing * dir;
        float aSwing = forward ? walkSwing * 1.3f : walkSwing * 0.7f;
        float leftArm  = FastMath.sin(cycle + FastMath.PI) * aSwing;
        float rightArm = FastMath.sin(cycle) * aSwing;

        float liftStrength = 0.40f;
        float leftShin = FastMath.clamp(FastMath.sin(cycle) * dir * liftStrength, 0f, liftStrength);
        float rightShin = FastMath.clamp(
            FastMath.sin(cycle + FastMath.PI) * dir * liftStrength, 0f, liftStrength);

        rig.getLegL().setLocalRotation(
            rig.getLegL().getLocalRotation().mult(
                new Quaternion().fromAngleAxis(leftLeg, AXIS_Z)));
        rig.getLegR().setLocalRotation(
            rig.getLegR().getLocalRotation().mult(
                new Quaternion().fromAngleAxis(rightLeg, AXIS_Z)));

        rig.getShinL().setLocalRotation(
            rig.getShinL().getLocalRotation().mult(
                new Quaternion().fromAngleAxis(leftShin, AXIS_Z)));
        rig.getShinR().setLocalRotation(
            rig.getShinR().getLocalRotation().mult(
                new Quaternion().fromAngleAxis(rightShin, AXIS_Z)));

        rig.getUpperArmL().setLocalRotation(
            rig.getUpperArmL().getLocalRotation().mult(
                new Quaternion().fromAngleAxis(leftArm, AXIS_Z)));
        rig.getUpperArmR().setLocalRotation(
            rig.getUpperArmR().getLocalRotation().mult(
                new Quaternion().fromAngleAxis(rightArm, AXIS_Z)));
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
