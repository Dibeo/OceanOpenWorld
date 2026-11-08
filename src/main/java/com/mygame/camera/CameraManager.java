package com.mygame.camera;

import com.jme3.input.FlyByCamera;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;

/** Applies the configured movement speed and view settings to the engine camera. */
public class CameraManager {
    private final Camera camera;
    private final FlyByCamera flyCam;

    /**
     * Creates a manager for the active render camera and fly camera controller.
     *
     * @param camera render camera whose position and frustum are configured
     * @param flyCam controller receiving the movement-speed setting
     */
    public CameraManager(Camera camera, FlyByCamera flyCam) {
        this.camera = camera;
        this.flyCam = flyCam;
    }

    /**
     * Sets the fly-camera movement speed and camera far clipping plane.
     *
     * @param speed movement speed in world units per second
     * @param farPlane far clipping distance in world units; must satisfy the camera's constraints
     */
    public void initialize(float speed, float farPlane) {
        flyCam.setMoveSpeed(speed);
        camera.setFrustumFar(farPlane);
    }

    /**
     * Sets the camera's world-space location.
     *
     * @param position new world-space camera position
     */
    public void setPosition(Vector3f position) { camera.setLocation(position); }

    /**
     * Aims the camera at a world-space target using the world Y axis as up.
     *
     * @param target world-space point to look at
     */
    public void lookAt(Vector3f target) { camera.lookAt(target, Vector3f.UNIT_Y); }
}
