package com.game.character;

import com.jme3.anim.Joint;
import com.jme3.math.FastMath;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

public class FootIKController {

    private final HumanoidRig rig;
    private final Node characterNode;
    private final Joint footL;
    private final Joint footR;
    private final Joint hips;

    private final FootState left = new FootState();
    private final FootState right = new FootState();

    private float blendSpeed = 12f;
    private float lockHeight = 0.15f;
    private float unlockHeight = 0.25f;
    private float lockSpeed = 0.15f;
    private float unlockSpeed = 0.35f;
    private float groundY = 0f;
    private float maxCorrection = 0.12f;

    private Boolean leftLockHint;
    private Boolean rightLockHint;

    private final Vector3f leftPersist = new Vector3f();
    private final Vector3f rightPersist = new Vector3f();

    private final Vector3f tmpPos = new Vector3f();
    private final Vector3f tmpWorld = new Vector3f();
    private final Vector3f tmpTarget = new Vector3f();
    private final Vector3f tmpCorrection = new Vector3f();

    public FootIKController(HumanoidRig rig, Node characterNode) {
        this.rig = rig;
        this.characterNode = characterNode;
        this.footL = rig.getFootL();
        this.footR = rig.getFootR();
        this.hips = rig.getHips();
    }

    public FootState getLeft()  { return left; }
    public FootState getRight() { return right; }

    public void setBlendSpeed(float v) { blendSpeed = v; }
    public void setLockHeight(float lockY, float unlockY) { lockHeight = lockY; unlockHeight = unlockY; }
    public void setLockSpeed(float lock, float unlock) { lockSpeed = lock; unlockSpeed = unlock; }
    public void setGroundY(float y) { groundY = y; }

    public void setLeftLockHint(Boolean hint) { leftLockHint = hint; }
    public void setRightLockHint(Boolean hint) { rightLockHint = hint; }

    public void update(float tpf) {
        updateFoot(left, footL, tpf, leftLockHint);
        updateFoot(right, footR, tpf, rightLockHint);
        applyCorrection(tpf);
    }

    private void updateFoot(FootState state, Joint foot, float tpf, Boolean hint) {
        foot.getModelTransform().getTranslation(tmpPos);
        characterNode.localToWorld(tmpPos, tmpWorld);

        if (state.firstFrame) {
            state.lastPos.set(tmpPos);
            state.firstFrame = false;
        }

        float dx = tmpPos.x - state.lastPos.x;
        float dz = tmpPos.z - state.lastPos.z;
        float instantSpeed = FastMath.sqrt(dx * dx + dz * dz) / Math.max(tpf, 0.0001f);
        state.speed = state.speed * 0.65f + instantSpeed * 0.35f;
        state.lastPos.set(tmpPos);

        if (hint != null) {
            if (hint && !state.locked) {
                characterNode.worldToLocal(tmpWorld, state.lockedLocal);
                state.lockedNodePos.set(characterNode.getWorldTranslation());
                state.locked = true;
            } else if (!hint && state.locked) {
                state.locked = false;
            }
            return;
        }

        float footY = tmpWorld.y - groundY;
        boolean nearGround = footY <= unlockHeight;

        if (state.locked) {
            if (!nearGround || state.speed > unlockSpeed) {
                state.locked = false;
            }
        } else {
            if (footY <= lockHeight && state.speed < lockSpeed) {
                characterNode.worldToLocal(tmpWorld, state.lockedLocal);
                state.lockedNodePos.set(characterNode.getWorldTranslation());
                state.locked = true;
            }
        }
    }

    private void applyCorrection(float tpf) {
        // Hip drift correction disabled — modifies hips joint translation
        // which deforms the butt/hip mesh (vertex weighting artifact).
        // Foot lock/unlock logic still works via animation hints.
    }

    private void updatePersist(Vector3f persist, FootState state, Joint foot, float t) {
        if (state.locked) {
            driftOf(state, foot, tmpCorrection);
            tmpCorrection.negateLocal();
            tmpCorrection.x = FastMath.clamp(tmpCorrection.x, -maxCorrection, maxCorrection);
            tmpCorrection.z = FastMath.clamp(tmpCorrection.z, -maxCorrection, maxCorrection);
            persist.interpolateLocal(tmpCorrection, t);
        } else {
            persist.interpolateLocal(Vector3f.ZERO, t);
        }
    }

    private void driftOf(FootState state, Joint foot, Vector3f out) {
        characterNode.localToWorld(state.lockedLocal, tmpWorld);
        tmpWorld.subtractLocal(characterNode.getWorldTranslation());
        tmpWorld.addLocal(state.lockedNodePos);
        characterNode.worldToLocal(tmpWorld, tmpTarget);
        foot.getModelTransform().getTranslation(out);
        out.subtractLocal(tmpTarget);
    }

    public static class FootState {
        public boolean locked = false;
        public boolean firstFrame = true;
        public float speed = 0f;
        public final Vector3f lastPos = new Vector3f();
        public final Vector3f lockedLocal = new Vector3f();
        public final Vector3f lockedNodePos = new Vector3f();
    }
}
