package com.sodasmile.sxxparalizer;

import com.jcraft.jsch.JSchException;
import com.sodasmile.sxxparalizer.SXXExecutor.Builder;
import java.util.Map;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class to run commands in parallel to multiple hosts over an ssh connection.
 * Pass inn an array with hosts it will connect to, and an array with commands,
 * and the commands will be executed one after another on all hosts.
 *
 * Supports barrier mechanism, just pass the command starting with at least three consecutive
 * equals signs, and all the execution will wait until all hosts have reached that barrier.
 *
 * Assumptions:
 * For now, the class assumes the username is 'jboss', the key file for authentication
 * is stored in a jboss_rsa file in your home folders .ssh folder.
 * @author anderssm
 */
public class SXXParalized {

    private SXXParameters parameters;

    public SXXParalized(SXXParameters parameters) {
        this.parameters = parameters;
    }
    // An attempt to try to avoid someone changing hosts or commands in the middle of an execution.
    private final Object lock = new Object();
    private String[] hosts;
    private String[] commands;

    public void setCommands(String[] commands) {
        synchronized (lock) {
            this.commands = commands;
        }
    }

    public void setHosts(String[] hosts) {
        synchronized (lock) {
            this.hosts = hosts;
        }
    }

    public void runCommands() throws Exception { // TODO: Wrap known exceptions in some smart exception, existing or new.
        synchronized (lock) {
            System.out.println("Hello folks... " + Thread.activeCount()); // TODO: Delete
            Thread[] runners = new Thread[hosts.length];

            CyclicBarrier barrier = new CyclicBarrier(hosts.length);

            for (int i = 0; i < runners.length; i++) {
                runners[i] = new ParaRunner(barrier, parameters.host(hosts[i]));
            }

            for (Thread thread : runners) {
                thread.start();
            }

            for (Thread thread : runners) {
                thread.join();
            }
        }
        System.out.println("That's all folks... " + Thread.activeCount());
        Map<Thread, StackTraceElement[]> map = Thread.getAllStackTraces();
        for (Thread thread : map.keySet()) {
            if (thread != Thread.currentThread()) {
                thread.interrupt();
            }
            if (!thread.isAlive()) {
                continue;
            }
            System.out.println("Thread: " + thread + " " + thread.isAlive());
            final StackTraceElement[] stacktr = map.get(thread);
            for (StackTraceElement stackTraceElement : stacktr) {
                System.out.println("\t" + stackTraceElement);
            }
            System.out.println("\n");
        }

        System.out.println("That's all folks... Really... " + Thread.activeCount());
        System.exit(0); // TODO: NO GOOD! But something needs to be done!
    }

    /**
     * Runner to be able to run commands in parallel.
     */
    private class ParaRunner extends Thread {

        private final SXXExecutor exec;
        private final CyclicBarrier barrier;

        private ParaRunner(CyclicBarrier barrier, SXXParameters builder) {
            this.barrier = barrier;
            exec = new Builder(parameters).build();
        }

        @Override
        public void run() {
            try {
                exec.openSession();
                for (String cmd : commands) {
                    if (isBarrierCmd(cmd)) {
                        System.out.println("===================\nhost: " + exec.getHost() + " roach the barrier\n=====================");
                        barrier.await();
                    } else {
                        exec.sendCommand(cmd);
                    }
                }
            } catch (JSchException ex) {
                Logger.getLogger(SXXParalized.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InterruptedException ex) {
                Logger.getLogger(SXXParalized.class.getName()).log(Level.SEVERE, null, ex);
            } catch (BrokenBarrierException ex) {
                Logger.getLogger(SXXParalized.class.getName()).log(Level.SEVERE, null, ex);
            }
        }

        private boolean isBarrierCmd(String cmd) {
            return cmd.startsWith("===");
        }
    }

    /**
     * Poor mans testing framework... 
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        SXXParameters param = new SXXParameters()
                .username("jboss")
                .keyfile(System.getProperty("user.home") + "/.ssh/jboss_rsa");
        SXXParalized me = new SXXParalized(param);
        me.setHosts(new String[]{"host1", "host"});
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
