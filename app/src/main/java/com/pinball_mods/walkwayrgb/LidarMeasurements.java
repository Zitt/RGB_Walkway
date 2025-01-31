package com.pinball_mods.walkwayrgb;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.apache.commons.math3.util.FastMath.abs;

public class LidarMeasurements {
    private AtomicInteger lidar_cm1 = new AtomicInteger(0);
    private AtomicInteger lidar_cm2 = new AtomicInteger(0);

    private AtomicLong lidar_ts1 = new AtomicLong(System.currentTimeMillis());
    private AtomicLong lidar_ts2 = new AtomicLong(System.currentTimeMillis());

    private double velocity_i = 0.0;
    private double velocity_f = 0.0;

    private double acceleration = 0.0;

    public void setNewDistance( int nval) {
        lidar_cm1.set( lidar_cm2.get());
        lidar_ts1.set( lidar_ts2.get());
        lidar_cm2.set(nval);
        lidar_ts2.set(System.currentTimeMillis());
    }

    public Integer deltaX() {
        return lidar_cm2.get() - lidar_cm1.get();
    }

    public Long deltaT() {
        return lidar_ts2.get() - lidar_ts1.get();
    }

    public void clearVals() {
        velocity_i = 0.0;
        velocity_f = 0.0;
        acceleration = 0.0;
    }

    public double getFinalVelocity() {
        velocity_i = velocity_f;
        if (deltaT() == 0.0) return Double.MAX_VALUE;
        double dT = deltaT() / 1000;

        velocity_f = (2*deltaX()/dT) - velocity_i;

        if ( abs(velocity_i) >= Double.MAX_VALUE) {
            velocity_f = (2*deltaX()/dT);
            velocity_i = 0.0;
        }

        acceleration = (velocity_f - velocity_i) / dT;

        return velocity_f;
    }

    public double getAcceleration() {
        return acceleration;
    }
}
