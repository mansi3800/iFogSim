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
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

import java.util.*;

public class SmartCarParkingSystem {
    // list of fog devices
    static List<FogDevice> fogDevices = new ArrayList<FogDevice>();

    // list of sensors
    static List<Sensor> sensors = new ArrayList<Sensor>();

    // list of actuators (for displaying output) (LED)
    static List<Actuator> actuators = new ArrayList<Actuator>();

    // number of fog nodes/ parking slots (one fog node for one parking slot)
    static int numberOfAreas = 1;

    // number of cameras in parking slot
    static int numberOfCamerasPerArea = 2;

    // camera takes picture after every x seconds. In our case x is 5
    static double CAM_TRANSMISSION_TIME = 5;

    // if [IS_CLOUD] is true, data is transferred to cloud directly. If false, data is transferred to fog first.
    private static boolean IS_CLOUD = false;


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

            createFogDevices(fogBroker.getId(), appId);

            /*

            Module is processing machine.
            One fog node can contain multiple modules.
            Module is basically a virtual machine. (dedicated to perform a specific simple task)

            In the expressions below, we are adding modules (VM) to fog nodes to perform specific actions.

            * */
            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();

            for (FogDevice fogDevice: fogDevices) {
                if (fogDevice.getName().startsWith("camera") || fogDevice.getName().startsWith("cloud")) {
                    moduleMapping.addModuleToDevice("picture-capture", fogDevice.getName());
                }
            }

            for (FogDevice fogDevice: fogDevices) {
                if (fogDevice.getName().startsWith("router")) {
                    moduleMapping.addModuleToDevice("slot-detector", fogDevice.getName());
                }
            }

            if (IS_CLOUD) {
                moduleMapping.addModuleToDevice("picute-capture", "cloud");
                moduleMapping.addModuleToDevice("slot-detector", "cloud");
            }

            Controller controller = new Controller("master-controller", fogDevices, sensors, actuators);

            if (IS_CLOUD) {
                controller.submitApplication(application, new ModulePlacementMapping(fogDevices, application, moduleMapping));
            } else {
                controller.submitApplication(application, new ModulePlacementEdgewards(fogDevices, sensors, actuators, application, moduleMapping));
            }

            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            Log.printLine("Simulation finished");
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted error occurred");
        }

    }

    private static void createFogDevices(int userId, String appId) {
        // creating cloud device on top of the hierarchy.
        FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 16*103, 16*83.25);
        // setting parentId as -1, indicating there is no parent of this fog node.
        cloud.setParentId(-1);
        fogDevices.add(cloud);

        // creating proxy server as child of cloud.
        FogDevice proxy = createFogDevice("proxy-server", 2800, 4000, 10000, 10000, 1, 0, 107.339, 83.433);
        // setting cloud as parent of this proxy server
        proxy.setParentId(cloud.getId());
        proxy.setUplinkLatency(100);
        fogDevices.add(proxy);

        // creating multiple fog nodes for each area under this proxy server
        for (int i = 0; i < numberOfAreas; i++) {
            addArea(i + "", userId, appId, proxy.getId());
        }
    }

    private static FogDevice addArea(String id, int userId, String appId, int parentId) {
        // here we are assuming that router is acting like the fog device
        FogDevice router = createFogDevice("router-" + id, 2800, 4000, 1000, 10000, 2, 0.0, 107.339, 83.4333);
        fogDevices.add(router);
        router.setUplinkLatency(2);
        router.setParentId(parentId);

        // creating multiple cameras under one router
        for (int i = 0; i < numberOfCamerasPerArea; i++) {
            String cameraId = i + "-of-router-" + id;
            FogDevice camera = addCamera(cameraId, userId, appId, router.getId());
            camera.setUplinkLatency(2);
            fogDevices.add(camera);
        }
        return router;
    }

    private static FogDevice addCamera(String id, int userId, String appId, int parentId) {
        // creating camera fog node under a router
        FogDevice camera = createFogDevice("camera-" + id, 500, 1000, 10000, 10000, 3, 0, 87.53, 82.44);
        camera.setParentId(parentId);

        Sensor sensor = new Sensor("sensor-" + id, "camera", userId, appId, new DeterministicDistribution(CAM_TRANSMISSION_TIME));
        sensors.add(sensor);

        Actuator actuator = new Actuator("ptz-" + id, userId, appId, "PTZ_CONTROL");
        actuators.add(actuator);

        sensor.setGatewayDeviceId(camera.getId());
        sensor.setLatency(40.0);

        actuator.setGatewayDeviceId(parentId);
        actuator.setLatency(1.0);
        return  camera;
    }

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

        application.addAppEdge("camera", "picture-capture", 1000, 500, "camera", Tuple.UP, AppEdge.SENSOR);
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
