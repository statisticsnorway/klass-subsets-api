#!/usr/bin/env bash

host=localhost
if grep '^1:name=systemd:/docker/' /proc/1/cgroup
then
    apt-get update
    apt-get install net-tools
    host=$(route -n | grep '^0.0.0.0' | sed -e 's/^0.0.0.0\s*//' -e 's/ .*//')
fi
export HOST_ADDRESS=$host
echo "\$HOST_ADDRESS is $HOST_ADDRESS"
