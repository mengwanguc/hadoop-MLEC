import subprocess
import sys
import signal
import os
import threading

# ANSI color codes
COLORS = {
    'namenode': '\033[95m',  # Magenta
    'dn1': '\033[94m',       # Blue
    'dn2': '\033[92m',       # Green
    'dn3': '\033[93m',       # Yellow
    'reset': '\033[0m'       # Reset
}

# Commands to start the nodes
COMMANDS = {
    'namenode_format': ['hdfs', 'namenode', '-format', '-force'],
    'namenode': ['hdfs', 'namenode'],
    'dn1': ['hdfs', 'datanode'],
    'dn2': ['hdfs', 'datanode'],
    'dn3': ['hdfs', 'datanode']
}

# Environment variables for the nodes
ENV_VARS = {
    'namenode': {'HADOOP_PID_DIR': '/tmp/hdfs/namenode', 'HADOOP_CONF_DIR': '/home/aaronmao/hadoop-MLEC/dockers'},
    'dn1': {'HADOOP_PID_DIR': '/tmp/hdfs/dn1', 'HADOOP_CONF_DIR': '/home/aaronmao/hadoop-MLEC/dockers/dn1'},
    'dn2': {'HADOOP_PID_DIR': '/tmp/hdfs/dn2', 'HADOOP_CONF_DIR': '/home/aaronmao/hadoop-MLEC/dockers/dn2'},
    'dn3': {'HADOOP_PID_DIR': '/tmp/hdfs/dn3', 'HADOOP_CONF_DIR': '/home/aaronmao/hadoop-MLEC/dockers/dn3'}
}

processes = {}

def run_command(command, env=None):
    process = subprocess.Popen(
        command,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        env=env
    )
    output, _ = process.communicate()
    print(output.decode('utf-8'))
    return process.returncode

def start_node(node_name):
    env = os.environ.copy()
    if node_name in ENV_VARS:
        env.update(ENV_VARS[node_name])

    log_file_path = f'log-{node_name}.log'

    process = subprocess.Popen(
        COMMANDS[node_name],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        env=env
    )
    processes[node_name] = process

    def stream_output():
        with open(log_file_path, 'w') as log_file:
            for line in iter(process.stdout.readline, b''):
                sys.stdout.write(f'{COLORS[node_name]}[{node_name}] {COLORS["reset"]}{line.decode("utf-8")}')
                log_file.write(line.decode('utf-8'))

    thread = threading.Thread(target=stream_output)
    thread.start()

def shutdown_all(signum, frame):
    print("Shutting down all processes...")
    for process in processes.values():
        process.terminate()
    sys.exit(0)

if __name__ == "__main__":
    signal.signal(signal.SIGINT, shutdown_all)

    # Run refresh.sh script before starting any nodes
    if run_command(['./dockers/refresh_dir.sh']) != 0:
        print("refresh.sh script failed. Exiting.")
        sys.exit(1)

    # Run namenode format command with namenode environment variables
    namenode_env = os.environ.copy()
    if 'namenode' in ENV_VARS:
        namenode_env.update(ENV_VARS['namenode'])

    # Run namenode format command before starting namenode
    if run_command(COMMANDS['namenode_format'], env=namenode_env) != 0:
        print("Namenode format failed. Exiting.")
        sys.exit(1)

    start_node('namenode')
    start_node('dn1')
    start_node('dn2')
    start_node('dn3')

    # Wait for all processes to complete
    for process in processes.values():
        process.wait()
