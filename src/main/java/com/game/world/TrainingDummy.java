package com.game.world;

import com.game.character.CharacterFactory;
import com.game.character.ProceduralHumanoid;
import com.game.character.SkeletonAnimator;
import com.jme3.asset.AssetManager;
import com.jme3.scene.Node;

public class TrainingDummy {

    static final float MAX_HEALTH = 100f;

    private final ProceduralHumanoid humanoid;
    private final SkeletonAnimator animator;
    private final Node characterNode;
    private float health = MAX_HEALTH;

    public TrainingDummy(AssetManager am) {
        humanoid = CharacterFactory.createHumanoid(am);
        animator = new SkeletonAnimator(humanoid.getRig());
        characterNode = humanoid.getCharacter();
    }

    public Node getCharacter() {
        return characterNode;
    }

    public ProceduralHumanoid getHumanoid() {
        return humanoid;
    }

    public float getHealth() {
        return health;
    }

    public float getMaxHealth() {
        return MAX_HEALTH;
    }

    public void takeDamage(float amount) {
        if (health <= 0f) return;

        amount = Math.min(amount, health);
        health -= amount;

        System.out.printf("TrainingDummy hit for %.1f damage! (%.0f/%.0f HP)%n",
                amount, health, MAX_HEALTH);

        if (health <= 0f) {
            health = 0f;
            System.out.println("TrainingDummy has been defeated!");
        }
    }

    public void update(float tpf) {
        animator.update(tpf);
    }
}
