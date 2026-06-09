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
    private float kneeBendStrength = 0.40f;
    private float walkBob = 0.04f;
    private float idleSwaySpeed = 1.2f;
    private float idleSwayAmount = 0.03f;
    private float attackDuration = 0.5f;
    private float attackSwing = 1.6f;
    private float attackTwist = 0.25f;

    private boolean sidestepLeft = true;
    private float sidestepSpeed = 6.0f;
    private float walkSign = 1f;
    private final Vector3f restRootPos;
    private final Vector3f cachedHipRight = new Vector3f(1f, 0f, 0f);
    private boolean sidestepCached = false;


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
            if (!wasAlready) {
                timer = 0f;
                sidestepCached = false;
            }
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
            basePose.getRotation("Spine").mult(
                new Quaternion().fromAngles(breath * 0.3f, 0f, breath * 0.5f)));

        float armAngle = breath * 0.2f;

        rig.getUpperArmL().setLocalRotation(
            basePose.getRotation("Upper_Arm.L").mult(
                new Quaternion().fromAngleAxis(armAngle, AXIS_Z)));
        rig.getUpperArmR().setLocalRotation(
            basePose.getRotation("Upper_Arm.R").mult(
                new Quaternion().fromAngleAxis(-armAngle, AXIS_Z)));

        rig.getHead().setLocalRotation(
            basePose.getRotation("Head").mult(
                new Quaternion().fromAngleAxis(breath * 0.1f, AXIS_X)));
    }

    // ─── WALK ───────────────────────────────────────────────

    private void updateWalk(float tpf) {
        float cycle = timer * walkSpeed;

        // Backward = amplitude scaling only, no phase inversion or direction branching
        float backward = (walkSign >= 0f) ? 1f : 0f;
        float speedScale = 1.0f + backward * 0.1f;
        float swingScale = 1.0f - backward * 0.4f;
        float armScale   = 1.0f - backward * 0.5f;

        float phase = cycle * speedScale;
        float legAngle = -FastMath.sin(phase) * walkSwing * swingScale;
        float armAngle = -legAngle * armScale;

        float swing = FastMath.sin(phase);
        float kneeBend = Math.max(0f, -swing);
        float shinAngle = kneeBend * kneeBendStrength;

        rig.getLegL().setLocalRotation(
            basePose.getRotation("Leg.L").mult(
                new Quaternion().fromAngleAxis(legAngle, AXIS_Z)));
        rig.getLegR().setLocalRotation(
            basePose.getRotation("Leg.R").mult(
                new Quaternion().fromAngleAxis(legAngle, AXIS_Z)));

        rig.getShinL().setLocalRotation(
            basePose.getRotation("Shin.L").mult(
                new Quaternion().fromAngleAxis(shinAngle, AXIS_Z)));
        rig.getShinR().setLocalRotation(
            basePose.getRotation("Shin.R").mult(
                new Quaternion().fromAngleAxis(shinAngle, AXIS_Z)));

        rig.getUpperArmL().setLocalRotation(
            basePose.getRotation("Upper_Arm.L").mult(
                new Quaternion().fromAngleAxis(armAngle, AXIS_X)));
        rig.getUpperArmR().setLocalRotation(
            basePose.getRotation("Upper_Arm.R").mult(
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

        Quaternion rArm = basePose.getRotation("Upper_Arm.R");
        rArm = rArm.mult(new Quaternion().fromAngleAxis(-swing, AXIS_Z));
        rArm = rArm.mult(new Quaternion().fromAngleAxis(twist, AXIS_X));
        rig.getUpperArmR().setLocalRotation(rArm);

        rig.getUpperArmL().setLocalRotation(
            basePose.getRotation("Upper_Arm.L").mult(
                new Quaternion().fromAngleAxis(swing * 0.15f, AXIS_Z)));

        rig.getSpine().setLocalRotation(
            basePose.getRotation("Spine").mult(
                new Quaternion().fromAngleAxis(-twist * 0.3f, AXIS_X)));
        rig.getHead().setLocalRotation(
            basePose.getRotation("Head").mult(
                new Quaternion().fromAngleAxis(twist * 0.2f, AXIS_X)));
    }

    // ─── SIDESTEP ───────────────────────────────────────────

    private void updateSidestep(float tpf) {
        if (!sidestepCached) {
            rig.getArmature().update();
            cachedHipRight.set(rig.getHips().getModelTransform().getRotation().mult(Vector3f.UNIT_X));
            sidestepCached = true;
        }

        float phase = (FastMath.sin(timer * sidestepSpeed) + 1f) * 0.5f;

        // Asymmetric push curve: leg is active ~0.2–0.8, stance otherwise
        float t0 = (phase - 0.2f) / 0.6f;
        t0 = Math.max(0f, Math.min(1f, t0));
        float pushStrength = t0 * t0 * (3f - 2f * t0);

        // Role assignment: which leg is the active driver
        boolean leftPush = sidestepLeft;

        // pushLeg = 0→1 for the push-leg active phase
        float pushLeg   = leftPush ? 1f - pushStrength : pushStrength;
        float stanceLeg = 1f - pushLeg;

        // ── ROOT: driven by push leg ──
        float rootAmp = 0.10f;
        float rootDir = leftPush ? -1f : 1f;
        Vector3f rootPos = rig.getRoot().getLocalTranslation();
        rootPos.x = restRootPos.x + pushLeg * rootAmp * rootDir;
        rig.getRoot().setLocalTranslation(rootPos);

        // ── HIP: lean toward push leg ──
        float hipTilt = pushLeg * 0.12f * rootDir;
        rig.getHips().setLocalRotation(
            basePose.getRotation("Hips").mult(
                new Quaternion().fromAngleAxis(hipTilt, AXIS_X)));

        // ── THIGHS: independent role drive ──
        float baseThigh = 0.03f;
        float pushDrive = 0.22f;
        float stanceLock = 0.05f;
        float thighL = baseThigh + (leftPush ? pushLeg * pushDrive : stanceLeg * stanceLock);
        float thighR = baseThigh + (leftPush ? stanceLeg * stanceLock : pushLeg * pushDrive);

        // ── SHINS: stance leg bends, push leg straight ──
        float shinBend = 0.05f;
        float shinL = leftPush ? (1f - pushLeg) * shinBend : stanceLeg * shinBend;
        float shinR = leftPush ? stanceLeg * shinBend : (1f - pushLeg) * shinBend;

        // ── FEET: stance planted, push follows ──
        float footFollow = 0.02f;
        float footL = leftPush ? pushLeg * footFollow : 0f;
        float footR = leftPush ? 0f : pushLeg * footFollow;

        // ── Apply ──
        rig.getLegL().setLocalRotation(
            basePose.getRotation("Leg.L").mult(
                new Quaternion().fromAngleAxis(thighL, cachedHipRight)));
        rig.getLegR().setLocalRotation(
            basePose.getRotation("Leg.R").mult(
                new Quaternion().fromAngleAxis(thighR, cachedHipRight)));

        rig.getShinL().setLocalRotation(
            basePose.getRotation("Shin.L").mult(
                new Quaternion().fromAngleAxis(shinL, AXIS_Z)));
        rig.getShinR().setLocalRotation(
            basePose.getRotation("Shin.R").mult(
                new Quaternion().fromAngleAxis(shinR, AXIS_Z)));

        rig.getFootL().setLocalRotation(
            basePose.getRotation("Foot.L").mult(
                new Quaternion().fromAngleAxis(footL, AXIS_Z)));
        rig.getFootR().setLocalRotation(
            basePose.getRotation("Foot.R").mult(
                new Quaternion().fromAngleAxis(footR, AXIS_Z)));
    }

}
