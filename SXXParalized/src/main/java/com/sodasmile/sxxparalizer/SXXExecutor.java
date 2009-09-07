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
 * <p/>
 * TODO: Implement proper logging, remove system.out
 *
 * @author anderssm
 */
public class SXXExecutor {

    /**
     * Milliseconds to wait between retries for checking if commands completed
     */
    private static final int RETRY_INTERVAL = 500;

    private String host;
    private String knownHosts;
    private int port;
    private boolean failOnError;
    private boolean verbose;
    private SSHUserInfo userInfo;

    /**
     * milliseconds to wait for command to finish, 0 means forever
     */
    private long maxwait;

    /**
     * Stores session for reuse between commands
     */
    private Session session;

    /**
     * Builder class to create new SXXExecutors.
     */
    public static class SXXExecutorBuilder {

        private SXXParameters parameters;

        public SXXExecutorBuilder(final SXXParameters parameters) {
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
     *
     * @param parameters Parameters to initialize the executor with.
     */
    private SXXExecutor(final SXXParameters parameters) {
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

    private void setKnownHostsIfSpecified(final SXXParameters parameters) {
        if (parameters.knownHosts() != null && !parameters.knownHosts().trim().equals("")) {
            setKnownhosts(parameters.knownHosts());
        }
    }

    /**
     * Open an ssh seession. Call this when done configuring executor, before calling sendCommand.
     *
     * @return the opened session
     * @throws JSchException on error
     */
    private void openSession() throws JSchException {
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
     *
     * @return the user information
     */
    private SSHUserInfo getUserInfo() {
        return userInfo;
    }

    /**
     * Sends command to server. Returns response as StringBuffer.
     *
     * @param cmd command to execute
     * @return
     * @throws JSchException
     * @throws InterruptedException
     */
    public StringBuffer sendCommand(final String cmd) throws JSchException, InterruptedException {
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
            if (failOnError) {
                throw new RuntimeException("Remote command failed with status: " + ec);
            }
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

    /**
     * Make sure to call this after last command to be sent to server. Closes connections and tidies up internal JSch stuff.
     */
    public void disconnect() {
        session.disconnect();
    }

    private void waitForCommandToFinish(final ChannelExec channel) throws InterruptedException {
        Thread threadl = new Thread("Waiting for command finished-thread: " + host) {

            @Override
            public void run() {
                while (!channel.isClosed()) {
                    try {
                        sleep(RETRY_INTERVAL);
                    } catch (InterruptedException e) {
                        // ignored
                    }
                }
            }
        };
        threadl.start();
        threadl.join(maxwait);
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

    private void checkValidCommand(final String cmd) {
        if (cmd == null || cmd.trim().equals("")) {
            throw new IllegalStateException("Command or commandResource is required.");
        }
    }

    private void checkValidSession() throws JSchException {
        if (session == null || !session.isConnected()) {
            openSession();
        }
    }

    private void log(final String message) {
        System.out.println(getHost() + ": " + message);
    }

    /* ===================== Bean properties ==============================*/

    /**
     * Remote host, either DNS name or IP.
     *
     * @param host The new host value
     */
    private void setHost(final String host) {
        this.host = host;
    }

    /**
     * Get the host.
     *
     * @return the host
     */
    public String getHost() {
        return host;
    }

    /**
     * Set the failonerror flag.
     * Default is true
     *
     * @param failure if true throw a build exception when a failure occuries,
     *                otherwise just log the failure and continue
     */
    private void setFailonerror(final boolean failure) {
        failOnError = failure;
    }

    /**
     * Get the failonerror flag.
     *
     * @return the failonerror flag
     */
    public boolean getFailonerror() {
        return failOnError;
    }

    /**
     * Set the verbose flag.
     *
     * @param verbose if true output more verbose logging
     */
    private void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Get the verbose flag.
     *
     * @return the verbose flag
     */
    public boolean getVerbose() {
        return verbose;
    }

    /**
     * Username known to remote host.
     *
     * @param username The new username value
     */
    private void setUsername(final String username) {
        userInfo.setName(username);
    }

    /**
     * Sets the password for the user.
     *
     * @param password The new password value
     */
    private void setPassword(final String password) {
        userInfo.setPassword(password);
    }

    /**
     * Sets the path to keyfile for the user.
     *
     * @param keyfile The new keyfile value
     */
    private void setKeyfile(final String keyfile) {
        userInfo.setKeyfile(keyfile);
    }

    /**
     * Sets the passphrase for the users key.
     *
     * @param passphrase The new passphrase value
     */
    private void setPassphrase(final String passphrase) {
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
    private void setKnownhosts(final String knownHosts) {
        this.knownHosts = knownHosts;
    }

    /**
     * Setting this to true trusts hosts whose identity is unknown.
     *
     * @param yesOrNo if true trust the identity of unknown hosts.
     */
    private void setTrust(final boolean yesOrNo) {
        userInfo.setTrust(yesOrNo);
    }

    /**
     * Changes the port used to connect to the remote host.
     *
     * @param port port number of remote host.
     */
    private void setPort(final int port) {
        this.port = port;
    }

    /**
     * Get the port attribute.
     *
     * @return the port
     */
    public int getPort() {
        return port;
    }

    private void setTimeout(final long timeout) {
        this.maxwait = timeout;
    }
}
