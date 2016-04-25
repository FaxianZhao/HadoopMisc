package yarn.example;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.api.protocolrecords.AllocateResponse;
import org.apache.hadoop.yarn.api.records.*;
import org.apache.hadoop.yarn.client.api.AMRMClient;
import org.apache.hadoop.yarn.client.api.NMClient;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.util.Records;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Created by Fasten on 2016/4/25.
 * <p/>
 * org.apache.hadoop.mapreduce.v2.app.MRAppMaster#serviceStart()
 * org.apache.hadoop.yarn.applications.distributedshell.ApplicationMaster#run()
 * <p/>
 * AMRMClientAsync and NMClientAsync are asynchronous clients.
 * AMRMClient and NMClient are synchronous clients.
 */
public class MyAppMaster {

    public static void main(String[] args) {
        try {
            Configuration conf = new YarnConfiguration();

            // Create a client to talk to the ResourceManager
            AMRMClient<AMRMClient.ContainerRequest> rmClient = AMRMClient.createAMRMClient();
            rmClient.init(conf);
            rmClient.start();

            // Register with the ResourceManager
            System.out.println("registerApplicationMaster: pending");
            rmClient.registerApplicationMaster("", 0, "");
            System.out.println("registerApplicationMaster: complete");

            // Create a client to talk to the NodeManagers
            NMClient nmClient = NMClient.createNMClient();
            nmClient.init(conf);
            nmClient.start();

            // Specify the property for the container
            Priority priority = Records.newRecord(Priority.class);
            priority.setPriority(0);

            // Set the resource requirements for the containers
            Resource capability = Records.newRecord(Resource.class);
            capability.setMemory(128);
            capability.setVirtualCores(1);

            // Create the request object that you'll send to the ResourceManager
            AMRMClient.ContainerRequest containerAsk =
                    new AMRMClient.ContainerRequest(capability, null, null, priority);
            System.out.println("adding container ask:" + containerAsk);
            rmClient.addContainerRequest(containerAsk);

            boolean allocatedContainer = false;
            // Loop until you have allocated and launched a container
            while (!allocatedContainer) {
                System.out.println("allocate");
                // Send any pending client-side messages(such as the container request),
                // and pull any messages from the ResourceManager
                AllocateResponse response = rmClient.allocate(0);
                // Loop through any containers that the ResourceManager allocated to you
                for (Container container : response.getAllocatedContainers()) {
                    allocatedContainer = true;
                    // Create a request to launch your container via the NodeManager
                    ContainerLaunchContext ctx = Records.newRecord(ContainerLaunchContext.class);
                    // Specify your command and redirect the outputs to disk
                    ctx.setCommands(Collections.singletonList(String.format(
                            "%s 1>%s/stdout 2>%s/stderr",
                            "/usr/bin/vmstat",
                            ApplicationConstants.LOG_DIR_EXPANSION_VAR,
                            ApplicationConstants.LOG_DIR_EXPANSION_VAR)));
                    System.out.println("Launching container " + container);
                    // Tell the NodeManager to launch your container
                    nmClient.startContainer(container, ctx);
                }
                TimeUnit.SECONDS.sleep(1);
            }

            // Wait for the container to complete
            boolean completedContainer = false;
            // Loop until you receive word that the container has completed
            while (!completedContainer) {
                // Call the allocate method, which also returns completed containers
                System.out.println("allocate (wait)");
                AllocateResponse response = rmClient.allocate(0);
                for (ContainerStatus status : response.getCompletedContainersStatuses()) {
                    // If you received any completed containers, you're done
                    // (because you only launched one container)
                    completedContainer = true;
                    System.out.println("Completed container " + status);
                }
                TimeUnit.SECONDS.sleep(1);
            }

            System.out.println("unregister");
            // Unregister with the ResourceManager, and then exit the process
            rmClient.unregisterApplicationMaster(FinalApplicationStatus.SUCCEEDED, "", "");
            System.out.println("exiting");
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
