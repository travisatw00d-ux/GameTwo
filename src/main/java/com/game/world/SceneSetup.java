package com.game.world;

import com.jme3.app.SimpleApplication;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.renderer.queue.RenderQueue.ShadowMode;

public class SceneSetup {

    public static void apply(SimpleApplication app) {
        AmbientLight ambient = new AmbientLight(new ColorRGBA(0.25f, 0.25f, 0.30f, 1f));
        app.getRootNode().addLight(ambient);

        DirectionalLight sun = new DirectionalLight(
                new Vector3f(-0.4f, -0.7f, -0.3f).normalizeLocal(),
                new ColorRGBA(0.95f, 0.93f, 0.88f, 1f));
        app.getRootNode().addLight(sun);

        DirectionalLight fill = new DirectionalLight(
                new Vector3f(0.3f, -0.2f, 0.5f).normalizeLocal(),
                new ColorRGBA(0.35f, 0.40f, 0.50f, 1f).multLocal(0.3f));
        app.getRootNode().addLight(fill);

        DirectionalLightShadowRenderer dlsr =
                new DirectionalLightShadowRenderer(app.getAssetManager(), 1024, 3);
        dlsr.setLight(sun);
        dlsr.setShadowIntensity(0.5f);
        app.getViewPort().addProcessor(dlsr);
    }

    public static void enableShadows(Spatial... roots) {
        for (Spatial root : roots) {
            applyShadows(root);
        }
    }

    private static void applyShadows(Spatial spatial) {
        spatial.setShadowMode(ShadowMode.CastAndReceive);
        if (spatial instanceof Node node) {
            for (Spatial child : node.getChildren()) {
                applyShadows(child);
            }
        }
    }
}
