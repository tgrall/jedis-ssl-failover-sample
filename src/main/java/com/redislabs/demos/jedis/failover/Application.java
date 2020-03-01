package com.redislabs.demos.jedis.failover;


import java.net.URISyntaxException;
import java.util.Arrays;

import com.redislabs.demos.jedis.failover.config.JedisAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.Banner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;

@SpringBootApplication
public class Application implements CommandLineRunner {

    Logger logger = LoggerFactory.getLogger(Application.class);

    @Autowired
    private Environment env;
    JedisAccess jedisAccess = null;

    public static void main(String[]args) throws Exception {

        SpringApplication app = new SpringApplication(Application.class);
        app.run(args);
    }

    //access command line arguments
    @Override
    public void run(String... args) throws Exception {
        logger.info("Aplication started");

        String cluster01 = args[0];
        String cluster02 = args[1];

        jedisAccess = new JedisAccess(cluster01, cluster02);
         this.callRedis();

    }


    private void callRedis() throws URISyntaxException {
        Jedis jedis = jedisAccess.getJedisConnection();
        jedis.del("cli.infinite.loop");

        long printDataEeverySeconds = 5;
        long lastPrintTime = System.currentTimeMillis();
        long nbOdOps = 0;


        boolean infinite = true;
        while (infinite) {

            long start  = System.currentTimeMillis();

            // retry the operation 3 times before failing
            int currentRetry = 0;

            while(currentRetry < 3) {
                currentRetry++;
                try {
                    jedis.incr("cli.infinite.loop");
                    currentRetry = 3; // stop
                } catch(JedisConnectionException jde) {
                    // reconnect
                    jedis = jedisAccess.getJedisConnection();
                    System.out.println("RECONNECT .....");

                } catch (Exception e) {
                }
            }


            nbOdOps++;
            long end  = System.currentTimeMillis();

            if ((end - printDataEeverySeconds*1000 ) >  lastPrintTime ) {
                lastPrintTime = end;
                System.out.println( "Ops per seconds : "+ (nbOdOps / printDataEeverySeconds));
                nbOdOps = 0;
            }

            try {
                Thread.sleep(20);
            } catch (InterruptedException ie){}

        }
    }
}