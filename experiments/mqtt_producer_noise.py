import argparse
from paho.mqtt import client as mqtt_client
import logging, time
import json
from tqdm import tqdm
import pandas as pd
import os

broker = 'localhost'
port = 1883
topic = "stream"
client_id = 'mqtt-publisher'
path = "logs/logs_noise/"

def connect_mqtt():
    def on_connect(client, userdata, flags, rc):
        if rc == 0:
            print("Connected to MQTT Broker!")
        else:
            print("Failed to connect, return code %d\n", rc)
    # Set Connecting Client ID
    client = mqtt_client.Client(client_id)
    # client.username_pw_set(username, password)
    client.on_connect = on_connect
    client.connect(broker, port)
    return client

def on_disconnect(client, userdata, rc):
    FIRST_RECONNECT_DELAY = 1
    RECONNECT_RATE = 2
    MAX_RECONNECT_COUNT = 12
    MAX_RECONNECT_DELAY = 60
    logging.info("Disconnected with result code: %s", rc)
    reconnect_count, reconnect_delay = 0, FIRST_RECONNECT_DELAY
    while reconnect_count < MAX_RECONNECT_COUNT:
        logging.info("Reconnecting in %d seconds...", reconnect_delay)
        time.sleep(reconnect_delay)

        try:
            client.reconnect()
            logging.info("Reconnected successfully!")
            return
        except Exception as err:
            logging.error("%s. Reconnect failed. Retrying...", err)

        reconnect_delay *= RECONNECT_RATE
        reconnect_delay = min(reconnect_delay, MAX_RECONNECT_DELAY)
        reconnect_count += 1
    logging.info("Reconnect failed after %s attempts. Exiting...", reconnect_count)


def read_logs(dataset):

    ooo_logs = {}
    for _f in os.listdir(os.path.join(path,dataset)):
        ooo_logs[_f][:-4]
    ooo_logs["00"] = pd.read_csv(os.path.join(path,dataset,dataset+".csv"), index_col=0)
    for k in prob_maxdist.keys():
        ooo_logs[k] = pd.read_csv(os.path.join(path,dataset,k+".csv"), index_col=0)
    
    return ooo_logs

def live_check():
    for i in range(5):
        res = client.publish(f"{topic}/test/test",json.dumps({"event":{"concept:name":"test","time:timestamp":"2024-01-01T01:01:01.179+01:00"},"trace":{"concept:name":"test"}}))
        if res[0]==0:
            time.sleep(2)
            break
        else:
            time.sleep(5)

def produce_events(event_log,swap_setting):
    counter = 0
    live_check()
    for r in tqdm(event_log.itertuples()):
        trace = r[1]
        activity = r[2]
        timestamp = str(r[3])
        payload = {"event":{"concept:name":activity,"time:timestamp":timestamp},"trace":{"concept:name":trace}}
        client.publish(f"{topic}/swap_{swap_setting}_{trace}/{activity}", json.dumps(payload),qos=0)
        counter += 1
        if counter > 50:
            time.sleep(50/1000)
            counter = 0


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="MQTT Producer")
    parser.add_argument("--swap_setting", type=str, required=True, help="Swap setting (00, 01, 05, 10, 20, 50, 99)")
    parser.add_argument("--dataset", type=str, required=True, help="Dataset name (bpi2012, bpi2017, bpi2020)")
    
    args = parser.parse_args()
    swap_setting = args.swap_setting
    dataset = args.dataset

    client = connect_mqtt()
    client.on_disconnect = on_disconnect
    ooo_logs = read_logs(dataset)
    event_log = ooo_logs[swap_setting]
    produce_events(event_log,swap_setting)