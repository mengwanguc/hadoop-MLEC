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
    'namenode': {'HADOOP_PID_DIR': '/tmp/hdfs/namenode', 'HADOOP_CONF_DIR': '/home/aaronmao/hadoop-MLEC/dockers', 'HADOOP_ROOT_LOGGER': 'DEBUG,stdout'},
    'dn1': {'HADOOP_PID_DIR': '/tmp/hdfs/dn1', 'HADOOP_CONF_DIR': '/home/aaronmao/hadoop-MLEC/dockers/dn1', 'HADOOP_ROOT_LOGGER': 'DEBUG,stdout'},
    'dn2': {'HADOOP_PID_DIR': '/tmp/hdfs/dn2', 'HADOOP_CONF_DIR': '/home/aaronmao/hadoop-MLEC/dockers/dn2', 'HADOOP_ROOT_LOGGER': 'INFO,stdout'},
    'dn3': {'HADOOP_PID_DIR': '/tmp/hdfs/dn3', 'HADOOP_CONF_DIR': '/home/aaronmao/hadoop-MLEC/dockers/dn3', 'HADOOP_ROOT_LOGGER': 'INFO,stdout'}
}

processes = {}

def run_command(command, env=None, cwd=None):
    process = subprocess.Popen(
        command,
        stderr=subprocess.STDOUT,
        env=env,
        cwd=cwd
    )
    output, _ = process.communicate()
    # print(output.decode('utf-8'))
    return process.returncode

def start_node(node_name):
    env = os.environ.copy()
    if node_name in ENV_VARS:
        env.update(ENV_VARS[node_name])

    log_file_path = f'log-{node_name}.log'

    print(env)
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

    # Ask the user whether to build the code before starting the nodes
    build_code = input("Do you want to build the code before starting the nodes? (y/n): ").strip().lower()

    if build_code == 'y':
        # Run Maven build commands before starting the nodes
        if run_command(['mvn', '-nsu', '-T', '24', 'clean', 'package', 'install', '-Pdist', '-DskipTests', '-Dtar', '-Dmaven.javadoc.skip=true'], cwd='hadoop-common-project') != 0:
            print("Maven build for hadoop-common-project failed. Exiting.")
            sys.exit(1)

        if run_command(['mvn', '-nsu', '-T', '24', 'clean', 'package', '-Pdist', '-DskipTests', '-Dtar', '-Dmaven.javadoc.skip=true'], cwd='hadoop-hdfs-project') != 0:
            print("Maven build for hadoop-hdfs-project failed. Exiting.")
            sys.exit(1)

        if run_command(['mvn', '-nsu', '-T', '24', 'clean', 'package', '-Pdist', '-DskipTests', '-Dtar', '-Dmaven.javadoc.skip=true'], cwd='hadoop-dist') != 0:
            print("Maven build for hadoop-dist failed. Exiting.")
            sys.exit(1)


        # Untar the newest Hadoop built file into /opt/hadoop
        if run_command(['tar', '-xzf', 'hadoop-dist/target/hadoop-3.5.0-SNAPSHOT.tar.gz', '-C', '/opt/hadoop']) != 0:
            print("Untar of Hadoop distribution failed. Exiting.")
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

    # Run seed test
    if run_command(COMMANDS['dockers/seed_test.sh test']) != 0:
        print("Seed test failed. Exiting.")
        sys.exit(1)

    # Wait for all processes to complete
    for process in processes.values():
        process.wait()
