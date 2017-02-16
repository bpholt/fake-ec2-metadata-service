# Fake EC2 Metadata Service

[![](https://images.microbadger.com/badges/image/bpholt/fake-ec2-metadata-service.svg)](https://microbadger.com/images/bpholt/fake-ec2-metadata-service)
[![license](https://img.shields.io/github/license/bpholt/fake-ec2-metadata-service.svg?style=flat-square)]()

You’re developing an app that will run in a VM locally but relies on the [EC2 Metadata Service](http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-metadata.html) for some part of its behavior when running in Amazon’s cloud. How do you run the same thing locally?

Enter the Fake EC2 Metadata Service, a simple Sinatra app that exposes some of the functionality running on http://169.254.169.254.

## Setup on Linux

Create a loopback interface bound to 169.254.169.254 (this probably needs to be run with root privileges):

```
ifconfig lo:0 169.254.169.254 netmask 255.255.255.255 up
```

Now run the app:

```
docker-compose up
```

## Setup on Mac

[Docker for Mac](https://docker.com/mac) [doesn’t support the same type of networking features that it does on Linux](https://docs.docker.com/docker-for-mac/networking/#/there-is-no-docker0-bridge-on-macos), so we need a different process.

On El Capitan and Sierra, we can redirect outgoing traffic intended for 169.254.169.254 back to the port forwarding set up by Docker.

```
+---------------------------------------------+          +-------------------------------------+
|HTTP Client                                  |          |pf                                   |
|  GET /latest/meta-data/local-ipv4 HTTP/1.1  +----------> Reroute outgoing traffic from       |
|  Host: 169.254.169.254                      |          | 169.254.169.254:80 to 127.0.0.1:80  |
|                                             |          +-------------------------+-----------+
+---------------------------------------------+                                    |
                                                                                   |
                                                                                   |
+---------------------------------------------+          +-------------------------v-------+
|Docker port forwarding                       |          |pf                               |
|  Forward traffic from 127.0.0.1:8169 to     <----------+ Redirect traffic from           |
|  published Container Port                   |          | 127.0.0.1:80 to 127.0.0.1:8169  |
|                                             |          +---------------------------------+
+---------------------+-----------------------+
                      |
                      |
       +--------------v---------------+
       |Fake EC2 Metadata Service     |
       |   200 OK                     |
       |   …                          |
       |                              |
       +------------------------------+
```

Create a file in `/etc/pf.anchors/fake-ec2-metadata-service` containing the following:

```
Packets = "proto tcp from any to 169.254.169.254 port 80"
rdr pass log on lo0 $Packets -> 127.0.0.1 port 8169
pass out log route-to lo0 inet $Packets keep state
```

Then, at the very bottom of `/etc/pf.conf`, load the `pf` rules:

```
load anchor "fake-ec2-metadata-service" from "/etc/pf.anchors/fake-ec2-metadata-service"
```

Immediately after the first Apple `anchor`, include the `fake-ec2-metadata-service` filter rules:

```
anchor "fake-ec2-metadata-service"
```

Immediately after the first Apple `rdr-anchor`, include the `fake-ec2-metadata-service` redirection rules:

```
rdr-anchor "fake-ec2-metadata-service"
```

The entire file should look something like this:

```
scrub-anchor "com.apple/*"
nat-anchor "com.apple/*"
rdr-anchor "com.apple/*"
rdr-anchor "fake-ec2-metadata-service"
dummynet-anchor "com.apple/*"
anchor "com.apple/*"
anchor "fake-ec2-metadata-service"
load anchor "com.apple" from "/etc/pf.anchors/com.apple"
load anchor "fake-ec2-metadata-service" from "/etc/pf.anchors/fake-ec2-metadata-service"
```

Load and enable the `pf` rules by executing

```
sudo pfctl -F all -f /etc/pf.conf
sudo pfctl -E
```

Run the container by executing

```
docker-compose up
```

## Example Requests

```
curl http://169.254.169.254/latest/meta-data/local-ipv4
curl http://169.254.169.254/latest/meta-data/local-hostname
curl http://169.254.169.254/latest/meta-data/iam/security-credentials/
curl http://169.254.169.254/latest/meta-data/iam/security-credentials/default
```
