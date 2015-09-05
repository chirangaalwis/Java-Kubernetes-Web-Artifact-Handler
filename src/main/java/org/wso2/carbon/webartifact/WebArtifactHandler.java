/*
* Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.wso2.carbon.webartifact;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.docker.JavaWebArtifactImageHandler;
import org.wso2.carbon.docker.interfaces.IDockerImageHandler;
import org.wso2.carbon.exceptions.WebArtifactHandlerException;
import org.wso2.carbon.kubernetes.tomcat.components.pods.TomcatPodHandler;
import org.wso2.carbon.kubernetes.tomcat.components.pods.interfaces.ITomcatPodHandler;
import org.wso2.carbon.kubernetes.tomcat.components.replication_controllers.TomcatReplicationControllerHandler;
import org.wso2.carbon.kubernetes.tomcat.components.replication_controllers.interfaces.ITomcatReplicationControllerHandler;
import org.wso2.carbon.kubernetes.tomcat.components.services.TomcatServiceHandler;
import org.wso2.carbon.kubernetes.tomcat.components.services.interfaces.ITomcatServiceHandler;
import org.wso2.carbon.webartifact.interfaces.IWebArtifactHandler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class WebArtifactHandler implements IWebArtifactHandler {

    private final ITomcatPodHandler podHandler;
    private final ITomcatReplicationControllerHandler replicationControllerHandler;
    private final ITomcatServiceHandler serviceHandler;
    private final IDockerImageHandler imageBuilder;

    private static final int OPERATION_DELAY_IN_MILLISECONDS = 2000;
    private static final Log LOG = LogFactory.getLog(TomcatReplicationControllerHandler.class);

    public WebArtifactHandler(String endpointURL) throws WebArtifactHandlerException {
        podHandler = new TomcatPodHandler(endpointURL);
        replicationControllerHandler = new TomcatReplicationControllerHandler(endpointURL);
        serviceHandler = new TomcatServiceHandler(endpointURL);
        imageBuilder = new JavaWebArtifactImageHandler();
    }

    public void deploy(String tenant, String appName, Path artifactPath, String version, int replicas)
            throws WebArtifactHandlerException {
        String componentName = generateKubernetesComponentName(tenant, appName);
        String dockerImageName;

        try {
            dockerImageName = imageBuilder.buildImage(tenant, appName, version, artifactPath);
            Thread.sleep(OPERATION_DELAY_IN_MILLISECONDS);
            replicationControllerHandler
                    .createReplicationController(componentName, componentName, dockerImageName, replicas);
            serviceHandler.createService(componentName, componentName);
        } catch (Exception exception) {
            String message = String.format("Failed to deploy web artifact[web-artifact]: %s", artifactPath.toString());
            LOG.error(message, exception);
            throw new WebArtifactHandlerException(message, exception);
        }
    }

    public void rollBack(String tenant, String appName, String version, String buildIdentifier)
            throws WebArtifactHandlerException {
        String componentName = generateKubernetesComponentName(tenant, appName);
        try {
            if (replicationControllerHandler.getReplicationController(componentName) != null) {
                int currentReplicas = replicationControllerHandler.
                        getReplicationController(componentName).getSpec().getReplicas();
                replicationControllerHandler.deleteReplicationController(componentName);
                serviceHandler.deleteService(componentName);
                replicationControllerHandler
                        .createReplicationController(componentName, componentName, buildIdentifier, currentReplicas);
                serviceHandler.createService(componentName, componentName);
            }
        } catch (WebArtifactHandlerException exception) {
            String message = String
                    .format("Failed to roll back previous build " + "version[artifact-build]: %s", buildIdentifier);
            LOG.error(message, exception);
            throw new WebArtifactHandlerException(message, exception);
        }
    }

    public void scale(String tenant, String appName, int noOfReplicas) throws WebArtifactHandlerException {
        String componentName = generateKubernetesComponentName(tenant, appName);
        replicationControllerHandler.changeNoOfReplicas(componentName, noOfReplicas);
    }

    public int getNoOfReplicas(String tenant, String appName) throws WebArtifactHandlerException {
        String componentName = generateKubernetesComponentName(tenant, appName);
        return replicationControllerHandler.getNoOfReplicas(componentName);
    }

    public List<String> listExistingBuildArtifacts(String tenant, String appName, String version)
            throws WebArtifactHandlerException {
        List<String> artifactList = new ArrayList<>();
        for (String tag : imageBuilder.getExistingImage(tenant, appName, version).repoTags()) {
            artifactList.add(tag);
        }

        return artifactList;
    }

    public List<String> listMinorBuildArtifactVersions(String tenant, String appName, String version)
            throws WebArtifactHandlerException {
        String upperLimitVersion = replicationControllerHandler
                .getReplicationController(generateKubernetesComponentName(tenant, appName))
                .getSpec().getTemplate().getSpec().getContainers().get(0).getImage();
        List<String> artifactList = listExistingBuildArtifacts(tenant, appName, version);
        List<String> minorArtifactList = new ArrayList<>();
        for(String artifactImageBuild : artifactList) {
            if(artifactImageBuild.compareTo(upperLimitVersion) < 0) {
                minorArtifactList.add(artifactImageBuild);
            }
        }

        return minorArtifactList;
    }

    public void remove(String tenant, String appName, String version) throws WebArtifactHandlerException {
        String componentName = generateKubernetesComponentName(tenant, appName);
        try {
            replicationControllerHandler.deleteReplicationController(componentName);
            podHandler.deleteReplicaPods(tenant, appName);
            serviceHandler.deleteService(componentName);
        } catch (Exception exception) {
            String message = String
                    .format("Failed to remove web artifact[artifact]: %s", tenant + "/" + appName + ":" + version);
            LOG.error(message, exception);
            throw new WebArtifactHandlerException(message, exception);
        }
    }

    public String getServiceAccessIPs(String tenant, String appName, Path artifactPath)
            throws WebArtifactHandlerException {
        String ipMessage;

        ipMessage = String.format("Cluster IP: %s\nNodePort: %s\n\n", serviceHandler
                        .getClusterIP(generateKubernetesComponentName(tenant, appName), getArtifactName(artifactPath)),
                serviceHandler.getNodePortIP(getArtifactName(artifactPath)));

        return ipMessage;
    }

    /**
     * utility method which returns a Kubernetes identifier based on the deploying
     * tenant and app name
     *
     * @param tenant  tenant which deploys the web artifact
     * @param appName name of the web artifact
     * @return Kubernetes identifier based on the deploying tenant and app name
     */
    private String generateKubernetesComponentName(String tenant, String appName) {
        return appName + "-" + tenant;
    }

    /**
     * utility method which returns the name of the web artifact specified
     *
     * @param artifactPath path to the web artifact
     * @return the name of the web artifact specified
     */
    private String getArtifactName(Path artifactPath) {
        String artifactFileName = artifactPath.getFileName().toString();
        return artifactFileName.substring(0, artifactFileName.length() - 4);
    }

}
