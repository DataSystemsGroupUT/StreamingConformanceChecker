# Original paper: Adaptive Handling of Out-of-order Streams in Conformance Checking
Kristo Raun, Riccardo Tommasini, Ahmed Awad.  
Best paper award of DOLAP 2024:  
https://dolapworkshop.github.io/dolap-2024/

**Note:** the work has since been extended to include partial order handling.

## Introduction
A Streaming Conformance Checker on top of Beamline.  
The conformance checker is able to handle out-of-order event arrivals and partial orders, as part of the Back to the Order (BttO) extension.  


### Citation

If you find this work useful, please cite:

Kristo Raun, Riccardo Tommasini, and Ahmed Awad.  
**Adaptive Handling of Out-of-order Streams in Conformance Checking.**  
26th International Workshop on Design, Optimization, Languages and Analytical Processing of Big Data (DOLAP), 2024.  
[Read the paper here](https://ceur-ws.org/Vol-3653/paper1.pdf).



### How to run

Please see latest instructions under the `experiments` folder. If in doubt, feel free to contact the authors.

### References

The IWS algorithm used in this repo is from here: https://github.com/DataSystemsGroupUT/ConformanceCheckingUsingTries/tree/streaming

The Beamline framework: https://github.com/beamline/framework

Original DOLAP paper commit:  
https://github.com/DataSystemsGroupUT/StreamingConformanceChecker/commit/404e8c52360506d2851749a5d5bc51546ae7ae69
