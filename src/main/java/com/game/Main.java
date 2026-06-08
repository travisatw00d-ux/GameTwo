package com.game;

import com.game.character.CharacterFactory;
import com.game.character.CharacterState;
import com.game.character.PlayerController;
import com.game.character.ProceduralHumanoid;
import com.game.character.SkeletonAnimator;
import com.game.world.SceneSetup;
import com.game.world.TrainingDummy;
import com.jme3.app.SimpleApplication;
import com.jme3.bullet.BulletAppState;
import com.jme3.bullet.control.RigidBodyControl;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.asset.plugins.FileLocator;
import com.jme3.scene.Geometry;
import com.jme3.scene.shape.Box;
import com.jme3.system.AppSettings;

public class Main extends SimpleApplication {

    private BulletAppState bulletAppState;
    private PlayerController playerController;
    private ProceduralHumanoid playerHumanoid;
    private SkeletonAnimator playerAnim;
    private TrainingDummy dummy;
    private GraphicsSettings gfx;

    public static void main(String[] args) {
        Main app = new Main();

        // Detect desktop resolution via AWT
        java.awt.GraphicsEnvironment ge = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment();
        java.awt.GraphicsDevice gd = ge.getDefaultScreenDevice();
        java.awt.DisplayMode dm = gd.getDisplayMode();

        // Borderless fullscreen: undecorated window at desktop resolution
        System.setProperty("org.lwjgl.opengl.Window.undecorated", "true");

        AppSettings settings = new AppSettings(true);
        settings.setFrameRate(60);
        settings.setFullscreen(false);
        settings.setResolution(dm.getWidth(), dm.getHeight());
        settings.setSamples(4);
        app.setSettings(settings);
        app.setShowSettings(false);
        app.start();
    }

    @Override
    public void simpleInitApp() {
        // Position window at top-left for borderless fullscreen
        org.lwjgl.opengl.Display.setLocation(0, 0);

        assetManager.registerLocator("Assets", FileLocator.class);

        // Debug: load model and print info
        var model = assetManager.loadModel("Models/MainChar.glb");
        ProceduralHumanoid.printDebugInfo(model);

        // Physics
        bulletAppState = new BulletAppState();
        stateManager.attach(bulletAppState);

        // Scene lighting + shadows
        SceneSetup.apply(this);

        // Ground
        Material groundMat = new Material(assetManager, "Common/MatDefs/Light/PBRLighting.j3md");
        groundMat.setColor("BaseColor", new ColorRGBA(0.30f, 0.35f, 0.30f, 1f));
        groundMat.setFloat("Metallic", 0f);
        groundMat.setFloat("Roughness", 0.9f);

        Geometry ground = new Geometry("Ground", new Box(20f, 0.1f, 20f));
        ground.setMaterial(groundMat);
        ground.setLocalTranslation(0f, -0.1f, 0f);
        rootNode.attachChild(ground);

        RigidBodyControl groundBody = new RigidBodyControl(0f);
        ground.addControl(groundBody);
        bulletAppState.getPhysicsSpace().add(groundBody);

        // Player
        playerHumanoid = CharacterFactory.createHumanoid(assetManager);
        playerHumanoid.attachHair(assetManager, "Models/Hair1.glb");
        playerAnim = new SkeletonAnimator(playerHumanoid.getRig());
        playerController = new PlayerController(playerHumanoid, playerAnim);
        rootNode.attachChild(playerController.getPhysicsNode());
        playerHumanoid.enableShadows();

        playerController.registerInput(inputManager);
        playerController.setupPhysics(bulletAppState);

        // Training dummy
        dummy = new TrainingDummy(assetManager);
        dummy.getCharacter().setLocalTranslation(-3f, 0f, 0f);
        rootNode.attachChild(dummy.getCharacter());

        // Graphics settings — applies high-quality shadows, tone mapping, sky
        gfx = new GraphicsSettings(this);
        gfx.setHighShadows(true, 2048, 4);
        gfx.setToneMapping(true);
        gfx.setFxaa(true);
        gfx.setBloom(false);
        gfx.setSky(false);
        gfx.apply();

        // Camera — initial position (modelHeight measured on first frame)
        flyCam.setEnabled(false);
        Vector3f start = playerHumanoid.getCharacter().getWorldTranslation();
        float camLookY = 0.5f;
        cam.setLocation(start.add(0f, camLookY + 2f, 6f));
        cam.lookAt(start.add(0f, camLookY, 0f), Vector3f.UNIT_Y);
    }

    @Override
    public void simpleUpdate(float tpf) {
        playerController.update(tpf);
        dummy.update(tpf);

        // Camera follows player
        Vector3f pos = playerHumanoid.getCharacter().getWorldTranslation();
        float camLookY = playerController.getModelHeight() * 0.6f;
        cam.setLocation(pos.add(0f, camLookY + 2f, 6f));
        cam.lookAt(pos.add(0f, camLookY, 0f), Vector3f.UNIT_Y);

        // Auto-damage dummy when player attacks nearby
        if (playerController.getState() == CharacterState.ATTACK) {
            float dist = playerHumanoid.getCharacter()
                    .getWorldTranslation().distance(dummy.getCharacter().getLocalTranslation());
            if (dist < 4f) {
                dummy.takeDamage(15f * tpf);
            }
        }
    }
}
