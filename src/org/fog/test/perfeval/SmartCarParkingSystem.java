package org.fog.test.perfeval;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.*;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;

import java.util.*;

public class SmartCarParkingSystem {

    static List<FogDevice> fogDevices = new ArrayList<FogDevice>();

    static List<Sensor> sensors = new ArrayList<Sensor>();

    static List<Actuator> actuators = new ArrayList<Actuator>();

    static int numberOfAreas = 1;
    static int numberOfCamerasPerArea = 2;
    static double CAM_TRANSMISSION_TIME = 5;

    private static boolean IS_CLOUD = false;

    static String pictureCaptureKey = "picture-capture";
    static String slotDetectorKey = "slot-detector";
    static String cameraKey = "CAMERA";


    public static void main(String[] args) {
        Log.printLine("Starting smart car parking system...");

        try {

            Log.disable();
            int numberOfUsers = 1;
            Calendar calendar = Calendar.getInstance();
            boolean traceFlag = false;

            CloudSim.init(numberOfUsers, calendar, traceFlag);

            String appId = "DCNS";

            FogBroker fogBroker = new FogBroker("broker");

            Application application = createApplication(appId, fogBroker.getId());
            application.setUserId(fogBroker.getId());



        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted error occurred");
        }

    }

    private static void createFogDevices(int userId, String appId) {
        FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 16*103, 16*83.25);
        cloud.setParentId(-1);
        fogDevices.add(cloud);

        FogDevice proxy = createFogDevice("proxy-server", 2800, 4000, 10000, 10000, 1, 0, 107.339, 83.433);
        proxy.setParentId(cloud.getId());
        proxy.setUplinkLatency(100);
        fogDevices.add(proxy);
        for (int i = 0; i < numberOfAreas; i++) {
            // addArea();
        }

    }


    /**
     * Creates a vanilla fog device
     * @param nodeName name of the device to be used in simulation
     * @param mips MIPS
     * @param ram RAM
     * @param upBw uplink bandwidth
     * @param downBw downlink bandwidth
     * @param level hierarchy level of the device
     * @param ratePerMips cost rate per MIPS used
     * @param busyPower
     * @param idlePower
     * @return
     */
    private static FogDevice createFogDevice(String nodeName, long mips,
                                             int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {

        List<Pe> peList = new ArrayList<Pe>();

        // 3. Create PEs and add these into a list.
        peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating

        int hostId = FogUtils.generateEntityId();
        long storage = 1000000; // host storage
        int bw = 10000;

        PowerHost host = new PowerHost(
                hostId,
                new RamProvisionerSimple(ram),
                new BwProvisionerOverbooking(bw),
                storage,
                peList,
                new StreamOperatorScheduler(peList),
                new FogLinearPowerModel(busyPower, idlePower)
        );

        List<Host> hostList = new ArrayList<Host>();
        hostList.add(host);

        String arch = "x86"; // system architecture
        String os = "Linux"; // operating system
        String vmm = "Xen";
        double time_zone = 10.0; // time zone this resource located
        double cost = 3.0; // the cost of using processing in this resource
        double costPerMem = 0.05; // the cost of using memory in this resource
        double costPerStorage = 0.001; // the cost of using storage in this
        // resource
        double costPerBw = 0.0; // the cost of using bw in this resource
        LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
        // devices by now

        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                arch, os, vmm, host, time_zone, cost, costPerMem,
                costPerStorage, costPerBw);

        FogDevice fogdevice = null;
        try {
            fogdevice = new FogDevice(nodeName, characteristics,
                    new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
        } catch (Exception e) {
            e.printStackTrace();
        }

        fogdevice.setLevel(level);
        return fogdevice;
    }

    /**
     * Function to create the Intelligent Surveillance application in the DDF model.
     * @param appId unique identifier of the application
     * @param userId identifier of the user of the application
     * @return
     */
    @SuppressWarnings({"serial" })
    private static Application createApplication(String appId, int userId) {
        Application application = Application.createApplication(appId, userId);

        application.addAppModule("picture-capture", 10);
        application.addAppModule("slot-detector", 10);

        application.addAppEdge("camera", "slot-detector", 1000, 500, "camera", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("picture-capture", "slot-detector", 1000, 500, "slots", Tuple.UP, AppEdge.MODULE);

        application.addAppEdge("slot-detector", "PTZ_CONTROL", 100, 28, 100, "PTZ_PARAMS", Tuple.UP, AppEdge.ACTUATOR);

        application.addTupleMapping("picture-capture", "camera", "slots", new FractionalSelectivity(1.0));
        application.addTupleMapping("slot-detector", "slots", "PTZ_PARAMS", new FractionalSelectivity(1.0));

        final AppLoop loop = new AppLoop(new ArrayList<String>(){
            {
                add("camera");
                add("picture-capture");
                add("slot-detector");
                add("PTZ_CONTROL");
            }
        });
        List<AppLoop> loops = new ArrayList<AppLoop>(){
            {
                add(loop);
            }
        };
        application.setLoops(loops);

        return  application;
    }

}