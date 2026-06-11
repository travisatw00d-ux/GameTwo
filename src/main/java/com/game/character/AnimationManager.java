package com.game.character;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;

public class AnimationManager {

    private final HashMap<String, AnimationClip> cache = new HashMap<>();
    private String basePath = "Assets/Animations";

    public AnimationManager() {}

    public AnimationManager(String basePath) {
        this.basePath = basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public AnimationClip getAnimation(String name) {
        AnimationClip clip = cache.get(name);
        if (clip != null) return clip;

        String path = basePath + "/" + name + ".json";
        try {
            String json = new String(Files.readAllBytes(Paths.get(path)));
            clip = AnimationClip.load(name, json);
            cache.put(name, clip);
            System.out.println("AnimationManager: loaded \"" + name + "\" (" + clip.getBoneNames().length + " bones, "
                + clip.getKeyframes().length + " keyframes, " + String.format("%.3f", clip.getDuration()) + "s)");
            return clip;
        } catch (IOException e) {
            System.err.println("AnimationManager: FAILED to load \"" + name + "\" from " + path + " - " + e.getMessage());
            return null;
        }
    }

    public boolean isCached(String name) {
        return cache.containsKey(name);
    }

    public void preload(String... names) {
        for (String name : names) {
            getAnimation(name);
        }
    }

    public void clearCache() {
        cache.clear();
    }
}
