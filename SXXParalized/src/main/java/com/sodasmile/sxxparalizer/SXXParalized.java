package com.sodasmile.sxxparalizer;

import com.jcraft.jsch.JSchException;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class to run commands in parallel to multiple hosts over an ssh connection.
 * Pass inn an array with hosts it will connect to, and an array with commands,
 * and the commands will be executed one after another on all hosts.
 * <p/>
 * Supports barrier mechanism, just pass the command starting with at least three consecutive
 * equals signs, and all the execution will wait until all hosts have reached that barrier.
 * <p/>
 *
 * @author anderssm
 */
public class SXXParalized {

    private SXXParameters parameters;

    public SXXParalized(final SXXParameters parameters) {
        this.parameters = parameters;
    }

    // An attempt to try to avoid someone changing hosts or commands in the middle of an execution.
    private final Object lock = new Object();

    private String[] hosts;
    private String[] commands;

    public void setCommands(final String[] commands) {
        synchronized (lock) {
            this.commands = commands;
        }
    }

    public void setHosts(final String[] hosts) {
        synchronized (lock) {
            this.hosts = hosts;
        }
    }

    public void runCommands() throws Exception { // TODO: Wrap known exceptions in some smart exception, existing or new.
        synchronized (lock) {
            Thread[] runners = new Thread[hosts.length];

            CyclicBarrier barrier = new CyclicBarrier(hosts.length);

            for (int i = 0; i < runners.length; i++) {
                parameters.host(hosts[i]);
                runners[i] = new ParallelCommandRunner(barrier, parameters);
            }

            for (Thread thread : runners) {
                thread.start();
            }

            for (Thread thread : runners) {
                thread.join();
            }
        }
    }

    /**
     * Runner to be able to run commands in parallel.
     */
    class ParallelCommandRunner extends Thread {

        private final SXXExecutor executor;
        private final CyclicBarrier barrier;

        /**
         * Runs commands on one hosts. Using barrier to synchronize with other running threads.
         *
         * @param barrier
         * @param parameters
         */
        private ParallelCommandRunner(final CyclicBarrier barrier, final SXXParameters parameters) {
            this.barrier = barrier;
            executor = new SXXExecutor.SXXExecutorBuilder(parameters).build();
        }

        @Override
        public void run() {
            try {
                for (String cmd : commands) {
                    cmd = cmd.trim();
                    if (isBarrierCmd(cmd)) {
                        System.out.println(executor.getHost() + " roach barrier, waiting...");
                        barrier.await();
                    } else if (isHostSpecificCommand(cmd) && !commandForCurrentHost(executor.getHost(), cmd)) {
                        System.out.println(executor.getHost() + ": Skipping command not specific for this host: " + cmd);
                    } else {
                        executor.sendCommand(cleanupCommand(cmd));
                    }
                }
            } catch (JSchException ex) {
                Logger.getLogger(SXXParalized.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InterruptedException ex) {
                Logger.getLogger(SXXParalized.class.getName()).log(Level.SEVERE, null, ex);
            } catch (BrokenBarrierException ex) {
                Logger.getLogger(SXXParalized.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                // Disconnects from server when all commands are run.
                executor.disconnect();
            }
        }
    }

    static String cleanupCommand(final String cmd) {
        return cmd.replaceAll("^%.*: *", "");
    }

    static boolean commandForCurrentHost(final String host, final String cmd) {
        return cmd.matches("%" + host + ": .*");
    }

    static boolean isHostSpecificCommand(final String cmd) {
        return cmd.startsWith("%");
    }

    static boolean isBarrierCmd(final String cmd) {
        return cmd.startsWith("===");
    }

    /**
     * Poor mans testing framework...
     *
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        SXXParameters param = new SXXParameters()
                .username("username")
                .keyfile(System.getProperty("user.home") + "/.ssh/keyfile");
        SXXParalized me = new SXXParalized(param);
        me.setHosts(new String[]{
                "host1",
                "host2"
        });
        me.setCommands(new String[]{
                "ls -l",
                "============",
                "touch rattata",
                "ls -l",
                "============",
                "rm rattata",
                "============",
                "ls"});
        me.runCommands();
    }
}
