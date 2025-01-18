Steps for creating the venv:

The virtual environment for running the mqtt_producer.py has been tested on Python 3.10.

`py -3.10 -m venv btto_venv`  
`source btto_venv/Scripts/activate`  
`pip install -r requirements.txt`

For running the conformance checker and MQTT producer:  

```
python runner.py \
    --proxyLog "logs/bpi2012/bpi2012_100traces.xes.gz" \
    --logFriendlyName "bpi2012" \
    --minDecayTime 3 \
    --decayTimeMultiplier 0.3 \
    --ewmaAlpha 0.005 \
    --eventTimeAware True \
    --adaptable True \
    --maxPatternLength 10 \
    --backToTheOrder "none" \
    --cores 1 \
    --swap_setting "20" \
    --dataset "bpi2012" \
    --numOfEvents 1000000 \
    --timeoutInSec 3600
```

# runner.py Arguments

## General Arguments
- `--proxyLog` *(required)*: Path to the proxy log file (mandatory).
- `--logFriendlyName` *(optional, default: "bpi2012")*: Friendly name for the log.
- `--minDecayTime` *(optional, default: 3)*: Minimum decay time.
- `--decayTimeMultiplier` *(optional, default: 0.3)*: Decay time multiplier.
- `--ewmaAlpha` *(optional, default: 0.005)*: EWMA alpha value.
- `--eventTimeAware` *(optional, default: true)*: Flag indicating whether event time awareness is enabled.
- `--adaptable` *(optional, default: true)*: Flag indicating whether adaptability is enabled.
- `--maxPatternLength` *(optional, default: 10)*: Maximum pattern length.
- `--backToTheOrder` *(optional, default: "none")*: Back to the order type. Choices: `none`, `greedy`, `ngrams`.
- `--cores` *(optional, default: 1)*: Number of cores to use.

## MQTT Producer Arguments
- `--swap_setting` *(optional, default: "20")*: Swap setting for the producer.
- `--dataset` *(optional, default: "bpi2012")*: Dataset for the producer.

## Runner Arguments
- `--numOfEvents` *(optional, default: 1000000)*: Number of events to wait for before stopping the processes.
- `--timeoutInSec` *(optional, default: 3600)*: Timeout in seconds before stopping the processes.
