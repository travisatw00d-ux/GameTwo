package com.game.character;

import java.util.Random;

public class CharacterDNA {

    private final long seed;
    private final float height;
    private final float shoulderWidth;
    private final float armLength;
    private final float legLength;
    private final float torsoWidth;
    private final float torsoHeight;
    private final float headSize;

    private CharacterDNA(long seed, float height, float shoulderWidth, float armLength,
                         float legLength, float torsoWidth, float torsoHeight, float headSize) {
        this.seed = seed;
        this.height = height;
        this.shoulderWidth = shoulderWidth;
        this.armLength = armLength;
        this.legLength = legLength;
        this.torsoWidth = torsoWidth;
        this.torsoHeight = torsoHeight;
        this.headSize = headSize;
    }

    public static CharacterDNA natural() {
        return new CharacterDNA(0, 1f, 1f, 1f, 1f, 1f, 1f, 1f);
    }

    public static CharacterDNA generate(long seed) {
        Random rng = new Random(seed);

        float height = lerp(0.6f, 1.8f, rng.nextFloat());
        float legLength = lerp(0.5f, 1.8f, rng.nextFloat());
        float torsoHeight = lerp(0.5f, 1.8f, rng.nextFloat());
        float torsoWidth = lerp(0.5f, 2.2f, rng.nextFloat());
        float shoulderWidth = lerp(0.5f, 2.5f, rng.nextFloat());
        float armLength = lerp(0.5f, 2.0f, rng.nextFloat());
        float headSize = lerp(0.5f, 2.0f, rng.nextFloat());

        return new CharacterDNA(seed, height, shoulderWidth, armLength,
                                legLength, torsoWidth, torsoHeight, headSize);
    }

    public long getSeed()              { return seed; }
    public float getHeight()           { return height; }
    public float getShoulderWidth()    { return shoulderWidth; }
    public float getArmLength()        { return armLength; }
    public float getLegLength()        { return legLength; }
    public float getTorsoWidth()       { return torsoWidth; }
    public float getTorsoHeight()      { return torsoHeight; }
    public float getHeadSize()         { return headSize; }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
