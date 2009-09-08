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

import com.sodasmile.sxxparalizer.SXXParalized;
import com.sodasmile.sxxparalizer.SXXParameters;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Goal which runs the paralizer.
 *
 * @goal sxx
 */
public class Paralizer extends AbstractMojo {

    /**
     * Username, same used on all hosts
     *
     * @parameter
     * @required
     */
    private String username;

    /**
     * Password, same used on all hosts
     *
     * @parameter
     * @required
     */
    private String password;

    /**
     * File containing commands. File with commands to execute.
     *
     * @parameter
     */
    private File commandsFile;

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
        String[] commands = slurpCommands(commandsFile);
        p.setCommands(commands);
        try {
            p.runCommands();
        } catch (Exception e) {
            throw new MojoExecutionException("SXX Command execution failed", e);
        }
    }

    private String[] slurpCommands(final File commandsFile) throws MojoExecutionException {
        List<String> out = new ArrayList<String>();
        try {
            BufferedReader input = new BufferedReader(new FileReader(commandsFile));
            try {
                String line = null;
                while ((line = input.readLine()) != null) {
                    line = line.trim();
                    if (line.equals("") || line.startsWith("#") || line.startsWith("//")) {
                        continue;
                    }
                    out.add(line);
                }
                return out.toArray(new String[]{});
            } finally {
                input.close();
            }
        } catch (FileNotFoundException e) {
            throw new MojoExecutionException("Cannot find file", e);
        } catch (IOException e) {
            throw new MojoExecutionException("IOException", e);
        }
    }
}
