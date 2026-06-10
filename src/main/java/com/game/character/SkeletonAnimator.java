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

    private boolean enabled = true;
    private boolean sidestepLeft = true;
    private float sidestepSpeed = 6.0f;
    private float walkSign = 1f;
    private final Vector3f restRootPos;
    private final Vector3f cachedHipRight = new Vector3f(1f, 0f, 0f);
    private boolean sidestepCached = false;

    // Foot-trajectory IK fields
    private final Vector3f baseRelL = new Vector3f();
    private final Vector3f baseRelR = new Vector3f();
    private float thighLength = 0.30f;
    private float shinLength = 0.28f;


    private static final Vector3f AXIS_X = new Vector3f(1f, 0f, 0f);
    private static final Vector3f AXIS_Z = new Vector3f(0f, 0f, 1f);

    public SkeletonAnimator(HumanoidRig rig) {
        this.rig = rig;
        this.basePose = new BasePose(rig.getArmature());
        this.restRootPos = rig.getRoot().getLocalTranslation().clone();
    }

    public void setEnabled(boolean e) { this.enabled = e; }

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
            case SIDESTEP: {
                float phase = (FastMath.sin(timer * sidestepSpeed) + 1f) * 0.5f;
                boolean leadFootPlanted = phase > 0.95f;
                boolean leftIsLead = sidestepLeft;
                return isLeftFoot ? (leftIsLead && leadFootPlanted) : (!leftIsLead && leadFootPlanted);
            }
            default:
                return true;
        }
    }

    public void update(float tpf) {
        if (!enabled) return;

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

            // Extract base foot positions relative to hip
            Vector3f hipPos = rig.getHips().getModelTransform().getTranslation(new Vector3f());
            Vector3f footLPos = rig.getFootL().getModelTransform().getTranslation(new Vector3f());
            Vector3f footRPos = rig.getFootR().getModelTransform().getTranslation(new Vector3f());
            baseRelL.set(footLPos).subtractLocal(hipPos);
            baseRelR.set(footRPos).subtractLocal(hipPos);

            // Extract bone lengths for IK
            thighLength = rig.getLegL().getLocalTranslation().length();
            shinLength = rig.getShinL().getLocalTranslation().length();

            sidestepCached = true;
        }

        float cycle = timer * sidestepSpeed;
        float rootDir = sidestepLeft ? -1f : 1f;
        boolean leftPush = !sidestepLeft;

        float sinC = FastMath.sin(cycle);
        float push = Math.max(0, sinC);
        float pull = Math.max(0, -sinC);

        // Wrap cycle to [0, 2π) for lateral progress calculations
        float wrappedCycle = cycle % (2f * (float) Math.PI);
        if (wrappedCycle < 0) wrappedCycle += 2f * (float) Math.PI;

        // ── 1. PELVIS POSITION ──────────────────────────────
        // Body height constant — no vertical lift
        // Body drifts sideways with each step — no oscillating sway
        float stepWidth = 0.12f;
        float maxDrift = 0.5f;
        float drift = FastMath.clamp((cycle / (2f * (float) Math.PI)) * stepWidth, 0, maxDrift) * rootDir;
        Vector3f rootPos = rig.getRoot().getLocalTranslation();
        rootPos.x = restRootPos.x + drift;
        rootPos.y = restRootPos.y;
        rig.getRoot().setLocalTranslation(rootPos);

        // ── 2. FOOT WORLD POSITIONS (stable planted feet) ───
        // Planted foot = fixed world X position (no sliding)
        // Stepping foot = animated world X position
        Vector3f baseRelLead = leftPush ? baseRelR : baseRelL;
        Vector3f baseRelTrail = leftPush ? baseRelL : baseRelR;

        // Planted foot world positions (constant)
        float trailPlantedX = restRootPos.x + baseRelTrail.x;
        float leadPlantedX = restRootPos.x + baseRelLead.x - stepWidth;

        // Lateral progress for stepping foot
        float leadLateral = FastMath.clamp(wrappedCycle / (float) Math.PI, 0, 1);
        float trailLateral = FastMath.clamp((wrappedCycle - (float) Math.PI) / (float) Math.PI, 0, 1);

        // Foot world X positions
        float footLeftWorldX, footRightWorldX;
        if (leftPush) {
            // Left = trail/planted during push, stepping during pull
            // Right = lead/stepping during push, planted during pull
            footLeftWorldX = push * trailPlantedX + pull * (trailPlantedX - 0.10f * trailLateral);
            footRightWorldX = push * (leadPlantedX + stepWidth - stepWidth * leadLateral)
                            + pull * leadPlantedX;
        } else {
            // Right = trail/planted during push, stepping during pull
            // Left = lead/stepping during push, planted during pull
            footLeftWorldX = push * (leadPlantedX + stepWidth - stepWidth * leadLateral)
                           + pull * leadPlantedX;
            footRightWorldX = push * trailPlantedX + pull * (trailPlantedX - 0.10f * trailLateral);
        }

        // Foot relative positions (for IK solve)
        float footLeftX = footLeftWorldX - rootPos.x;
        float footRightX = footRightWorldX - rootPos.x;
        float footLeftY = baseRelTrail.y;  // ground level (constant)
        float footRightY = baseRelLead.y;  // ground level (constant)

        // ── 3. IK SOLVE — THIGH ANGLES ─────────────────────
        float thighL = FastMath.atan2(footLeftX, -footLeftY);
        float thighR = FastMath.atan2(-footRightX, -footRightY);

        rig.getLegL().setLocalRotation(
            basePose.getRotation("Leg.L").mult(
                new Quaternion().fromAngleAxis(thighL, cachedHipRight)));
        rig.getLegR().setLocalRotation(
            basePose.getRotation("Leg.R").mult(
                new Quaternion().fromAngleAxis(thighR, cachedHipRight)));

        // ── 4. SHIN ANGLES (visible bend at max separation) ─
        // Stepping leg: bends MORE as it steps outward (peaks at max separation)
        // Support leg: straightens during push extension
        float shinL, shinR;
        if (leftPush) {
            // Left = trail/support, Right = lead/stepping
            shinL = push * (1f - leadLateral) * kneeBendStrength * 0.5f
                  + pull * trailLateral * kneeBendStrength * 1.0f;
            shinR = push * leadLateral * kneeBendStrength * 1.5f
                  + pull * 0.3f * kneeBendStrength;
        } else {
            // Right = trail/support, Left = lead/stepping
            shinL = push * leadLateral * kneeBendStrength * 1.5f
                  + pull * 0.3f * kneeBendStrength;
            shinR = push * (1f - leadLateral) * kneeBendStrength * 0.5f
                  + pull * trailLateral * kneeBendStrength * 1.0f;
        }

        rig.getShinL().setLocalRotation(
            basePose.getRotation("Shin.L").mult(
                new Quaternion().fromAngleAxis(shinL, AXIS_Z)));
        rig.getShinR().setLocalRotation(
            basePose.getRotation("Shin.R").mult(
                new Quaternion().fromAngleAxis(shinR, AXIS_Z)));

        // ── 5. HIP TILT ─────────────────────────────────────
        float hipTilt = (push - pull) * 0.2f * (leftPush ? -1f : 1f);
        rig.getHips().setLocalRotation(
            basePose.getRotation("Hips").mult(
                new Quaternion().fromAngleAxis(hipTilt, AXIS_X)));

        // ── 6. FEET TILT ────────────────────────────────────
        float footL = (leftPush ? -1f : 1f) * push * 0.05f + (leftPush ? 1f : -1f) * pull * 0.04f;
        float footR = (leftPush ? 1f : -1f) * push * 0.05f + (leftPush ? -1f : 1f) * pull * 0.04f;

        rig.getFootL().setLocalRotation(
            basePose.getRotation("Foot.L").mult(
                new Quaternion().fromAngleAxis(footL, AXIS_Z)));
        rig.getFootR().setLocalRotation(
            basePose.getRotation("Foot.R").mult(
                new Quaternion().fromAngleAxis(footR, AXIS_Z)));

        // ── 7. ARMS ─────────────────────────────────────────
        float armSwing = FastMath.sin(cycle) * 0.14f;
        rig.getUpperArmL().setLocalRotation(
            basePose.getRotation("Upper_Arm.L").mult(
                new Quaternion().fromAngleAxis(armSwing, AXIS_Z)));
        rig.getUpperArmR().setLocalRotation(
            basePose.getRotation("Upper_Arm.R").mult(
                new Quaternion().fromAngleAxis(-armSwing, AXIS_Z)));
    }

}
