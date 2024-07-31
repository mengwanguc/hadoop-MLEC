#!/bin/bash

for i in {1..3}; do
  echo "$i"
  rm -rf "/data/dataNode$i"
  rm -rf "/data/recon_buffer$i"
  mkdir -p "/data/dataNode$i"
  mkdir -p "/data/recon_buffer$i"
done

rm -rf /data/nameNode
mkdir -p /data/nameNode/opt
cp ./dockers/rack-topo.sh /data/nameNode/opt