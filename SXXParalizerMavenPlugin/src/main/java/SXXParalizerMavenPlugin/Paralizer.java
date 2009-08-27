package SXXParalizerMavenPlugin;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import com.sodasmile.sxxparalizer.SXXParalized;
import com.sodasmile.sxxparalizer.SXXParameters;

/**
 * Goal which runs the paralizer.
 *
 * @goal sxx
 */
public class Paralizer extends AbstractMojo {

    /**
     * Username, same used on all hosts
     * @parameter
     * @required
     */
    private String username;

    /**
     * Password, same used on all hosts
     * @parameter
     * @required
     */
    private String password;

    /**
     * Commands to execute.
     *
     * @parameter
     * @required
     */
    private String[] commands;

    /**
     * Hosts to run commands on.
     *
     * @parameter
     * @required
     */
    private String[] hosts;

    public void execute() throws MojoExecutionException {
        SXXParameters params = new SXXParameters()
                .username(username)
                .password(password)
                .trust(true);
        SXXParalized p = new SXXParalized(params);
        p.setHosts(hosts);
        p.setCommands(commands);
        try {
            p.runCommands();
        } catch (Exception e) {
            throw new MojoExecutionException("SXX Command execution failed", e);
        }
    }
}
