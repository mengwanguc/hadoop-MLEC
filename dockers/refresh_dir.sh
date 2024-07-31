#!/bin/bash

# /data/dataNode1 is special because its a zfs mount
rm -rf /data/dataNode1/*

for i in {2..3}; do
  echo "$i"
  rm -rf "/data/dataNode$i"
  rm -rf "/data/recon_buffer$i"
  mkdir -p "/data/dataNode$i"
  mkdir -p "/data/recon_buffer$i"
done

rm -rf /data/nameNode
mkdir -p /data/nameNode/opt
cp ./dockers/rack-topo.sh /data/nameNode/opt