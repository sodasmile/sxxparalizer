package com.sodasmile.sxxparalizer;

/**
 *
 * @author anderssm
 */
public class SXXParameters {

    /** Default listen port for SSH daemon */
    private static final int SSH_PORT = 22;

    private String host;
    private String username;
    private String password;
    private String keyfile;
    private String passphrase;
    private String knownHosts;
    private int port = SSH_PORT;
    private boolean failOnError = true;
    private boolean verbose;
    private long maxwait;
    private boolean trust;

    public SXXParameters() {
    }

    public SXXParameters failOnError(boolean failOnError) {
        this.failOnError = failOnError;
        return this;
    }

    boolean failOnError() {
        return failOnError;
    }

    public SXXParameters host(String host) {
        this.host = host;
        return this;
    }

    String host() {
        return host;
    }

    public SXXParameters keyfile(String keyfile) {
        this.keyfile = keyfile;
        return this;
    }

    String keyfile() {
        return keyfile;
    }

    public SXXParameters knownHosts(String knownHosts) {
        this.knownHosts = knownHosts;
        return this;
    }

    String knownHosts() {
        return knownHosts;
    }

    public SXXParameters passphrase(String passphrase) {
        this.passphrase = passphrase;
        return this;
    }

    String passphrase() {
        return passphrase;
    }

    public SXXParameters password(String password) {
        this.password = password;
        return this;
    }

    String password() {
        return password;
    }

    public SXXParameters port(int port) {
        this.port = port;
        return this;
    }

    int port() {
        return port;
    }

    public SXXParameters timeout(long timeout) {
        this.maxwait = timeout;
        return this;
    }

    long timeout() {
        return maxwait;
    }

    public SXXParameters trust(boolean trust) {
        this.trust = trust;
        return this;
    }

    boolean trust() {
        return trust;
    }

    public SXXParameters username(String username) {
        this.username = username;
        return this;
    }

    String username() {
        return username;
    }

    public SXXParameters verbose(boolean verbose) {
        this.verbose = verbose;
        return this;
    }

    boolean verbose() {
        return verbose;
    }

    /**
     * Checks if SXXExecutor configuration is valid.
     */
    void verifyValidConfig() {
        failIfAllNull("Hostname is required", host);
        failIfAllNull("Username is required", username);
        failIfAllNull("Keyfile or password required", keyfile, password);
    }

    /**
     * Throws exception if all values are null.
     * @param message Message to add to exception if all values are null.
     * @param values Values to check for null.
     * @throws IllegalArgumentException if all values are null.
     */
    private void failIfAllNull(String message, Object... values) {
        boolean set = false;
        for (Object object : values) {
            if (object != null) {
                set = true;
            }
        }
        if (!set) {
            throw new IllegalArgumentException(message);
        }
    }
}
