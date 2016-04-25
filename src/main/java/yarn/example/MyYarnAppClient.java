package yarn.example;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.util.ClassUtil;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.api.records.*;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.client.api.YarnClientApplication;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.util.Apps;
import org.apache.hadoop.yarn.util.ConverterUtils;
import org.apache.hadoop.yarn.util.Records;

import java.io.File;
import java.util.EnumSet;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Created by Fasten on 2016/4/25.
 * <p/>
 * org.apache.hadoop.mapred.YARNRunner#submitJob()
 * org.apache.hadoop.yarn.applications.distributedshell.Client#run()
 */
public class MyYarnAppClient {

    public static void main(String[] args) {
        try {
            // Create a YarnConfiguration object. This class extends
            // the Hadoop Configuration object so that in addition to
            // loading the standard Hadoop config files, it will also
            // attempt to load yarn-site.xml from the classpath, which
            // contains your cluster configuration.
            YarnConfiguration conf = new YarnConfiguration();
            // Create a new client to communicate with the ResourceManager
            YarnClient yarnClient = YarnClient.createYarnClient();
            yarnClient.init(conf);
            yarnClient.start();

            // Create the YARN application.
            YarnClientApplication app = yarnClient.createApplication();

            ContainerLaunchContext container = Records.newRecord(ContainerLaunchContext.class);

            // Specify the launch command and instruct the process to pipe
            // standard output and error to the container's work directory.
            String amLaunchCmd = String.format(
                    "$JAVA_HOME/bin/java -Xmx256M %s 1>%s/stdout 2>%s/stderr",
                    MyAppMaster.class.getName(),
                    ApplicationConstants.LOG_DIR_EXPANSION_VAR,
                    ApplicationConstants.LOG_DIR_EXPANSION_VAR);

            container.setCommands(Lists.newArrayList(amLaunchCmd));

            // Find the JAR that contains your code
            String jar = ClassUtil.findContainingJar(MyYarnAppClient.class);
            FileSystem fs = FileSystem.get(conf);

            Path src = new Path(jar);
            Path dest = new Path(fs.getHomeDirectory(), src.getName());
            System.out.format("Copying JAR from %s to %s%n", src, dest);
            // Copy the JAR to HDFS
            fs.copyFromLocalFile(src, dest);

            FileStatus jarStat = FileSystem.get(conf).getFileStatus(dest);

            LocalResource appMasterJar = Records.newRecord(LocalResource.class);
            // Create a LocalResource for the JAR and populate the HDFS URI
            // for the JAR
            appMasterJar.setResource(ConverterUtils.getYarnUrlFromPath(dest));
            appMasterJar.setSize(jarStat.getLen());
            appMasterJar.setTimestamp(jarStat.getModificationTime());
            appMasterJar.setType(LocalResourceType.FILE);
            appMasterJar.setVisibility(LocalResourceVisibility.APPLICATION);

            // Add the JAR as a local resource for the container
            container.setLocalResources(ImmutableMap.of("AppMaster.jar", appMasterJar));

            Map<String, String> appMasterEnv = Maps.newHashMap();
            // Add the YARN JARs to the classpath for the ApplicationMaster
            for (String c : conf.getStrings(
                    YarnConfiguration.YARN_APPLICATION_CLASSPATH,
                    YarnConfiguration.DEFAULT_YARN_APPLICATION_CLASSPATH)) {
                Apps.addToEnvironment(appMasterEnv, ApplicationConstants.Environment.CLASSPATH.name(),
                        c.trim());
            }

            Apps.addToEnvironment(appMasterEnv,
                    ApplicationConstants.Environment.CLASSPATH.name(),
                    ApplicationConstants.Environment.PWD.$() + File.separator + "*");
            // Include the classpath as a variable to export to the container's environment
            container.setEnvironment(appMasterEnv);

            Resource capability = Records.newRecord(Resource.class);
            // Specify the amount of memory needed by the ApplicationMaster in megabytes
            capability.setMemory(256);
            // Set the number of virtual cores
            capability.setVirtualCores(1);


            // Finally, set-up ApplicationSubmissionContext for the application
            ApplicationSubmissionContext appContext = app.getApplicationSubmissionContext();
            appContext.setApplicationName("basic-dshell");
            // Set the container properties (specified in the previous code)
            appContext.setAMContainerSpec(container);
            // Set the memory and CPU properties
            appContext.setResource(capability);
            // Specify which scheduler queue to submit the container to
            appContext.setQueue("default");

            ApplicationId appId = appContext.getApplicationId();
            System.out.println("Submitting application " + appId);
            // Tell the ResourceManager to launch the ApplicationMaster
            yarnClient.submitApplication(appContext);

            // Fetch current state of the application from the ResourceManager
            ApplicationReport report = yarnClient.getApplicationReport(appId);
            YarnApplicationState state = report.getYarnApplicationState();

            // Define the terminal ApplicationMaster states.
            EnumSet<YarnApplicationState> terminalStates = EnumSet.of(
                    YarnApplicationState.FINISHED,
                    YarnApplicationState.KILLED,
                    YarnApplicationState.FAILED);

            // Loop until the ApplicationMaster is in a terminal state
            // NEW -> NEW_SAVING -> SUBMITTED -> ACCEPTED -> RUNNING -> FINISHED
            // FAILED or KILLED
            while (!terminalStates.contains(state)) {
                TimeUnit.SECONDS.sleep(1);
                // Re-fetch the application state
                report = yarnClient.getApplicationReport(appId);
                state = report.getYarnApplicationState();
            }

            System.out.printf("Application %s finished with state %s%n",
                    appId, state);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}
