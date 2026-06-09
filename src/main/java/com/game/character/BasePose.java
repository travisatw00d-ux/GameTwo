package com.game.character;

import com.jme3.anim.Armature;
import com.jme3.anim.Joint;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import java.util.HashMap;
import java.util.Map;

public class BasePose {

    private static class JointTransform {
        Quaternion rotation;
        final Vector3f translation;
        final Vector3f scale;

        JointTransform(Joint j) {
            this.rotation = j.getLocalRotation().clone();
            this.translation = j.getLocalTranslation().clone();
            this.scale = j.getLocalScale().clone();
        }
    }

    private final Map<String, JointTransform> poses = new HashMap<>();

    private static final Vector3f AXIS_Z = new Vector3f(0f, 0f, 1f);

    public BasePose(Armature armature) {
        System.out.println("=== BasePose captured " + armature.getJointList().size() + " joints ===");
        for (Joint joint : armature.getJointList()) {
            JointTransform jt = new JointTransform(joint);

            // Rotate upper arms downward from T-pose to relaxed A-pose
            String name = joint.getName();
            if (name.equals("Upper_Arm.L")) {
                jt.rotation = jt.rotation.mult(new Quaternion().fromAngleAxis(1.414f, AXIS_Z));
            } else if (name.equals("Upper_Arm.R")) {
                jt.rotation = jt.rotation.mult(new Quaternion().fromAngleAxis(-1.414f, AXIS_Z));
            }

            poses.put(name, jt);
            System.out.println("  " + name
                + " rot=[" + jt.rotation.getX() + ", " + jt.rotation.getY() + ", " + jt.rotation.getZ() + ", " + jt.rotation.getW() + "]"
                + " trans=[" + jt.translation.getX() + ", " + jt.translation.getY() + ", " + jt.translation.getZ() + "]"
                + " scale=[" + jt.scale.getX() + ", " + jt.scale.getY() + ", " + jt.scale.getZ() + "]");
        }
    }

    public Quaternion getRotation(String name) {
        JointTransform jt = poses.get(name);
        return jt != null ? jt.rotation.clone() : new Quaternion();
    }

    public void apply(HumanoidRig rig) {
        for (Joint joint : rig.getArmature().getJointList()) {
            JointTransform jt = poses.get(joint.getName());
            if (jt != null) {
                joint.setLocalRotation(jt.rotation.clone());
                joint.setLocalTranslation(jt.translation.clone());
                joint.setLocalScale(jt.scale.clone());
            }
        }
    }
}
