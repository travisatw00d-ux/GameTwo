# How to Copy Poses from Blender into jME3

## Coordinate System

| Space | X | Y | Z |
|---|---|---|---|
| Blender | Right | Forward | Up |
| jME3 | Right | Up | Forward (negated) |

Blender is Z-up, jME3 is Y-up. All bone rotations exported from Blender must be axis-remapped before use in jME3.

## JSON Format

Export per-bone pose data from Blender as:

```json
{
  "BoneName": {
    "rot": [w, x, y, z],
    "loc": [x, y, z]
  }
}
```

- Rotation quaternion in Blender component order `[w, x, y, z]`
- Translation in Blender coordinate space `[x, y, z]`

## Loading in Code (SideStepPoseApplier)

### Step 1 — Parse

Read JSON `[w, x, y, z]` and store in JME's `(x, y, z, w)` constructor order:

```java
float w = Float.parseFloat(parts[0]);
float x = Float.parseFloat(parts[1]);
float y = Float.parseFloat(parts[2]);
float z = Float.parseFloat(parts[3]);

RawPose rp = new RawPose();
rp.rot = new Quaternion(x, y, z, w);  // JME component order
```

### Step 2 — Skip identity bones

If `w >= 0.9999` and `x,y,z ≈ 0`, the bone is at its rest pose. **Skip it** — don't override the bind pose with identity. This preserves the model's default bone orientation (e.g., legs pointing downward instead of snapping to the bone's raw local axis).

### Step 3 — Apply with axis conversion

When setting the rotation on a jME joint, apply the Blender→jME axis conversion:

```java
Quaternion blenderToJme(Quaternion q) {
    return new Quaternion(q.getX(), q.getZ(), -q.getY(), q.getW());
}
```

This remaps: Blender X→jME X, Blender Z→jME Y, -Blender Y→jME Z.

### Step 4 — Compose with bind pose (critical)

The exported JSON rotations are **deltas** relative to the Blender rest pose, not absolute bone-local rotations. The bind pose stores the absolute orientation that points each bone in the correct direction (e.g., thighs down, spine up).

Always compose the delta on top of the bind pose:

```java
armature.applyInitialPose();  // reset all bones to GLB bind pose

for (each delta bone) {
    Quaternion bindRot = capturedBindPose.get(boneName);
    Quaternion delta = blenderToJme(jsonDelta);
    joint.setLocalRotation(bindRot.mult(delta));
}
```

Without this step, near-identity deltas replace the bind pose, causing bones to snap to their raw local axis direction (typically upward).

## Full Pipeline Summary

```
Blender (Z-up)                     jME3 (Y-up)
─────────────────────────          ──────────────────────
Rest pose (large rotations)  ───>  Bind pose (applyInitialPose)
           ↕                                    ↕
JSON delta (small rotations)  ───>  blenderToJme(delta)
           ↕                                    ↕
Target pose                            bindRot * delta
```

## Rig Bone Names

| HumanoidRig constant | Bone |
|---|---|
| `ROOT` | Root |
| `HIPS` | Hips |
| `SPINE` | Spine |
| `CHEST` | Chest |
| `NECK` | Neck |
| `HEAD` | Head |
| `LEG_L` / `LEG_R` | Leg.L / Leg.R |
| `SHIN_L` / `SHIN_R` | Shin.L / Shin.R |
| `FOOT_L` / `FOOT_R` | Foot.L / Foot.R |
| `UPPER_ARM_L` / `UPPER_ARM_R` | Upper_Arm.L / Upper_Arm.R |
| `LOWER_ARM_L` / `LOWER_ARM_R` | Lower_Arm.L / Lower_Arm.R |
| `HAND_L` / `HAND_R` | Hand.L / Hand.R |
| `CLAVICLE_L` / `CLAVICLE_R` | Clavicle.L / Clavicle.R |

## SideStepPoseApplier API

```java
// 1. Create (captures bind pose rotations automatically)
SideStepPoseApplier applier = new SideStepPoseApplier(rig);

// 2. Load a pose JSON
applier.parseJson(jsonString);

// 3. Apply every frame (or once if static)
applier.applyPose();
```

The whitelist in `WHITELIST` controls which bones the applier touches. Currently set to lower body only (`Hips`, `Spine`, `Leg.L/R`, `Shin.L/R`, `Foot.L/R`). Add more bones to the set to extend to other body parts.

## Common Pitfalls

| Symptom | Likely cause |
|---|---|
| Legs snap upward through body | JSON delta applied without bind pose composition |
| Mesh does not move at all | Rig joints not connected to SkinningControl (check `sameObj` in diagnostic) |
| Limbs rotate around wrong axes | Missing `blenderToJme()` axis conversion |
| Upper body collapses / arms flatten | Identity rotations applied to upper-body bones (skip identity, or don't include in whitelist) |
| Pose matches but looks mirrored | Quaternion component order wrong — JME constructor is `(x, y, z, w)`, not `(w, x, y, z)` |
