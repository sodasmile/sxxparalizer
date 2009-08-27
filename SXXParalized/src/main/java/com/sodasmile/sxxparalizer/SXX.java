package com.sodasmile.sxxparalizer;

public class SXX {

    public static void main(String[] args) throws Exception {
        SXXExecutor me = new SXXExecutor.Builder(new SXXParameters()
                .username("jboss")
                .host("larm03syst")
                // One of these:
                .password("larmsyst")
                //.keyfile("C:/Users/anderssm/.ssh/jboss_rsa");
                .verbose(true)
                //.trust(true); // no need when using known_hosts
                .timeout(15000))
                .build();
        
        me.openSession();

        me.sendCommand("ls -l");
        me.sendCommand("ls -l /root");
        me.sendCommand("rm rattata");

        me.disconnect();

        try {
            me.sendCommand("fisk");
            throw new RuntimeException("Expecting last command to fail, since session should be closed");
        } catch (IllegalStateException ise) {
            // Nada, expecting this exception.
        }

        System.out.println("That's all folks...");
    }
}

