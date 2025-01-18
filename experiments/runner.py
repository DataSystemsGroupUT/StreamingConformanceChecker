import argparse
import subprocess
import os
import time
import threading

# Paths to your files
STREAMING_CHECKER_JAR = "streaming-trie-conformance-0.0.1.jar"
MQTT_PRODUCER_PY = "mqtt_producer.py"

# Define Java executable path
JAVA_EXEC = "java"

# Path to the virtual environment's Python executable
MQTT_PRODUCER_VENV_PYTHON = r"btto_venv\Scripts\python.exe"


def stream_output(process, name):
    """Stream the output of a subprocess in real-time."""
    for line in iter(process.stdout.readline, ''):
        print(f"[{name} stdout] {line.strip()}")
    for line in iter(process.stderr.readline, ''):
        print(f"[{name} stderr] {line.strip()}")


def start_streaming_checker(args):
    try:
        print("Starting conformance checker...")
        java_args = [
            JAVA_EXEC,
            f"-XX:ActiveProcessorCount={args.cores}",
            "-jar",
            STREAMING_CHECKER_JAR,
            "--proxyLog", args.proxyLog,
            "--logFriendlyName", args.logFriendlyName,
            "--minDecayTime", str(args.minDecayTime),
            "--decayTimeMultiplier", str(args.decayTimeMultiplier),
            "--ewmaAlpha", str(args.ewmaAlpha),
            "--eventTimeAware", str(args.eventTimeAware).lower(),
            "--adaptable", str(args.adaptable).lower(),
            "--maxPatternLength", str(args.maxPatternLength),
            "--backToTheOrder", args.backToTheOrder,
        ]
        print("Printing java args:")
        print(java_args)
        process = subprocess.Popen(
            java_args,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            bufsize=1,
            universal_newlines=True,
        )
        threading.Thread(target=stream_output, args=(process, "StreamingChecker"), daemon=True).start()
        print("Conformance checker started successfully!")
        return process
    except Exception as e:
        print(f"Failed to start conformance checker: {e}")
        return None


def start_mqtt_producer(args):
    try:
        print("Starting MQTT producer...")
        producer_args = [MQTT_PRODUCER_VENV_PYTHON, MQTT_PRODUCER_PY, "--swap_setting", args.swap_setting, "--dataset", args.dataset]
        process = subprocess.Popen(
            producer_args,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            bufsize=1,
            universal_newlines=True,
        )
        threading.Thread(target=stream_output, args=(process, "MQTTProducer"), daemon=True).start()
        print("MQTT producer started successfully!")
        return process
    except Exception as e:
        print(f"Failed to start MQTT producer: {e}")
        return None


def parse_arguments(custom_args=None):
    parser = argparse.ArgumentParser(description="Run conformance checking and MQTT producing processes.")

    # Java arguments
    parser.add_argument("--proxyLog", required=True, help="Path to the proxy log file (mandatory).")
    parser.add_argument("--logFriendlyName", default="bpi2012", help="Friendly name for the log.")
    parser.add_argument("--minDecayTime", type=int, default=3, help="Minimum decay time (default: 3).")
    parser.add_argument("--decayTimeMultiplier", type=float, default=0.3, help="Decay time multiplier (default: 0.3).")
    parser.add_argument("--ewmaAlpha", type=float, default=0.005, help="EWMA alpha value (default: 0.005).")
    parser.add_argument("--eventTimeAware", default="true", help="Event time aware flag (default: true).")
    parser.add_argument("--adaptable", default="true", help="Adaptable flag (default: true).")
    parser.add_argument("--maxPatternLength", type=int, default=10, help="Maximum pattern length (default: 10).")
    parser.add_argument("--backToTheOrder",default="none",choices=["none", "greedy", "ngrams"],
        help="Back to the order type (default: none; choices: none, greedy, ngrams)."
    )
    parser.add_argument("--cores", type=int, default=1, help="Number of cores to use (default: 1).")

    # MQTT producer arguments
    parser.add_argument("--swap_setting", default="20", help="Swap setting for the producer.")
    parser.add_argument("--dataset", default="bpi2012", help="Dataset for the producer.")

    # Runner arguments

    parser.add_argument("--numOfEvents", type=int, default=1000000, help="Number of events to wait for (default: 1000000).")
    parser.add_argument("--timeoutInSec", type=int, default=3600, help="Timeout in seconds (default: 3600).")

    return parser.parse_args(custom_args)

def monitor_output_folder(output_folder, existing_folders, num_of_events, timeout_in_sec, start_time):
    while True:
        # Check timeout
        if time.time() - start_time > timeout_in_sec:
            print("Timeout reached. Stopping processes...")
            return True

        # Check for new folder and files
        new_folders = [f for f in os.listdir(output_folder) if os.path.isdir(os.path.join(output_folder, f))]
        new_folders = set(new_folders) - existing_folders  # Exclude already existing folders
        
        for folder in new_folders:
            folder_path = os.path.join(output_folder, folder)
            total_lines = sum(
                sum(1 for _ in open(os.path.join(folder_path, file)))
                for file in os.listdir(folder_path)
                if os.path.isfile(os.path.join(folder_path, file))
            )

            if total_lines >= num_of_events:
                print(f"Reached {num_of_events} events. Stopping processes...")
                return True

        time.sleep(3) 

def main(custom_args=None):
    if custom_args is None:
        # Parse arguments from the command line
        args = parse_arguments()
    else:
        # Parse arguments from the method call
        args = parse_arguments(custom_args)

    # monitor the output folder
    output_folder = "output"
    if not os.path.exists(output_folder):
        os.makedirs(output_folder)
    existing_folders = set(os.listdir(output_folder))

    # Start conformance checker
    start_time = time.time()
    checker_process = start_streaming_checker(args)
    if not checker_process:
        print("Exiting due to failure in starting conformance checker")
        return

    # Wait a bit to ensure the listener is up
    time.sleep(15)

    # Start the MQTT producer
    producer_process = start_mqtt_producer(args)
    if not producer_process:
        print("Exiting due to failure in starting MQTT producer")
        checker_process.terminate()
        return

    try:
        if monitor_output_folder(output_folder, existing_folders, args.numOfEvents, args.timeoutInSec, start_time):
            producer_process.terminate()
            checker_process.terminate()
    except KeyboardInterrupt:
        print("Stopping processes...")
        producer_process.terminate()
        checker_process.terminate()


if __name__ == "__main__":
    main()
