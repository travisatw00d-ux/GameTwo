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
    private final AssetManager assetManager;

    public ProceduralHumanoid(Spatial model, AssetManager assetManager) {
        this(model, null, assetManager);
    }

    public ProceduralHumanoid(Spatial model, CharacterDNA dna, AssetManager assetManager) {
        this.assetManager = assetManager;

        if (!(model instanceof Node rootNode)) {
            throw new IllegalArgumentException("Model must be a Node (scene graph root)");
        }

        SkinningControl skinControl = findSkinningControl(rootNode);
        if (skinControl == null) {
            throw new IllegalStateException("No SkinningControl found in model");
        }

        Armature armature = skinControl.getArmature();
        System.out.println("Armature: " + armature.getJointList().size() + " joints");
        rig = new HumanoidRig(armature);

        // Quick validation: check core joints exist
        String[] checkJoints = {"RL_BoneRoot", "CC_Base_Hip", "CC_Base_Spine01", "CC_Base_Head",
            "CC_Base_L_Upperarm", "CC_Base_L_Hand", "CC_Base_L_Thigh", "CC_Base_L_Foot",
            "CC_Base_R_Upperarm", "CC_Base_R_Hand", "CC_Base_R_Thigh", "CC_Base_R_Foot"};
        for (String name : checkJoints) {
            if (armature.getJoint(name) == null) {
                System.err.println("WARNING: Core joint '" + name + "' not found in armature!");
            }
        }

        armature.saveInitialPose();

        // Fix over-metallic materials from Blender export
        fixMaterials(rootNode);

        this.characterNode = rootNode;
        this.armatureNode = findArmatureNode(rootNode);
    }

    private void fixMaterials(Spatial spatial) {
        if (spatial instanceof Geometry g) {
            com.jme3.material.Material mat = g.getMaterial();
            if (mat != null && mat.getMaterialDef() != null) {
                String def = mat.getMaterialDef().getName();
                if (def != null && def.contains("PBR")) {
                    mat.setFloat("Metallic", 0.0f);
                    String name = g.getName();
                    float roughness = guessRoughness(name);
                    mat.setFloat("Roughness", roughness);

                    com.jme3.material.MatParamTexture normalParam = mat.getTextureParam("NormalMap");
                    boolean needsNormal = normalParam == null || normalParam.getTextureValue() == null;
                    if (needsNormal && assetManager != null) {
                        try {
                            String texName = name.replace("mesh", "mat");
                            com.jme3.texture.Texture normalTex = assetManager.loadTexture(
                                "Models/MainCharRigged.fbm/" + texName + "_Normal.png");
                            mat.setTexture("NormalMap", normalTex);
                            System.out.println("  Set NormalMap from " + texName + "_Normal.png");
                        } catch (Exception e) {
                            System.err.println("  Failed to load normal map: " + e.getMessage());
                        }
                    }

                    logMaterial(g, mat);
                }
            }
        }
        if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                fixMaterials(child);
            }
        }
    }

    private void logMaterial(Geometry g, com.jme3.material.Material mat) {
        System.out.println("Material: " + g.getName());
        System.out.println("  Def: " + mat.getMaterialDef().getName());
        Object metallic = mat.getParamValue("Metallic");
        System.out.println("  Metallic: " + (metallic != null ? metallic : "default"));
        Object roughness = mat.getParamValue("Roughness");
        System.out.println("  Roughness: " + (roughness != null ? roughness : "default"));
        System.out.println("  NormalMap: " + textureParamStr(mat.getTextureParam("NormalMap")));
        System.out.println("  MetallicRoughnessMap: " + textureParamStr(mat.getTextureParam("MetallicRoughnessMap")));
        System.out.println("  LightMap: " + textureParamStr(mat.getTextureParam("LightMap")));
    }

    private static String textureParamStr(com.jme3.material.MatParamTexture param) {
        if (param == null) return "absent";
        if (param.getTextureValue() == null) return "broken";
        com.jme3.asset.AssetKey key = param.getTextureValue().getKey();
        if (key == null) return "loaded (no key)";
        return key.toString();
    }

    private static float guessRoughness(String name) {
        if (name == null) return 0.7f;
        String lower = name.toLowerCase();
        if (lower.contains("eye") || lower.contains("glass") || lower.contains("gloss")
            || lower.contains("gem") || lower.contains("metal")) return 0.1f;
        if (lower.contains("skin") || lower.contains("body") || lower.contains("head")
            || lower.contains("face") || lower.contains("hand") || lower.contains("arm")
            || lower.contains("leg") || lower.contains("neck") || lower.contains("ear")
            || lower.contains("nose") || lower.contains("mouth") || lower.contains("torso")) return 0.75f;
        if (lower.contains("hair") || lower.contains("eyebrow") || lower.contains("lash")
            || lower.contains("beard") || lower.contains("fur")) return 0.55f;
        if (lower.contains("cloth") || lower.contains("fabric") || lower.contains("shirt")
            || lower.contains("pant") || lower.contains("coat") || lower.contains("jacket")
            || lower.contains("boot") || lower.contains("shoe") || lower.contains("sock")
            || lower.contains("belt") || lower.contains("sleeve") || lower.contains("collar")
            || lower.contains("jean") || lower.contains("dress") || lower.contains("skirt"))
            return 0.85f;
        if (lower.contains("mat") || lower.contains("tripo")) return 0.75f;
        return 0.7f;
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

    public Node getArmatureNode() {
        return armatureNode;
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
