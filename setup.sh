#!/usr/bin/env bash

DIR=`dirname "$0"`

cp "$DIR/etc/init.d/fake-ec2-metadata-service" /etc/init.d/fake-ec2-metadata-service
cp "$DIR/etc/network/interfaces.d/fake-ec2-metadata-service" /etc/network/interfaces.d/
/etc/init.d/networking restart
ifup -a

if ! ifconfig | fgrep 169.254.169.254 > /dev/null 2>&1; then
    echo "Network setup failed; loopback interface on 169.254.169.254 not available."
    exit 1
fi
