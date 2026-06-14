package com.game.world;

import com.jme3.app.SimpleApplication;
import com.jme3.app.state.AbstractAppState;
import com.jme3.environment.EnvironmentCamera;
import com.jme3.environment.LightProbeFactory;
import com.jme3.environment.generation.JobProgressAdapter;
import com.jme3.light.LightProbe;
import com.jme3.math.Vector3f;
import com.jme3.scene.Spatial;

public class EnvironmentSetup {

    public static void setup(SimpleApplication app, Spatial rootNode) {
        EnvironmentCamera envCam = new EnvironmentCamera(256, new Vector3f(0f, 2f, 0f));
        app.getStateManager().attach(envCam);

        app.getStateManager().attach(new AbstractAppState() {
            private boolean baking = false;
            private boolean baked = false;

            @Override
            public void update(float tpf) {
                if (baked) {
                    app.getStateManager().detach(this);
                    return;
                }
                if (baking) return;
                baking = true;

                LightProbeFactory.makeProbe(envCam, rootNode,
                    new JobProgressAdapter<LightProbe>() {
                        @Override
                        public void done(LightProbe result) {
                            result.setPosition(new Vector3f(0f, 2f, 0f));
                            app.getRootNode().addLight(result);
                            baked = true;
                            System.out.println("LightProbe baked and added to scene");
                        }
                    });
            }
        });
    }
}
