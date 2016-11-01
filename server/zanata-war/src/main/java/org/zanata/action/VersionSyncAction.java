/*
 * Copyright 2010, Red Hat, Inc. and individual contributors as indicated by the
 * @author tags. See the copyright.txt file in the distribution for a full
 * listing of individual contributors.
 * 
 * This is free software; you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 * 
 * This software is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA, or see the FSF
 * site: http://www.fsf.org.
 */
package org.zanata.action;

import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.Build;
import com.offbytwo.jenkins.model.BuildWithDetails;
import org.zanata.dao.ProjectDAO;
import org.zanata.model.HAccount;
import org.zanata.model.HProject;
import org.zanata.security.annotations.Authenticated;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author Carlos Munoz <a href="mailto:camunoz@redhat.com">camunoz@redhat.com</a>
 */
@Named("versionSyncAction")
public class VersionSyncAction {

    private static final String JOB_XML_TEMPLATE = "<?xml version='1.0' encoding='UTF-8'?>\n" +
        "<project>\n" +
        "  <actions/>\n" +
        "  <description>This project synchronizes from a git repository and into Zanata</description>\n" +
        "  <keepDependencies>false</keepDependencies>\n" +
        "  <properties/>\n" +
        "  <scm class=\"hudson.plugins.git.GitSCM\" plugin=\"git@3.0.0\">\n" +
        "    <configVersion>2</configVersion>\n" +
        "    <userRemoteConfigs>\n" +
        "      <hudson.plugins.git.UserRemoteConfig>\n" +
        "        <url><<GIT_URL>></url>\n" +
        "      </hudson.plugins.git.UserRemoteConfig>\n" +
        "    </userRemoteConfigs>\n" +
        "    <branches>\n" +
        "      <hudson.plugins.git.BranchSpec>\n" +
        "        <name>*/master</name>\n" +
        "      </hudson.plugins.git.BranchSpec>\n" +
        "    </branches>\n" +
        "    <doGenerateSubmoduleConfigurations>false</doGenerateSubmoduleConfigurations>\n" +
        "    <submoduleCfg class=\"list\"/>\n" +
        "    <extensions/>\n" +
        "  </scm>\n" +
        "  <canRoam>true</canRoam>\n" +
        "  <disabled>false</disabled>\n" +
        "  <blockBuildWhenDownstreamBuilding>false</blockBuildWhenDownstreamBuilding>\n" +
        "  <blockBuildWhenUpstreamBuilding>false</blockBuildWhenUpstreamBuilding>\n" +
        "  <triggers/>\n" +
        "  <concurrentBuild>false</concurrentBuild>\n" +
        "  <builders>\n" +
        "    <hudson.tasks.Shell>\n" +
        "      <command>#/bin/bash\n" +
        "cd server\n" +
        "/opt/zanata-cli/bin/zanata-cli -B push \\\n" +
        "\t--username <<USERNAME>> --key <<API_KEY>> \\\n" +
        "\t--includes &quot;zanata-war/src/main/resources/messages.properties&quot; \\\n" +
        "\t--project <<PROJECT_SLUG>> \\\n" +
        "\t--project-version <<PROJECT_VERSION_SLUG>> \\\n" +
        "    --url &quot;http://zanata:8080/&quot;</command>\n" +
        "    </hudson.tasks.Shell>\n" +
        "  </builders>\n" +
        "  <publishers/>\n" +
        "  <buildWrappers/>\n" +
        "</project>";

    private JenkinsServer jenkins;

    @Inject @Authenticated
    private HAccount authenticatedAccount;

    @Inject
    private ProjectDAO projectDAO;

    @PostConstruct
    public void initializeSyncConnection() throws URISyntaxException {
        jenkins = new JenkinsServer(new URI("http://zanata-sync:8080/"), "admin",
            "admin");
    }

    public void runSync(String projectSlug, String versionSlug) throws Exception {
        jenkins.getJob(getJobSlug(projectSlug, versionSlug))
            .build(true);
    }

    public boolean syncJobExists(String projectSlug, String versionSlug)
        throws IOException {
        return jenkins.getJob( getJobSlug(projectSlug, versionSlug)) != null;
    }

    public void createJob(String projectSlug, String versionSlug)
        throws IOException {
        HProject project = projectDAO.getBySlug(projectSlug);
        // Prepare the job xml
        String jobXml = JOB_XML_TEMPLATE
                .replace("<<GIT_URL>>", project.getSourceCheckoutURL())
                .replace("<<USERNAME>>", authenticatedAccount.getUsername())
                .replace("<<API_KEY>>", authenticatedAccount.getApiKey())
                .replace("<<PROJECT_SLUG>>", projectSlug)
                .replace("<<PROJECT_VERSION_SLUG>>", versionSlug);
        jenkins.createJob(getJobSlug(projectSlug, versionSlug), jobXml, true);
    }

    private String getJobSlug(String projectSlug, String versionSlug) {
        return "ZANATA - (" + projectSlug + ") (" + versionSlug + ")";
    }

    public boolean isJobRunning(String projectSlug, String versionSlug)
        throws IOException {
        Build lastBuild =
            jenkins.getJob(getJobSlug(projectSlug, versionSlug))
                .getLastBuild();
        // Only way to detect the build has not ran yet
        if(lastBuild.getNumber() == -1) {
            return false;
        } else {
            return lastBuild.details().isBuilding();
        }
    }

    public Build getLastJobBuild(String projectSlug, String versionSlug)
        throws IOException {
        return jenkins.getJob(getJobSlug(projectSlug, versionSlug))
            .getLastCompletedBuild();
    }

    public Build getCurrentJobBuild(String projectSlug, String versionSlug)
        throws IOException {
        return jenkins.getJob(getJobSlug(projectSlug, versionSlug))
            .getLastBuild();
    }
}
