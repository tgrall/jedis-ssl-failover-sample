package com.redislabs.demos.jedis.failover.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

public class JedisAccess {

    Logger logger = LoggerFactory.getLogger(JedisAccess.class);


    int retryCounter = 0;
    int maxRetries = 2;
    int waitTime  = 500;


    String cluster01URI = null;
    String cluster02URI = null;
    private static JedisPool jedisPoolCluster01 = null;
    private static JedisPool jedisPoolCluster02 = null;
    private Map<String, JedisPool> pools = new HashMap<>();
    private String activeClusterName = "01";

    private static Map connectionStatusMessage = new HashMap<>();


    public JedisAccess(String cluster01URI, String cluster02URI) {

        this.cluster01URI = cluster01URI;
        this.cluster02URI = cluster02URI;

    }

    private JedisPool getJedisPool() throws URISyntaxException {
        return getJedisPool(activeClusterName);
    };


    private JedisPool getJedisPool(String clusterName) throws URISyntaxException {

        System.out.println("getJedisPool " + cluster01URI);


        JedisPoolConfig jedisPoolConfig = new JedisPoolConfig();
        jedisPoolConfig.setMaxTotal(3);
        jedisPoolConfig.setMaxWaitMillis(5000);

        if (jedisPoolCluster01 == null) {
            URI c01 = new URI(cluster01URI);
            jedisPoolCluster01 = new JedisPool(jedisPoolConfig, c01);
            logger.info("Jedis Pool to Cluster-001 Created");
            pools.put("01", jedisPoolCluster01);
        }

        if (jedisPoolCluster02 == null) {
            URI c02= new URI(cluster02URI);
            jedisPoolCluster02 = new JedisPool(jedisPoolConfig, c02);
            logger.info("Jedis Pool to Cluster-002 Created");
            pools.put("02", jedisPoolCluster02);

        }

        System.out.println("==========");
        System.out.println(pools.get(clusterName));

        return pools.get(clusterName);

    }

    private Jedis getJedisFromPool() throws URISyntaxException {
        return getJedisFromPool(activeClusterName);
    }

    private Jedis getJedisFromPool(String clusterName) throws URISyntaxException {

        System.out.println("POOL "+ clusterName);

        connectionStatusMessage = new HashMap(); // reset messages
        long start = System.currentTimeMillis();
        Jedis jedis = null ;
        retryCounter = 0;
        while(retryCounter < maxRetries) {
            retryCounter++;
            logger.warn("Connection retry : "+ retryCounter);
            try {
                logger.info("Use connection from "+ clusterName);
                jedis = this.getJedisPool(clusterName).getResource();
                connectionStatusMessage.put("fqdn-retry-"+ retryCounter, "success");
                retryCounter = 99;
            } catch (Exception e) {
                e.printStackTrace();
                connectionStatusMessage.put("fqdn-retry-"+ retryCounter, "fail");
                if (retryCounter == maxRetries-1) {
                    if ( activeClusterName.equalsIgnoreCase("01") ) {
                        logger.warn("!!! === Switching to Cluster 02 === !!!");
                        activeClusterName = "02";
                    } else if ( activeClusterName.equalsIgnoreCase("02") ) {
                            logger.warn("!!! === Switching back to Cluster 01 === !!!");
                            activeClusterName = "01";
                        }
                    return getJedisFromPool();
                }
                try { Thread.sleep(1000 * retryCounter); } catch (Exception e2) {}
            }
        }
        connectionStatusMessage.put("conn_total_elapsed_time_ms", System.currentTimeMillis() - start);
        return jedis;

    }


    public Jedis getJedisConnection() throws URISyntaxException {
        Jedis jedis = null;
        jedis = this.getJedisFromPool();
        return jedis;
    }

    /**
     * Remove pool
     */
    public void resetJedisPool() {
        if (jedisPoolCluster01 != null) {
            jedisPoolCluster01.close();
            jedisPoolCluster01.destroy();
            jedisPoolCluster01 = null;
        }

    }


    public Map getMessages() {
        return connectionStatusMessage;
    }
}