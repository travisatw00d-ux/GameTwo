package com.game.character;

import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.control.BetterCharacterControl;
import com.jme3.input.InputManager;
import com.jme3.input.KeyInput;
import com.jme3.input.MouseInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;

public class PlayerController {

    private final Node characterNode;
    private final Node physicsNode;
    private final SkeletonAnimator animator;
    private final CharacterStateMachine stateMachine;
    private BetterCharacterControl physicsControl;
    private BulletAppState bulletAppState;
    private final FootIKController footIK;
    private final ProceduralHumanoid humanoid;

    private float modelHeight = 2f;
    private boolean firstUpdate = true;
    private boolean moveForward;
    private boolean moveBackward;
    private boolean moveRight;
    private boolean attackPressed;
    private boolean sidestepActive;

    private SideStepPoseApplier poseApplier;

    private float moveSpeed = 5f;
    private float walkSpeedMult = 0.55f;
    private float sidestepSpeedMult = 0.22f;
    private static final Vector3f[] DIR_CACHE = new Vector3f[4];
    static {
        DIR_CACHE[0] = new Vector3f(0f, 0f, -1f);
        DIR_CACHE[1] = new Vector3f(0f, 0f, 1f);
        DIR_CACHE[2] = new Vector3f(-1f, 0f, 0f);
        DIR_CACHE[3] = new Vector3f(1f, 0f, 0f);
    }

    public PlayerController(ProceduralHumanoid humanoid, SkeletonAnimator animator) {
        this.humanoid = humanoid;
        this.characterNode = humanoid.getCharacter();
        this.animator = animator;
        this.stateMachine = new CharacterStateMachine();

        this.physicsNode = new Node("PlayerPhysics");
        physicsNode.attachChild(characterNode);

        this.physicsControl = new BetterCharacterControl(0.3f, 2f, 80f);
        physicsNode.addControl(physicsControl);

        this.footIK = new FootIKController(humanoid.getRig(), characterNode);
    }

    public void setPoseApplier(SideStepPoseApplier a) { this.poseApplier = a; }

    public CharacterStateMachine getStateMachine() {
        return stateMachine;
    }

    public void registerInput(InputManager inputManager) {
        inputManager.addMapping("WalkForward",  new KeyTrigger(KeyInput.KEY_W));
        inputManager.addMapping("WalkBackward", new KeyTrigger(KeyInput.KEY_S));
        inputManager.addMapping("SidestepPose", new KeyTrigger(KeyInput.KEY_A));
        inputManager.addMapping("WalkRight",    new KeyTrigger(KeyInput.KEY_D));
        inputManager.addMapping("Attack",       new MouseButtonTrigger(MouseInput.BUTTON_LEFT));

        inputManager.addListener(actionListener,
                "WalkForward", "WalkBackward", "SidestepPose", "WalkRight", "Attack");
    }

    public void setupPhysics(BulletAppState bulletAppState) {
        this.bulletAppState = bulletAppState;
        bulletAppState.getPhysicsSpace().add(physicsControl);
    }

    public void setMoveSpeed(float moveSpeed) {
        this.moveSpeed = moveSpeed;
    }

    public CharacterState getState() {
        return stateMachine.getCurrentState();
    }

    public void update(float tpf) {
        if (firstUpdate) {
            firstUpdate = false;

            humanoid.getRig().getArmature().update();
            Vector3f footPos = new Vector3f();
            humanoid.getRig().getFootL().getModelTransform().getTranslation(footPos);
            float footY = footPos.y;
            Vector3f headPos = new Vector3f();
            humanoid.getRig().getHead().getModelTransform().getTranslation(headPos);
            float modelH = headPos.y - footY;

            System.err.println("=== FIRST UPDATE MEASUREMENT ===");
            System.err.println("footY=" + footY + " modelHeight=" + modelH);

            if (footY >= 0f) {
                System.err.println("WARNING: footY not negative, forcing -0.8");
                footY = -0.8f;
                modelH = 1.7f;
            }

            float radius = modelH * 0.12f;
            float capHeight = 2f * (-footY - radius);
            if (capHeight < 0.3f) capHeight = 0.3f;
            float spawnY = -footY;

            System.err.println("capHeight=" + capHeight + " radius=" + radius + " spawnY=" + spawnY);

            // Replace placeholder BCC with correctly-sized one
            physicsNode.removeControl(physicsControl);
            this.physicsControl = new BetterCharacterControl(radius, capHeight, 80f);
            physicsNode.addControl(physicsControl);
            bulletAppState.getPhysicsSpace().add(physicsControl);

            // Offset the visual model inside physicsNode so feet rest at Y=0
            characterNode.setLocalTranslation(0f, spawnY, 0f);
            // Start physics node resting on ground (capsule bottom at Y=0)
            physicsControl.warp(Vector3f.ZERO);

            this.modelHeight = modelH;
        }

        if (poseApplier != null && sidestepActive) {
            physicsControl.setWalkDirection(Vector3f.ZERO);
            if (!poseApplier.isActive()) {
                poseApplier.startSequence();
            }
            poseApplier.update(tpf);
            return;
        }

        if (stateMachine.getCurrentState() == CharacterState.DEAD) {
            physicsControl.setWalkDirection(Vector3f.ZERO);
            return;
        }

        Vector3f direction = computeDirection();
        boolean moving = direction.length() > 0.01f;

        if (moving) {
            direction.normalizeLocal();
            float speedMult = switch (stateMachine.getCurrentState()) {
                case WALK     -> walkSpeedMult;
                case SIDESTEP -> sidestepSpeedMult;
                default       -> 1f;
            };
            physicsControl.setWalkDirection(direction.mult(moveSpeed * speedMult));
        } else {
            physicsControl.setWalkDirection(Vector3f.ZERO);
        }

        boolean strafing = moveRight && !(moveForward || moveBackward);

        CharacterState current = stateMachine.getCurrentState();

        switch (current) {
            case IDLE -> {
                if (attackPressed) {
                    animator.playAttack();
                    stateMachine.changeState(CharacterState.ATTACK);
                } else if (strafing) {
                    animator.playSidestep(false);
                    stateMachine.changeState(CharacterState.SIDESTEP);
                } else if (moving) {
                    animator.playWalk();
                    stateMachine.changeState(CharacterState.WALK);
                }
            }
            case WALK -> {
                if (attackPressed) {
                    animator.playAttack();
                    stateMachine.changeState(CharacterState.ATTACK);
                } else if (strafing) {
                    animator.playSidestep(false);
                    stateMachine.changeState(CharacterState.SIDESTEP);
                } else if (!moving) {
                    animator.playIdle();
                    stateMachine.changeState(CharacterState.IDLE);
                }
            }
            case SIDESTEP -> {
                if (attackPressed) {
                    animator.playAttack();
                    stateMachine.changeState(CharacterState.ATTACK);
                } else if (!strafing) {
                    if (moving) {
                        animator.playWalk();
                        stateMachine.changeState(CharacterState.WALK);
                    } else {
                        animator.playIdle();
                        stateMachine.changeState(CharacterState.IDLE);
                    }
                }
            }
            case ATTACK -> {
                if (animator.getCurrentAnimation() == SkeletonAnimator.AnimationType.IDLE) {
                    if (strafing) {
                        animator.playSidestep(false);
                        stateMachine.changeState(CharacterState.SIDESTEP);
                    } else if (moving) {
                        animator.playWalk();
                        stateMachine.changeState(CharacterState.WALK);
                    } else {
                        animator.playIdle();
                        stateMachine.changeState(CharacterState.IDLE);
                    }
                }
            }
            case DEAD -> {}
        }

        animator.setWalkSign(!moveForward || moveBackward);
        animator.update(tpf);
        footIK.setLeftLockHint(animator.shouldLockFoot(true));
        footIK.setRightLockHint(animator.shouldLockFoot(false));
        footIK.update(tpf);


    }

    public Node getPhysicsNode() {
        return physicsNode;
    }

    public float getModelHeight() {
        return modelHeight;
    }

    private Vector3f computeDirection() {
        Vector3f dir = new Vector3f();
        if (moveForward)  dir.addLocal(DIR_CACHE[0]);
        if (moveBackward) dir.addLocal(DIR_CACHE[1]);
        if (moveRight)    dir.addLocal(DIR_CACHE[3]);
        return dir;
    }

    private final ActionListener actionListener = (name, keyPressed, tpf) -> {
        switch (name) {
            case "WalkForward"  -> moveForward  = keyPressed;
            case "WalkBackward" -> moveBackward = keyPressed;
            case "SidestepPose" -> {
                sidestepActive = keyPressed;
                if (!keyPressed && poseApplier != null) {
                    poseApplier.stopSequence();
                }
            }
            case "WalkRight"    -> moveRight    = keyPressed;
            case "Attack"       -> attackPressed = keyPressed;
        }
    };
}
