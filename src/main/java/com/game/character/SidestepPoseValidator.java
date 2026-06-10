package com.game.character;

import com.jme3.anim.Joint;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import java.util.*;

public class SidestepPoseValidator {

    private static class BonePose {
        Quaternion rot;
        Vector3f loc;  // non-null, may be zero
    }

    private final HumanoidRig rig;
    private final Node armatureNode;
    private final Map<String, BonePose> poseData = new LinkedHashMap<>();
    private boolean logged = false;
    private final Set<String> warnedMissing = new HashSet<>();

    public SidestepPoseValidator(HumanoidRig rig, Node armatureNode) {
        this.rig = rig;
        this.armatureNode = armatureNode;
    }

    public void parseJson(String json) {
        System.out.println("=== SidestepLift.json Quaternion Conversion (JSON[w,x,y,z] -> JME(x,y,z,w)) ===");

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "\"([\\w.]+)\"\\s*:\\s*\\{\\s*\"rot\"\\s*:\\s*\\[([^\\]]+)\\]\\s*,\\s*\"loc\"\\s*:\\s*\\[([^\\]]+)\\]"
        );
        java.util.regex.Matcher matcher = pattern.matcher(json);

        while (matcher.find()) {
            String name = matcher.group(1);
            String[] rotParts = matcher.group(2).split(",");
            String[] locParts = matcher.group(3).split(",");

            float w = Float.parseFloat(rotParts[0].trim());
            float x = Float.parseFloat(rotParts[1].trim());
            float y = Float.parseFloat(rotParts[2].trim());
            float z = Float.parseFloat(rotParts[3].trim());

            float lx = Float.parseFloat(locParts[0].trim());
            float ly = Float.parseFloat(locParts[1].trim());
            float lz = Float.parseFloat(locParts[2].trim());

            System.out.println(String.format("  %s: JSON[w=%.5f x=%.5f y=%.5f z=%.5f] -> JME(x=%.5f y=%.5f z=%.5f w=%.5f)",
                name, w, x, y, z, x, y, z, w));

            BonePose bp = new BonePose();
            bp.rot = new Quaternion(x, y, z, w);
            bp.loc = new Vector3f(lx, ly, lz);
            poseData.put(name, bp);
        }

        System.out.println("Parsed " + poseData.size() + " bones from SideStepLift.json");
    }

    public void applyPose() {
        for (var entry : poseData.entrySet()) {
            String name = entry.getKey();
            Joint j = rig.get(name);
            if (j == null) {
                if (warnedMissing.add(name)) {
                    System.out.println("WARN: Bone '" + name + "' not found in rig -- skipping");
                }
                continue;
            }
            BonePose bp = entry.getValue();
            j.setLocalRotation(convertBlenderToJme(bp.rot));
            // JSON loc = [0,0,0] means no translation change from rest pose.
            // Only apply non-zero translations to avoid collapsing bone structure.
            if (bp.loc.x != 0f || bp.loc.y != 0f || bp.loc.z != 0f) {
                j.setLocalTranslation(bp.loc.clone());
            }
        }
    }

    // Blender uses Z-up with different bone axis convention than JME (Y-up).
    // This remaps the exported quaternion to JME bone-local space.
    // Calibrated for this rig's GLB import orientation.
    private static Quaternion convertBlenderToJme(Quaternion q) {
        return new Quaternion(q.getX(), q.getZ(), -q.getY(), q.getW());
    }

    public void logDebug() {
        if (logged) return;
        logged = true;

        Vector3f footL = worldPos(rig.getFootL());
        Vector3f footR = worldPos(rig.getFootR());
        Vector3f root  = worldPos(rig.getRoot());
        Vector3f hips  = worldPos(rig.getHips());

        System.out.println("=== SIDESTEP POSE VALIDATION (post-render) ===");
        System.out.println("Foot.L world: " + footL);
        System.out.println("Foot.R world: " + footR);
        System.out.println("Foot distance: " + footL.distance(footR));
        System.out.println("Root world: " + root);
        System.out.println("Hips world: " + hips);
        System.out.println("Leg.L localRot: " + rig.getLegL().getLocalRotation());
        System.out.println("Leg.R localRot: " + rig.getLegR().getLocalRotation());
        System.out.println("Shin.L localRot: " + rig.getShinL().getLocalRotation());
        System.out.println("Shin.R localRot: " + rig.getShinR().getLocalRotation());

        float legDot  = rig.getLegL().getLocalRotation().dot(rig.getLegR().getLocalRotation());
        float shinDot = rig.getShinL().getLocalRotation().dot(rig.getShinR().getLocalRotation());
        System.out.println("Leg.L . Leg.R dot: " + legDot  + " (expect << 1)");
        System.out.println("Shin.L . Shin.R dot: " + shinDot + " (expect << 1)");

        if (footL.equals(footR)) System.err.println("FAIL: Feet at identical world position");
        if (legDot  > 0.99f)     System.err.println("FAIL: Leg rotations nearly identical (symmetry collapse)");
        if (shinDot > 0.99f)     System.err.println("FAIL: Shin rotations nearly identical");
    }

    public boolean isLogged() { return logged; }

    private Vector3f worldPos(Joint j) {
        Vector3f local = new Vector3f();
        j.getModelTransform().getTranslation(local);
        return armatureNode.localToWorld(local, new Vector3f());
    }
}
