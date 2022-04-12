package org.fog.test.perfeval;

import org.cloudbus.cloudsim.Log;
import org.fog.entities.Actuator;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;

import java.util.*;

public class SmartCarParkingSystem {

    static List<FogDevice> fogDevices = new ArrayList<FogDevice>();

    static List<Sensor> sensors = new ArrayList<Sensor>();

    static List<Actuator> actuators = new ArrayList<Actuator>();

    static int numberOfAreas = 1;
    static int numberOfCamerasPerArea = 2;
    static double CAM_TRANSMISSION_TIME = 5;

    private static boolean IS_CLOUD = false;

    public static void main(String[] args) {
        Log.printLine("Starting smart car parking system...");

    }

}
