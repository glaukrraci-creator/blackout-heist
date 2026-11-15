#!/bin/sh
# Blackout Heist - Vertical Slice v0.1 (kerkon Java 17+)
set -e
javac -encoding UTF-8 BlackoutSim.java Progress.java BlackoutDemo.java
java BlackoutDemo
