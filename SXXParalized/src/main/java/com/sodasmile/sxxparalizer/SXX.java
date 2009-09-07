package com.sodasmile.sxxparalizer;

/**
 * Class to test the execution of un-parallelized commands on one host.
 */
public class SXX {

    /**
     * Running un-parallelized 'test'.
     * @param args - not used.
     * @throws Exception everything that's thrown.
     */
    public static void main(String[] args) throws Exception {
        SXXExecutor executor = new SXXExecutor.SXXExecutorBuilder(new SXXParameters()
                .username("username")
                .host("hostname")
                // One of these:
                .password("password")
                //.keyfile("C:/Users/anderssm/.ssh/jboss_rsa");
                .verbose(true)
                //.trust(true); // no need when using known_hosts
                .timeout(15000))
                .build();
        
        executor.sendCommand("ls -l");
        //executor.sendCommand("ls -l /root");
        executor.sendCommand("touch rattata");
        executor.sendCommand("ls -l");
        executor.sendCommand("rm rattata");
        executor.sendCommand("ls -l");

        executor.disconnect();

        try {
            executor.sendCommand("fisk");
            throw new RuntimeException("Expecting last command to fail, since session should be closed");
        } catch (IllegalStateException ise) {
            // Nada, expecting this exception.
        }

        System.out.println("That's all folks...");
    }
}

