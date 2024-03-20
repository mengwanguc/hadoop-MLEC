#!/bin/bash
size=$1

for i in {1..4}; do
    mkdir -p /media/ramdisk$i
    truncate -s "${size}" /media/ramdisk$i/disk.img
    # We have already created the file image through truncate
    # Partition the image file
    cfdisk /media/ramdisk$i/disk.img
    # Create loop back (pad out the loop back index by 20 since the device might already have a few loop back
    loopback_num=$(($i+20))
    losetup -P /dev/loop$loopback_num /media/ramdisk$i/disk.img
    # Make fs
    mkfs.ext4 /dev/loop$loopback_num
done