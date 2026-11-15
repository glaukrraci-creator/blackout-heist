@echo off
REM Blackout Heist - Vertical Slice v0.1 (kerkon Java 17+)
javac -encoding UTF-8 BlackoutSim.java Progress.java BlackoutDemo.java || pause
java BlackoutDemo
