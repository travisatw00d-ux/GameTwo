package com.game.character;

import com.jme3.anim.Armature;
import com.jme3.anim.Joint;
import com.jme3.anim.SkinningControl;
import com.jme3.asset.AssetManager;
import com.jme3.math.Matrix4f;
import com.jme3.math.Vector3f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Mesh;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.VertexBuffer;
import com.jme3.util.BufferUtils;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

public class ProceduralHumanoid {

    private final Node characterNode;
    private final Node armatureNode;
    private final HumanoidRig rig;

    public ProceduralHumanoid(Spatial model) {
        this(model, null);
    }

    public ProceduralHumanoid(Spatial model, CharacterDNA dna) {

        if (!(model instanceof Node rootNode)) {
            throw new IllegalArgumentException("Model must be a Node (scene graph root)");
        }

        SkinningControl skinControl = findSkinningControl(rootNode);
        if (skinControl == null) {
            throw new IllegalStateException("No SkinningControl found in model");
        }

        Armature armature = skinControl.getArmature();
        System.out.println("Armature joints: " + armature.getJointList().size());
        for (Joint j : armature.getJointList()) {
            System.out.println("  Joint: " + j.getName() + " (id=" + j.getId() + ")");
        }

        rig = new HumanoidRig(armature);

        // DEBUG: print loaded joint rotations to verify rest pose
        System.out.println("=== Joint rest-pose rotations ===");
        String[] checkJoints = {"Root", "Hips", "Spine", "Chest", "Neck", "Head",
            "Clavicle.L", "Upper_Arm.L",             "Lower_Arm.L", "Hand.L",
            "Clavicle.R", "Upper_Arm.R", "Lower_Arm.R", "Hand.R"};
        for (String name : checkJoints) {
            Joint j = armature.getJoint(name);
            if (j != null) {
                com.jme3.math.Quaternion q = j.getLocalRotation();
                System.out.println("  " + name + ": [" + q.getX() + ", " + q.getY() + ", " + q.getZ() + ", " + q.getW() + "]");
            } else {
                System.out.println("  " + name + ": JOINT NOT FOUND");
            }
        }

        armature.saveInitialPose();

        // Fix over-metallic materials from Blender export
        fixMaterials(rootNode);

        this.characterNode = rootNode;
        this.armatureNode = findArmatureNode(rootNode);
    }

    private static void fixMaterials(Spatial spatial) {
        if (spatial instanceof Geometry g) {
            com.jme3.material.Material mat = g.getMaterial();
            if (mat != null && mat.getMaterialDef() != null) {
                String def = mat.getMaterialDef().getName();
                if (def != null && def.contains("PBR")) {
                    mat.setFloat("Metallic", 0.0f);
                    mat.setFloat("Roughness", 0.8f);
                }
            }
        }
        if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                fixMaterials(child);
            }
        }
    }

    public void attachHair(AssetManager am, String modelPath) {
        Spatial hairModel = am.loadModel(modelPath);

        // Remove the hair's own SkinningControl — it has its own armature
        // reference that would conflict with the character's master armature.
        SkinningControl hairSkin = hairModel.getControl(SkinningControl.class);
        if (hairSkin != null) {
            hairModel.removeControl(hairSkin);
            System.out.println("Removed hair's SkinningControl");
        }
        // Also search children for any SkinningControl
        removeSkinningControlFromTree(hairModel);

        Geometry hairGeo = findGeometry(hairModel);
        if (hairGeo == null) return;

        Mesh hairMesh = hairGeo.getMesh();

        // Print skinning info for debugging
        VertexBuffer biBuf = hairMesh.getBuffer(VertexBuffer.Type.BoneIndex);
        VertexBuffer bwBuf = hairMesh.getBuffer(VertexBuffer.Type.BoneWeight);
        System.out.println("Hair JOINTS_0=" + (biBuf != null ? biBuf.getData().limit() + " elements" : "null")
            + " WEIGHTS_0=" + (bwBuf != null ? bwBuf.getData().limit() + " elements" : "null"));

        // Bake the hair node's translation into vertex positions so the
        // character's armature inverse bind matrices work correctly.
        if (hairModel instanceof Node hairRoot) {
            com.jme3.math.Vector3f t = hairRoot.getLocalTranslation();
            if (t.x != 0f || t.y != 0f || t.z != 0f) {
                FloatBuffer posBuf = hairMesh.getFloatBuffer(VertexBuffer.Type.Position);
                if (posBuf != null) {
                    posBuf.rewind();
                    for (int i = 0; i < hairMesh.getVertexCount() * 3; i += 3) {
                        posBuf.put(i,   posBuf.get(i)   + t.x);
                        posBuf.put(i+1, posBuf.get(i+1) + t.y);
                        posBuf.put(i+2, posBuf.get(i+2) + t.z);
                    }
                }
            }
            hairRoot.setLocalTranslation(0f, 0f, 0f);
        }

        hairMesh.updateBound();
        hairMesh.updateCounts();
        hairGeo.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);

        // Attach to the character's armature node so the SkinningControl
        // processes the hair using its existing JOINTS_0/WEIGHTS_0 buffers.
        // The bone indices in JOINTS_0 match the character's armature
        // joint ordering because both were exported from the same Blender file.
        armatureNode.attachChild(hairGeo);

        System.out.println("Hair attached to armatureNode: " + modelPath
            + " (" + hairMesh.getVertexCount() + " verts)");
    }

    private static void removeSkinningControlFromTree(Spatial spatial) {
        SkinningControl sc = spatial.getControl(SkinningControl.class);
        if (sc != null) spatial.removeControl(sc);
        if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                removeSkinningControlFromTree(child);
            }
        }
    }

    private static Geometry findGeometry(Spatial spatial) {
        if (spatial instanceof Geometry g) return g;
        if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                Geometry result = findGeometry(child);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static Node findArmatureNode(Spatial spatial) {
        if (spatial.getControl(SkinningControl.class) != null && spatial instanceof Node node) {
            return node;
        }
        if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                Node result = findArmatureNode(child);
                if (result != null) return result;
            }
        }
        return null;
    }

    private static SkinningControl findSkinningControl(Spatial spatial) {
        SkinningControl ctrl = spatial.getControl(SkinningControl.class);
        if (ctrl != null) return ctrl;
        if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                ctrl = findSkinningControl(child);
                if (ctrl != null) return ctrl;
            }
        }
        return null;
    }

    public Node getCharacter() {
        return characterNode;
    }

    public HumanoidRig getRig() {
        return rig;
    }

    public void enableShadows() {
        characterNode.setShadowMode(RenderQueue.ShadowMode.CastAndReceive);
    }

    public float getFootY() {
        rig.getArmature().update();
        Vector3f footPos = new Vector3f();
        rig.getFootL().getModelTransform().getTranslation(footPos);
        return footPos.y;
    }

    public float getModelHeight() {
        rig.getArmature().update();
        Vector3f footPos = new Vector3f();
        rig.getFootL().getModelTransform().getTranslation(footPos);
        Vector3f headPos = new Vector3f();
        rig.getHead().getModelTransform().getTranslation(headPos);
        return headPos.y - footPos.y;
    }

    public static void printDebugInfo(Spatial model) {
        System.out.println("=== Model Debug Info ===");
        printSpatialTree(model, 0);

        SkinningControl sc = findSkinningControl(model);
        if (sc != null) {
            System.out.println("SkinningControl found on model");
            Armature a = sc.getArmature();
            System.out.println("Armature joints (" + a.getJointList().size() + "):");
            for (Joint j : a.getJointList()) {
                String parentName = j.getParent() != null ? j.getParent().getName() : "(none)";
                System.out.println("  Joint: " + j.getName() + " (id=" + j.getId() + ", parent=" + parentName + ")");
            }
        } else {
            System.out.println("No SkinningControl found on model");
        }
    }

    private static void printSpatialTree(Spatial spatial, int depth) {
        String indent = "  ".repeat(depth);
        String type = spatial instanceof Node ? "Node" : "Geometry";
        System.out.println(indent + type + ": " + spatial.getName() + " (" + spatial.getClass().getSimpleName() + ")");
        if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                printSpatialTree(child, depth + 1);
            }
        }
    }
}
