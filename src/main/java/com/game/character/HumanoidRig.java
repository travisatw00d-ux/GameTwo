package com.game.character;

import com.jme3.anim.Armature;
import com.jme3.anim.Joint;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;

public class HumanoidRig {

    // Required bone names (18 total)
    public static final String ROOT = "Root";
    public static final String HIPS = "Hips";
    public static final String SPINE = "Spine";
    public static final String CHEST = "Chest";
    public static final String NECK = "Neck";
    public static final String HEAD = "Head";
    public static final String UPPER_ARM_L = "Upper_Arm.L";
    public static final String FORE_ARM_L = "ForeArm.L";
    public static final String HAND_L = "Hand.L";
    public static final String UPPER_ARM_R = "Upper_Arm.R";
    public static final String FORE_ARM_R = "ForeArm.R";
    public static final String HAND_R = "Hand.R";
    public static final String LEG_L = "Leg.L";
    public static final String SHIN_L = "Shin.L";
    public static final String FOOT_L = "Foot.L";
    public static final String LEG_R = "Leg.R";
    public static final String SHIN_R = "Shin.R";
    public static final String FOOT_R = "Foot.R";

    public static final String[] ALL_BONES = {
        ROOT, HIPS, SPINE, CHEST, NECK, HEAD,
        UPPER_ARM_L, FORE_ARM_L, HAND_L,
        UPPER_ARM_R, FORE_ARM_R, HAND_R,
        LEG_L, SHIN_L, FOOT_L,
        LEG_R, SHIN_R, FOOT_R
    };

    private final Armature armature;
    private final java.util.Map<String, Joint> joints = new java.util.HashMap<>();

    public HumanoidRig(Armature armature) {
        this.armature = armature;
        for (String name : ALL_BONES) {
            Joint j = armature.getJoint(name);
            if (j == null) {
                System.err.println("WARNING: Joint '" + name + "' not found in armature!");
                continue;
            }
            joints.put(name, j);
        }
        System.out.println("HumanoidRig initialized: " + joints.size() + "/" + ALL_BONES.length + " joints cached");
    }

    public Armature getArmature() {
        return armature;
    }

    public Joint getRoot()        { return joints.get(ROOT); }
    public Joint getHips()        { return joints.get(HIPS); }
    public Joint getSpine()       { return joints.get(SPINE); }
    public Joint getChest()       { return joints.get(CHEST); }
    public Joint getNeck()        { return joints.get(NECK); }
    public Joint getHead()        { return joints.get(HEAD); }
    public Joint getUpperArmL()   { return joints.get(UPPER_ARM_L); }
    public Joint getForeArmL()    { return joints.get(FORE_ARM_L); }
    public Joint getHandL()       { return joints.get(HAND_L); }
    public Joint getUpperArmR()   { return joints.get(UPPER_ARM_R); }
    public Joint getForeArmR()    { return joints.get(FORE_ARM_R); }
    public Joint getHandR()       { return joints.get(HAND_R); }
    public Joint getLegL()        { return joints.get(LEG_L); }
    public Joint getShinL()       { return joints.get(SHIN_L); }
    public Joint getFootL()       { return joints.get(FOOT_L); }
    public Joint getLegR()        { return joints.get(LEG_R); }
    public Joint getShinR()       { return joints.get(SHIN_R); }
    public Joint getFootR()       { return joints.get(FOOT_R); }

    public Joint get(String name) {
        return joints.get(name);
    }

    public void setBoneScale(String name, float uniformScale) {
        Joint j = joints.get(name);
        if (j != null) j.setLocalScale(new Vector3f(uniformScale, uniformScale, uniformScale));
    }

    public void setBoneScale(String name, float x, float y, float z) {
        Joint j = joints.get(name);
        if (j != null) j.setLocalScale(new Vector3f(x, y, z));
    }

    public void setBoneScale(String name, Vector3f scale) {
        Joint j = joints.get(name);
        if (j != null) j.setLocalScale(scale);
    }

    public void setBoneRotation(String name, Quaternion rot) {
        Joint j = joints.get(name);
        if (j != null) j.setLocalRotation(rot);
    }

    public void setBoneTranslation(String name, Vector3f trans) {
        Joint j = joints.get(name);
        if (j != null) j.setLocalTranslation(trans);
    }

    public void resetToInitialPose() {
        armature.applyInitialPose();
    }
}
