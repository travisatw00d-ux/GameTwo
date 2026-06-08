package com.game.character;

import com.jme3.asset.AssetManager;
import com.jme3.scene.Spatial;

public class CharacterFactory {

    private static final String MODEL_PATH = "Models/MainChar.glb";

    public static ProceduralHumanoid createHumanoid(AssetManager am) {
        Spatial model = am.loadModel(MODEL_PATH);
        return new ProceduralHumanoid(model);
    }
}
