# Event Time Aware Scalable Streaming Conformance Checking
A Streaming Conformance Checker on top of Beamline.

### How to run
The system should have Docker installed, a Java IDE running Java 11 and Python 3.9+.
Initialize the MQTT broker by running the shell script in `mqtt` folder. Start the Java IDE and run the Runner class for the appropriate log. Then, run the Python notebook in `mqtt` folder with the same log. The results will be outputted per event arrival to the `output` folder.

### References

The IWS algorithm used in this repo is from here: https://github.com/DataSystemsGroupUT/ConformanceCheckingUsingTries/tree/streaming

The Beamline framework: https://github.com/beamline/framework


