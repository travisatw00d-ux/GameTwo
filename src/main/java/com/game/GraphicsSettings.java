package com.game;

import com.jme3.app.SimpleApplication;
import com.jme3.post.FilterPostProcessor;
import com.jme3.post.filters.BloomFilter;
import com.jme3.post.filters.FXAAFilter;
import com.jme3.post.filters.ToneMapFilter;
import com.jme3.post.ssao.SSAOFilter;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.shadow.DirectionalLightShadowRenderer;
import com.jme3.texture.Texture;
import com.jme3.util.SkyFactory;

public class GraphicsSettings {

    private boolean highShadows = true;
    private boolean toneMapping = true;
    private boolean fxaa = true;
    private boolean bloom = false;
    private boolean ssao = true;
    private boolean sky = true;
    private float resolutionScale = 1.0f;

    private final SimpleApplication app;
    private FilterPostProcessor fpp;
    private DirectionalLightShadowRenderer shadowRenderer;

    private int shadowMapSize = 2048;
    private int shadowSplits = 4;

    public GraphicsSettings(SimpleApplication app) {
        this.app = app;
    }

    public void setHighShadows(boolean v, int mapSize, int splits) {
        highShadows = v;
        shadowMapSize = mapSize;
        shadowSplits = splits;
    }
    public void setToneMapping(boolean v) { toneMapping = v; }
    public void setFxaa(boolean v) { fxaa = v; }
    public void setBloom(boolean v) { bloom = v; }
    public void setSsao(boolean v) { ssao = v; }
    public void setSky(boolean v) { sky = v; }
    public void setResolutionScale(float v) { resolutionScale = Math.max(1f, v); }

    public boolean isHighShadows() { return highShadows; }
    public boolean isToneMapping() { return toneMapping; }
    public boolean isFxaa() { return fxaa; }
    public boolean isBloom() { return bloom; }
    public boolean isSky() { return sky; }
    public float getResolutionScale() { return resolutionScale; }

    public void apply() {
        applyShadows();
        applySky();
        applyPostFilters();
    }

    private void applyShadows() {
        // Remove ALL existing shadow renderers from viewport
        var toRemove = new java.util.ArrayList<com.jme3.post.SceneProcessor>();
        for (var proc : app.getViewPort().getProcessors()) {
            if (proc instanceof DirectionalLightShadowRenderer) {
                toRemove.add(proc);
            }
        }
        for (var r : toRemove) app.getViewPort().removeProcessor(r);

        int size = highShadows ? shadowMapSize : 1024;
        int splits = highShadows ? shadowSplits : 3;

        for (var light : app.getRootNode().getLocalLightList()) {
            if (light instanceof com.jme3.light.DirectionalLight dl) {
                shadowRenderer = new DirectionalLightShadowRenderer(
                    app.getAssetManager(), size, splits);
                shadowRenderer.setLight(dl);
                shadowRenderer.setShadowIntensity(0.2f);
                app.getViewPort().addProcessor(shadowRenderer);
                break;
            }
        }
    }

    private void applySky() {
        for (var child : app.getRootNode().getChildren()) {
            if ("_GfxSky".equals(child.getName())) {
                child.removeFromParent();
            }
        }
        if (!sky) return;
        try {
            Texture tex = app.getAssetManager().loadTexture(
                "Common/MatDefs/Sky/Editable/clouds.jpg");
            if (tex == null) return;
            var skyObj = SkyFactory.createSky(app.getAssetManager(),
                tex, SkyFactory.EnvMapType.CubeMap);
            skyObj.setName("_GfxSky");
            skyObj.setQueueBucket(RenderQueue.Bucket.Sky);
            skyObj.setLocalScale(100f);
            app.getRootNode().attachChild(skyObj);
        } catch (com.jme3.asset.AssetNotFoundException e) {
            // Sky texture not available — skip
        }
    }

    public int getRenderWidth(int baseWidth) {
        return (int) (baseWidth * resolutionScale);
    }

    public int getRenderHeight(int baseHeight) {
        return (int) (baseHeight * resolutionScale);
    }

    private void applyPostFilters() {
        if (fpp != null) {
            app.getViewPort().removeProcessor(fpp);
            fpp = null;
        }
        fpp = new FilterPostProcessor(app.getAssetManager());
        boolean active = false;
        if (toneMapping) {
            fpp.addFilter(new ToneMapFilter());
            active = true;
        }
        if (fxaa) {
            fpp.addFilter(new FXAAFilter());
            active = true;
        }
        if (bloom) {
            BloomFilter bf = new BloomFilter(BloomFilter.GlowMode.Objects);
            bf.setBloomIntensity(1.2f);
            fpp.addFilter(bf);
            active = true;
        }
        if (ssao) {
            SSAOFilter ssaoFilter = new SSAOFilter(5f, 0.7f, 1f, 0.02f);
            fpp.addFilter(ssaoFilter);
            active = true;
        }
        if (active) {
            app.getViewPort().addProcessor(fpp);
        }
    }
}
