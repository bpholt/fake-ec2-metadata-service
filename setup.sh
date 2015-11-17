#!/usr/bin/env bash

DIR=`dirname "$0"`

INTERFACE_CFG=/etc/network/interfaces

if ! ( fgrep "source interfaces.d" ${INTERFACE_CFG} || fgrep "source-directory interfaces.d" ${INTERFACE_CFG} ); then
    echo "source interfaces.d/*.cfg" >> ${INTERFACE_CFG}
fi

cd "$DIR"

cp "etc/init.d/fake-ec2-metadata-service" /etc/init.d/fake-ec2-metadata-service
update-rc.d fake-ec2-metadata-service start 98 2 3 4 5 . stop 12 0 1 6 .
cp "etc/network/interfaces.d"/* /etc/network/interfaces.d/
/etc/init.d/networking restart
ifup -a

if ! ifconfig | fgrep 169.254.169.254 > /dev/null 2>&1; then
    echo "Network setup failed; loopback interface on 169.254.169.254 not available."
    exit 1
fi

gem install bundler
bundle install
