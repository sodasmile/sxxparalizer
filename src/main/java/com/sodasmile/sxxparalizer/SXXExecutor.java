/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.sodasmile.sxxparalizer;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class to execute operations over SSH/SCP. Wraps JSch and this code is mainly
 * a copy of apache ants
 * {@link org.apache.tools.ant.taskdefs.optional.ssh.SSHExcec} and
 * {@link org.apache.tools.ant.taskdefs.optional.ssh.SSHBase}.
 *
 * TODO: Implement proper logging, remove system.out
 *
 * @author anderssm
 */
public class SXXExecutor {

    /** milliseconds to wait between retries for checking if commands completed */
    private static final int RETRY_INTERVAL = 500;
    private String host;
    private String knownHosts;
    private int port;
    private boolean failOnError;
    private boolean verbose;
    private SSHUserInfo userInfo;
    /** milliseconds to wait for command to finish, 0 means forever */
    private long maxwait;
    /** for waiting for the command to finish */
    private Thread thread = null;
    /** Stores session for reuse between commands */
    private Session session;

    /**
     * Builder class to create new SXXExecutors.
     */
    public static class Builder {

        private SXXParameters parameters;

        public Builder(SXXParameters parameters) {
            this.parameters = parameters;
        }

        SXXExecutor build() {
            parameters.verifyValidConfig();
            return new SXXExecutor(parameters);
        }
    }
    /**
     * Constructor for SSHBase.
     * This initializizs the known hosts and sets the default port.
     * @throws IllegalArgumentException on error
     */
    private SXXExecutor(SXXParameters parameters) {
        userInfo = new SSHUserInfo();
        this.knownHosts = System.getProperty("user.home") + "/.ssh/known_hosts";

        setHost(parameters.host());
        setUsername(parameters.username());
        setPassword(parameters.password());
        setKeyfile(parameters.keyfile());
        setPassphrase(parameters.passphrase());
        setKnownHostsIfSpecified(parameters);
        setPort(parameters.port());
        setFailonerror(parameters.failOnError());
        setVerbose(parameters.verbose());
        setTimeout(parameters.timeout());
        setTrust(parameters.trust());
    }

    private void setKnownHostsIfSpecified(SXXParameters parameters) {
        if (parameters.knownHosts() != null && !parameters.knownHosts().trim().equals("")) {
            setKnownhosts(parameters.knownHosts());
        }
    }

    /**
     * Open an ssh seession. Call this when done configuring executor, before calling sendCommand.
     * @return the opened session
     * @throws JSchException on error
     */
    public void openSession() throws JSchException {
        checkValidConfiguration();

        JSch jsch = new JSch();
        if (null != userInfo.getKeyfile()) {
            jsch.addIdentity(userInfo.getKeyfile());
        }

        if (!userInfo.getTrust() && knownHosts != null) {
            log("Using known hosts: " + knownHosts);
            jsch.setKnownHosts(knownHosts);
        }

        Session s = jsch.getSession(userInfo.getName(), host, port);
        s.setUserInfo(userInfo);
        s.setTimeout((int) maxwait);
        log("Connecting to " + host + ":" + port);
        s.connect();
        this.session = s;
    }

    /**
     * Get the user information.
     * @return the user information
     */
    protected SSHUserInfo getUserInfo() {
        return userInfo;
    }

    public StringBuffer sendCommand(String cmd) throws JSchException, InterruptedException {
        checkValidSession();
        checkValidCommand(cmd);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        final ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(cmd);
        channel.setOutputStream(out);
        channel.setExtOutputStream(out);

        log("DEBUG: Executing command: " + cmd);

        channel.connect();
        waitForCommandToFinish(channel);

        log("DEBUG: Done with command: " + cmd);

        int ec = channel.getExitStatus();
        if (ec != 0) {
            String msg = "WARN: Remote command failed with exit status " + ec;
            log(msg);
            // TODO: fail on error... 
        } else {
            log("DEBUG: Success");
        }

        log("DEBUG: Command result:\n<result>\n" + out.toString() + "</result>");
        String result = out.toString();

        try {
            out.close();
        } catch (IOException ex) {
            Logger.getLogger(SXXExecutor.class.getName()).log(Level.SEVERE, null, ex);
        }

        return new StringBuffer(result);
    }

    public void disconnect() {
        session.disconnect();
    }

    private void waitForCommandToFinish(final ChannelExec channel) throws InterruptedException {
        thread = new Thread("Command finished thread - " + host) {

            @Override
            public void run() {
                while (!channel.isClosed()) {
                    if (thread == null) {
                        return;
                    }
                    try {
                        sleep(RETRY_INTERVAL);
                    } catch (Exception e) {
                        // ignored
                    }
                }
            }
        };
        thread.start();
        thread.join(maxwait);
    }

    private void checkValidConfiguration() {
        if (getHost() == null) {
            throw new IllegalStateException("No host is configured. Host is required.");
        }
        if (getUserInfo().getName() == null) {
            throw new IllegalStateException("No username is configured. Username is required.");
        }
        if (getUserInfo().getKeyfile() == null && getUserInfo().getPassword() == null) {
            throw new IllegalStateException("No password nor keyfile is configured. Password or Keyfile is required.");
        }
    }

    private void checkValidCommand(String cmd) throws IllegalStateException {
        if (cmd == null) {
            throw new IllegalStateException("Command or commandResource is required.");
        }
    }

    private void checkValidSession() throws IllegalStateException {
        if (session == null || !session.isConnected()) {
            throw new IllegalStateException("No session open, make sure to call 'openSession' before sending first command");
        }
    }

    private void log(String message) {
        System.out.println(getHost() + ": " + message);
    }

    /* ===================== Bean properties ==============================*/
    
    /**
     * Remote host, either DNS name or IP.
     *
     * @param host  The new host value
     */
    private void setHost(String host) {
        this.host = host;
    }

    /**
     * Get the host.
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * Set the failonerror flag.
     * Default is true
     * @param failure if true throw a build exception when a failure occuries,
     *                otherwise just log the failure and continue
     */
    private void setFailonerror(boolean failure) {
        failOnError = failure;
    }

    /**
     * Get the failonerror flag.
     * @return the failonerror flag
     */
    public boolean getFailonerror() {
        return failOnError;
    }

    /**
     * Set the verbose flag.
     * @param verbose if true output more verbose logging
     * @since Ant 1.6.2
     */
    private void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Get the verbose flag.
     * @return the verbose flag
     * @since Ant 1.6.2
     */
    public boolean getVerbose() {
        return verbose;
    }

    /**
     * Username known to remote host.
     *
     * @param username  The new username value
     */
    private void setUsername(String username) {
        userInfo.setName(username);
    }

    /**
     * Sets the password for the user.
     *
     * @param password  The new password value
     */
    private void setPassword(String password) {
        userInfo.setPassword(password);
    }

    /**
     * Sets the path to keyfile for the user.
     *
     * @param keyfile  The new keyfile value
     */
    private void setKeyfile(String keyfile) {
        userInfo.setKeyfile(keyfile);
    }

    /**
     * Sets the passphrase for the users key.
     *
     * @param passphrase  The new passphrase value
     */
    private void setPassphrase(String passphrase) {
        userInfo.setPassphrase(passphrase);
    }

    /**
     * Sets the path to the file that has the identities of
     * all known hosts.  This is used by SSH protocol to validate
     * the identity of the host.  The default is
     * <i>${user.home}/.ssh/known_hosts</i>.
     *
     * @param knownHosts a path to the known hosts file.
     */
    private void setKnownhosts(String knownHosts) {
        this.knownHosts = knownHosts;
    }

    /**
     * Setting this to true trusts hosts whose identity is unknown.
     *
     * @param yesOrNo if true trust the identity of unknown hosts.
     */
    private void setTrust(boolean yesOrNo) {
        userInfo.setTrust(yesOrNo);
    }

    /**
     * Changes the port used to connect to the remote host.
     *
     * @param port port number of remote host.
     */
    private void setPort(int port) {
        this.port = port;
    }

    /**
     * Get the port attribute.
     * @return the port
     */
    public int getPort() {
        return port;
    }

    private void setTimeout(long timeout) {
        this.maxwait = timeout;
    }
}
