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
package org.wso2.carbon.webartifact.interfaces;

import org.wso2.carbon.exceptions.WebArtifactHandlerException;

import java.nio.file.Path;
import java.util.List;

/**
 * a Java interface which orchestrates web artifact deployment in Apache Tomcat Docker containers
 */
public interface IWebArtifactHandler {
    /**
     * deploys the specified web app
     *
     * @param tenant       name of the tenant
     * @param appName      name of the app
     * @param artifactPath uri to the web app resource
     * @param version      deployed version of the artifact
     * @param replicas     number of deployed replicas of the web app
     * @throws WebArtifactHandlerException
     */
    void deploy(String tenant, String appName, Path artifactPath, String version, int replicas)
            throws WebArtifactHandlerException;

    /**
     * rolls back to an existing, older version of the web artifact build
     *
     * @param tenant          name of the tenant
     * @param appName         name of the app
     * @param version         deployed version of the artifact
     * @param buildIdentifier identifier of web artifact build to be newly deployed
     * @throws WebArtifactHandlerException
     */
    void rollBack(String tenant, String appName, String version, String buildIdentifier)
            throws WebArtifactHandlerException;

    /**
     * scale the number of web artifact replicas running
     *
     * @param tenant       name of the tenant
     * @param appName      name of the app
     * @param noOfReplicas latest number of replicas to be deployed
     * @throws WebArtifactHandlerException
     */
    void scale(String tenant, String appName, int noOfReplicas) throws WebArtifactHandlerException;

    /**
     * returns the number of replicas of a particular web artifact running, currently
     *
     * @param tenant  name of the tenant
     * @param appName name of the app
     * @return the number of replicas of a particular web artifact running, currently
     * @throws WebArtifactHandlerException
     */
    int getNoOfReplicas(String tenant, String appName) throws WebArtifactHandlerException;

    /**
     * utility method which returns a list of web artifact build versions under the specified
     * repo and version
     *
     * @param tenant  tenant which deploys the web artifact
     * @param appName name of the web artifact
     * @param version major version of the web artifact
     * @return a list of web artifact build versions under the specified repo and version
     * @throws WebArtifactHandlerException
     */
    List<String> listExistingBuildArtifacts(String tenant, String appName, String version)
            throws WebArtifactHandlerException;

    /**
     * returns a list of web artifact build versions under the specified
     * repo and version which are minor to the currently running build version
     *
     * @param tenant  tenant which deploys the web artifact
     * @param appName name of the web artifact
     * @param version major version of the web artifact
     * @return list of web artifact build versions under the specified repo and version which
     * are minor to the currently running build version
     * @throws WebArtifactHandlerException
     */
    List<String> listMinorBuildArtifactVersions(String tenant, String appName, String version)
            throws WebArtifactHandlerException;

    /**
     * returns a String message of access IPs for the most recently created service
     *
     * @param tenant       tenant which deploys the web artifact
     * @param appName      name of the web artifact
     * @param artifactPath path to the web artifact
     * @return a String message of access IPs for the most recently created service
     * @throws WebArtifactHandlerException
     */
    String getServiceAccessIPs(String tenant, String appName, Path artifactPath) throws WebArtifactHandlerException;

    /**
     * removes the deployed, specified web app
     *
     * @param tenant  name of the tenant
     * @param appName name of the app
     * @param version deployed version of the artifact
     * @throws WebArtifactHandlerException
     */
    void remove(String tenant, String appName, String version) throws WebArtifactHandlerException;

}
