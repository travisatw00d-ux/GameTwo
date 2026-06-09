# Animation System Architecture

## Overview

This game uses a **reset-then-apply** procedural animation system built on jMonkeyEngine 3's `Armature`/`Joint` system. There are no animation clips, no blend trees, and no keyframe assets at runtime. Every animation is a pure function of time that computes joint deltas and applies them deterministically each frame.

---

## Core Pipeline (per frame)

Every frame executes in this strict order:

```
1. Physics updates root
   → BetterCharacterControl moves the physics capsule
2. basePose.apply(rig)
   → Resets ALL 21 skeleton joints to GLB bind pose
3. SkeletonAnimator.update(tpf)
   → Switch on currentAnim → runs one of 4 animation methods
4. FootIKController.update(tpf)
   → Output-only foot adjustment (currently a no-op)
```

**No system after step 4 touches bones.** No system reads bone state as input for step 3.

---

## BasePose — The Single Source of Truth

`BasePose` is constructed once at load time. It captures every joint's local rotation, translation, and scale from the GLB armature at that moment. These values are never modified at runtime.

### Rest pose correction

During construction, `BasePose` applies a one-time rotation to the upper arm bones to convert from T-pose to a relaxed A-pose:

```java
if (name.equals("Upper_Arm.L")) {
    jt.rotation = jt.rotation.mult(fromAngleAxis(1.414f, AXIS_Z));  // ~81° down
}
```

This means `BasePose.apply(rig)` already produces a natural arms-down stance — no runtime stance system is needed.

### Key property: immutability

`BasePose.getRotation(name)` returns a **clone** of the stored quaternion every call. The stored values are never written after construction.

---

## SkeletonAnimator — Animation Engine

### Architecture

```
SkeletonAnimator
├── basePose (immutable GLB snapshot)
├── currentAnim (IDLE / WALK / ATTACK / SIDESTEP)
├── timer (accumulated seconds, resets on state change)
├── update(float tpf)
│   ├── basePose.apply(rig)         ← reset
│   └── switch(currentAnim)
│       ├── updateIdle()
│       ├── updateWalk()
│       ├── updateAttack()
│       └── updateSidestep()
```

### Deterministic rule

Every animation method writes bones using the same pattern:

```java
bone.setLocalRotation(
    basePose.getRotation("BoneName").mult(deltaQuaternion)
);
```

This means:
- **Animation NEVER reads the current bone state** (`getLocalRotation()` is never called)
- Every frame starts from the same bind pose and applies a delta
- No accumulation, no drift, no frame-order dependency
- The output is a pure function of time + bind pose

---

## Per-Animation Details

### IDLE

A subtle breathing sway applied to spine, arms, and head:

```java
float breath = sin(timer * idleSwaySpeed) * idleSwayAmount;
spine → fromAngles(breath * 0.3, 0, breath * 0.5)
arms  → fromAngleAxis(breath * 0.2, AXIS_Z)
head  → fromAngleAxis(breath * 0.1, AXIS_X)
```

### WALK

A single sinusoidal phase drives all limbs:

```java
float cycle = timer * walkSpeed;
float backward = (walkSign >= 0f) ? 1f : 0f;
float swingScale = 1f - backward * 0.4f;
float armScale   = 1f - backward * 0.5f;
float speedScale = 1f + backward * 0.1f;
float phase = cycle * speedScale;
float legAngle = -sin(phase) * walkSwing * swingScale;
float armAngle = -legAngle * armScale;
```

**Key discovery — mirrored bone Z axes.** The left and right leg/shins have mirrored local Z axes. This means applying the **same angle** to both produces **opposite world motion** (correct alternating gait). The arms share this property for Z-axis rotation but use AXIS_X (forward/back swing) with an inverted right arm.

| Bone pair | Rotation axis | Application |
|---|---|---|
| Leg.L / Leg.R | `AXIS_Z` (mirrored) | Same angle → opposite world motion |
| Shin.L / Shin.R | `AXIS_Z` (mirrored) | Same angle → opposite knee bend |
| UpperArm.L / UpperArm.R | `AXIS_X` (not mirrored) | L gets angle, R gets -angle |

**Backward walking** uses amplitude scaling only — no phase inversion, no 180° character rotation:

| Parameter | Forward | Backward |
|---|---|---|
| stride | 1.0× | 0.6× |
| arm swing | 1.0× | 0.5× |
| cadence | 1.0× | 1.1× |

Knee bend is computed as a secondary effect:

```java
float kneeBend = max(0, -swing);  // bends when leg is behind body
float shinAngle = kneeBend * kneeBendStrength;
```

### ATTACK

A 0.5-second animation triggered on left-click. The right arm swings forward with a twist, spine and head follow:

```java
float p = timer / attackDuration;
float swing = sin(p * PI) * attackSwing;
float twist = sin(p * PI) * attackTwist;
// Right arm: swing + twist
// Left arm: 15% follow
// Spine: counter-twist
// Head: follow twist
```

Auto-returns to IDLE when `p >= 1.0`.

### SIDESTEP

The most complex animation. Uses a role-based push-off model:

```java
float phase = (sin(t * sidestepSpeed) + 1) * 0.5;
float pushStrength = smoothstep(0.2, 0.8, phase);

// Role assignment: which leg drives
float pushLeg   = leftPush ? 1 - pushStrength : pushStrength;
float stanceLeg = 1 - pushLeg;

// Root: driven only by push leg
rootPos.x += pushLeg * 0.10;

// Thighs: push leg abducts, stance leg locks
thighPush = base + pushLeg * 0.22;
thighStance = base + stanceLeg * 0.05;

// Shins: stance leg bends slightly
shinStance = stanceLeg * 0.05;

// Feet: push leg follows, stance leg planted
footPush = pushLeg * 0.02;
footStance = 0;
```

The `cachedHipRight` vector captures the hip's lateral axis once on sidestep entry, providing a stable rotation axis that doesn't drift with animation.

---

## FootIK Controller

Currently a no-op. The `applyCorrection()` method has been disabled because modifying hip joints to correct drift caused mesh deformation at the butt/hip vertex weighting. The foot locking logic (detecting when feet are planted vs sliding) still runs and provides hints, but no bone transforms are written.

---

## Key Discovery: Upper Arm Axis Issue

When the sidestep and walk animations were first built, the upper arms were in T-pose (horizontal). The walk cycle rotated arms around `AXIS_Z`, which produced forward/back swing correctly because the Z axis pointed forward in T-pose.

After `BasePose` was modified to rotate the upper arms downward (~81° to a relaxed A-pose), the Z axis no longer pointed forward. `AXIS_Z` rotation now produced lateral (side-to-side) arm motion instead of forward/back swing.

**The fix:** Changed the arm swing axis from `AXIS_Z` to `AXIS_X` in the walk cycle. The X axis maps to shoulder flexion/extension with the arms in a vertical position.

**Lesson:** Rotation axes are **pose-dependent**, not globally consistent. When the bind pose changes (via `BasePose` correction), the animation axes must be re-evaluated per joint. Never assume a global axis like `AXIS_Z` produces the same motion across different bone orientations.

This applies to all future animations: if you add a new animation or modify the bind pose, verify which local bone axis produces the desired world-space motion for each joint.

---

## How to Add a Simple Animation

### 1. Define the animation type

Add to `AnimationType` enum in `SkeletonAnimator.java`:

```java
public enum AnimationType {
    IDLE, WALK, ATTACK, SIDESTEP, YOUR_NEW_TYPE
}
```

### 2. Add a play method

```java
public void playYourNewType() {
    currentAnim = AnimationType.YOUR_NEW_TYPE;
    timer = 0f;
}
```

### 3. Add a private update method

```java
private void updateYourNewType(float tpf) {
    float t = timer * yourSpeed;  // time parameter

    // Compute deltas
    Quaternion armDelta = new Quaternion().fromAngleAxis(angle, AXIS_X);

    // Apply using basePose pattern
    rig.getUpperArmL().setLocalRotation(
        basePose.getRotation("Upper_Arm.L").mult(armDelta));
}
```

### 4. Wire it into the switch

```java
case YOUR_NEW_TYPE -> updateYourNewType(tpf);
```

### 5. Add state transition logic in `PlayerController`

### Golden rules for new animations

- **Never read `getLocalRotation()`** — always start from `basePose.getRotation(name)`
- **Never modify basePose** — it is an immutable snapshot
- **Test axis direction** after adding any rotation — verify it moves in the expected world direction
- **Paired bones (legs/arms)** likely have mirrored coordinate systems — apply the same angle to both and let the mirror produce opposite motion
- **For walk, sidestep, strafe:** the character always faces forward — never rotate the root transform
