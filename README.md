# Redis Enterprise : Jedis & SSL


This is an extension/set of demos that are related to the [How to Use SSL/TLS With Redis Enterprise
](http://tgrall.github.io/blog/2020/01/02/how-to-use-ssl-slash-tls-with-redis-enterprise/) blog post.

      
## Create new certificates, keys for your Redis Cluster and Application


### Create and install the Redis Enterprise Proxy Certificate and Key

Your environment has for example 2 clusters configured with "Active-Active".

You want to use SSL for TLS and Authentication,  and would like to use the same Certificate for all the clusters and client applications.

This to allow an easy switch from one cluster to another, here an easy way to achieve this:

1. Create a new proxy and client certificates that contain the domain names for all cluster.

2. Use these certificates in your application.


#### Create and deploy a multi domain certificate

For example, my Redis Enterprise deployment has 2 clusters named:

* `cluster01.demo.redislabs.com`
* `cluster02.demo.redislabs.com`

**1- Create a OpenSSL Request file**

To create a new certificate with OpenSSL you have to use a configuration `req.conf`, with the following sections:

```
[req]
distinguished_name = req_distinguished_name
req_extensions = v3_req
prompt = no

[req_distinguished_name]
C = US
ST = CA
L = Mountain View
O = Redis Labs Demo
OU = Redis Labs Demo
CN = demo.redislabs.com

[v3_req]
keyUsage = keyEncipherment, dataEncipherment
extendedKeyUsage = serverAuth
subjectAltName = @alt_names

[alt_names]
DNS.1 = cluster01.demo.redislabs.com
DNS.2 = *.cluster01.demo.redislabs.com
DNS.3 = cluster02.demo.redislabs.com
DNS.4 = *.cluster02.demo.redislabs.com
```

**2- Generate the certificate and key**


Create a new certificate and key using Openssl and the request configuration file:

```
openssl req -nodes \
    -newkey rsa:2048  \
    -keyout my_proxy_key.pem  \
    -x509 -days 36500  \
    -out my_proxy_cert.pem \
    -config ./req.conf \
    -extensions 'v3_req'
```

You can look at the content of the CSR using the following command:
```
openssl  x509 -in my_proxy_cert.pem -text
```

You can see that all domains has been added to the Aternative Name section using:
```
openssl  x509 -in my_proxy_cert.pem -text | grep DNS
```

verify the key:
```
openssl rsa -in my_proxy_key.pem -check
```

The use CSR file to create a new certificate:


**3- Install the certificate and key**

Copy the files (`my_proxy_cert.pem` and `my_proxy_key.pem`) to one of the node of each cluster and 
run the following command to replace the Redis Enterprise proxy certificate:

```
rladmin cluster certificate set proxy certificate_file my_proxy_cert.pem key_file my_proxy_key.pem
```



### Create and use a client application certificate and key

The certificate that you have created, is used to secure the connection between the Redis Cluster and the database (One Way SSL).

It is also possible to do 2 ways SSL and enforce the authentication of the client application, using a certificate,
in this case you have to create a new certificate and enable database authentication.


Create a new request configuration file, named `req_client_app_01.conf` with the following content:


```
[req]
distinguished_name = req_distinguished_name
req_extensions = v3_req
prompt = no

[req_distinguished_name]
C = US
ST = CA
L = Mountain View
O = Redis Labs Demo
OU = Redis Labs Demo
CN = demo.redislabs.com

[v3_req]
keyUsage = keyEncipherment, dataEncipherment
extendedKeyUsage = serverAuth
subjectAltName = @alt_names

[alt_names]
DNS.1 = client01.demo.redislabs.com
DNS.2 = *.client01.demo.redislabs.com
DNS.3 = *.client02.demo.redislabs.com
DNS.4 = *.client02.demo.redislabs.com
```

This configuration expect the client applications/services to be executed on server that are called `*.client01.demo.redislabs.com` or `*.client02.demo.redislabs.com`.

Let's create a new client certificate and key for the `app_01` client application.

```
openssl req \
 -nodes \
 -newkey rsa:2048 \
 -keyout app_01_key.pem \
 -x509 -days 36500 \
 -out app_01_cert.pem \
 -config ./req_client_app_01.conf \
 -extensions 'v3_req'
```



### Enable TLS and Client Authentication for your database

In Redis Enterprise Web UI, go to Databases, select the database you want to enable SSL and click Configuration. Click Edit and then click TLS to enable SSL. In the dropdown list, select "Require SSL for All Communications".

In addition, since we want to use also certificate for authentication, you have to 

* Check `Enforce client authentication`
* Click the "+" button
* Copy the client certificate into the text area, (you can use `pbcopy < client_cert_app_001.pem` on Mac to copy the certificate on your clipboard )
* Click the save button to save the client certificate for authentication.
* Click Update button to save the database settings.

Repeat these steps on the second cluster.


Let's now use these certificates and keys in the client application.



### 2 Ways SSL & Java application

Your Java application has to:

* Use the Redis Labs Cluster certificate to allow communication
* Use the Client certificate to authenticate itself against the database

To achieve this you must:

* Create a Java keystore and store the certificates and keys your applications are using
* Configure your application to use the keystore
* Use a secure URL to connect to Redis Enterprise database


#### Create a Java Keystore and add the certificate and keys to it

Create a **keystore** file that stores the key and certificate you have created earlier:

```
openssl pkcs12 -export \
  -in ./client_cert_app_001.pem \
  -inkey ./client_key_app_001.pem \
  -out client-keystore.p12 \
  -name "APP_01_P12"
```

As you can see the keystore is used to store the credentials associated with you client; it will be used later with the `-javax.net.ssl.keyStore` system property in the Java application.

In addition to the keys tore, you also have to create a trust store, that is used to store other credentials for example in our case the redis cluster certificate.

Create a **trust store** file and add the Redis cluster certificate to it

```
keytool -genkey \
  -dname "cn=CLIENT_APP_01" \
  -alias truststorekey \
  -keyalg RSA \
  -keystore ./client-truststore.p12 \
  -keypass secret \
  -storepass secret \
  -storetype pkcs12
```

```
keytool -import \
  -keystore ./client-truststore.p12 \
  -file ./my_proxy_cert.pem \
  -alias redis-cluster-crt
```

The trustore will be used later with the `-javax.net.ssl.trustStore` system property in the Java application.

When you have an application you can use the following environment variables to configure the application:

```
java -Djavax.net.ssl.keyStore=/path_to/certificates/java/client-keystore.p12 \
-Djavax.net.ssl.keyStorePassword=secret \
-Djavax.net.ssl.trustStore=/path_to/certificates/java/client-truststore.p12 \
-Djavax.net.ssl.trustStorePassword=secret \
-jar MyApp.jar
```

For simplicity reason, in this article, I am "hardcoding" the path to the keystore in the code. Just adapt this to your application, and environment/tool.

``` java
import redis.clients.jedis.Jedis;
import java.net.URI;

public class SSLTest {

    public static void main(String[] args) {

        System.setProperty("javax.net.ssl.keyStore", "/path_to/certificates/client-keystore.p12");
        System.setProperty("javax.net.ssl.keyStorePassword", "secret");

        System.setProperty("javax.net.ssl.trustStore","/path_to/certificates/client-truststore.p12");
        System.setProperty("javax.net.ssl.trustStorePassword","secret");

        URI uri = URI.create("rediss://127.0.0.1:12000");

        Jedis jedis = new Jedis(uri);
        jedis.auth("secretdb01");


        System.out.println(jedis.info("SERVER"));
        jedis.close();
    }

}
```

## Building and Running the Spring Application

The Spring application is connecting to 2 clusters using 2 pools, but only using 1 single pool/cluster at the time.

When the first cluster is not available, the application fails over automatically to the other cluster.

Note: still some logic to implement to raise error, fallback, ...


```
mvn clean package 
```

Run the application

```
java -Djavax.net.ssl.keyStore=/path/to/certificates/client-keystore.p12 \
     -Djavax.net.ssl.keyStorePassword=secret \
     -Djavax.net.ssl.trustStore=/path/to/certificates/client-truststore.p12 \
     -Djavax.net.ssl.trustStorePassword=secret \
     -jar ./target/jedis-failover-sample-app.jar rediss://redis-12000.cluster01.demo.redislabs.com:12000 rediss://redis-12000.cluster02.demo.redislabs.com:12000
```
