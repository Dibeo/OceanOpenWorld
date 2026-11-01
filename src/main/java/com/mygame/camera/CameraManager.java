package com.mygame.camera;

import com.jme3.input.FlyByCamera;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

public class CameraManager {
    private final Camera camera;
    private final FlyByCamera flyCam;

    public CameraManager(Camera camera, FlyByCamera flyCam) {
        this.camera = camera;
        this.flyCam = flyCam;
    }

    public void initialize(float speed, float farPlane) {
        flyCam.setMoveSpeed(speed);
        camera.setFrustumFar(farPlane);
    }

    public void setPosition(Vector3f position) { camera.setLocation(position); }
    public void lookAt(Vector3f target) { camera.lookAt(target, Vector3f.UNIT_Y); }
}
