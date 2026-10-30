import argparse
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile


def run_json(*command):
    return json.loads(subprocess.check_output(command, text=True))


def runtime_version(runtime):
    return tuple(int(part) for part in runtime["version"].split("."))


def select_simulator(existing_only):
    data = run_json("xcrun", "simctl", "list", "-j")
    runtimes = sorted(
        (
            runtime
            for runtime in data["runtimes"]
            if runtime["isAvailable"]
            and runtime["identifier"].startswith("com.apple.CoreSimulator.SimRuntime.iOS-")
        ),
        key=runtime_version,
        reverse=True,
    )
    if not runtimes:
        raise ValueError("No iOS runtime installed. Install iOS 26.2 or newer in Xcode > Settings > Components.")

    requested_id = os.environ.get("SIM_ID", "")
    requested_name = os.environ.get("SIM_NAME", "")
    for runtime in runtimes:
        devices = [
            device
            for device in data["devices"].get(runtime["identifier"], [])
            if device["isAvailable"]
        ]
        if requested_id:
            devices = [device for device in devices if device["udid"] == requested_id]
        elif requested_name:
            devices = [device for device in devices if device["name"] == requested_name]
        else:
            iphone_types = {
                device_type["identifier"]
                for device_type in data["devicetypes"]
                if device_type["productFamily"] == "iPhone"
            }
            devices = [device for device in devices if device["deviceTypeIdentifier"] in iphone_types]
        if devices:
            device = next((device for device in devices if device["state"] == "Booted"), devices[0])
            print(f"Simulator: {device['name']} ({runtime['name']})", file=sys.stderr)
            return device["udid"]

    if requested_id or existing_only:
        raise ValueError("No matching simulator found. Run scripts/run.sh devices to list available devices.")

    for runtime in runtimes:
        device_types = [
            device_type
            for device_type in runtime["supportedDeviceTypes"]
            if device_type["productFamily"] == "iPhone"
            and (not requested_name or device_type["name"] == requested_name)
        ]
        if not device_types:
            continue
        device_type = device_types[0]
        print(f"Creating simulator: {device_type['name']} ({runtime['name']})", file=sys.stderr)
        return subprocess.check_output(
            ["xcrun", "simctl", "create", device_type["name"], device_type["identifier"], runtime["identifier"]],
            text=True,
        ).strip()

    raise ValueError("No compatible iPhone simulator type found. Check SIM_NAME or install a newer iOS runtime.")


def select_device(selector):
    with tempfile.TemporaryDirectory(prefix="muxy-devices-") as directory:
        output = Path(directory) / "devices.json"
        subprocess.run(
            ["xcrun", "devicectl", "list", "devices", "--quiet", "--json-output", str(output)],
            check=True,
        )
        devices = json.loads(output.read_text())["result"]["devices"]

    devices = [
        device
        for device in devices
        if device["hardwareProperties"]["platform"] == "iOS"
        and device["hardwareProperties"]["reality"] == "physical"
        and device["connectionProperties"]["pairingState"] == "paired"
    ]
    if selector:
        devices = [
            device
            for device in devices
            if selector in (
                device["identifier"],
                device["hardwareProperties"]["udid"],
                device["deviceProperties"]["name"],
                *device["connectionProperties"].get("potentialHostnames", []),
            )
        ]
    if not devices:
        raise ValueError("No matching paired iPhone or iPad. Connect and unlock it, trust this Mac, and check Xcode > Window > Devices and Simulators.")

    device = devices[0] if len(devices) == 1 else prompt_for_device(devices)
    print(f"Device: {device['deviceProperties']['name']}", file=sys.stderr)
    return device["hardwareProperties"]["udid"]


def describe_device(device):
    hardware = device["hardwareProperties"]
    model = hardware.get("marketingName") or hardware.get("productType", "")
    os_version = device["deviceProperties"].get("osVersionNumber", "")
    return f"{device['deviceProperties']['name']} — {model}, iOS {os_version} ({hardware['udid']})"


def prompt_for_device(devices):
    if not sys.stdin.isatty():
        raise ValueError("Multiple devices found. Run scripts/run.sh devices, then scripts/run.sh device '<name or ID>'.")

    print("Multiple devices found:", file=sys.stderr)
    for index, device in enumerate(devices, start=1):
        print(f"  {index}) {describe_device(device)}", file=sys.stderr)

    while True:
        print(f"Select a device [1-{len(devices)}]: ", end="", file=sys.stderr, flush=True)
        answer = sys.stdin.readline()
        if not answer:
            raise ValueError("No device selected.")
        choice = answer.strip()
        if choice.isdigit() and 1 <= int(choice) <= len(devices):
            return devices[int(choice) - 1]
        print("Invalid selection.", file=sys.stderr)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("target", choices=("simulator", "device"))
    parser.add_argument("selector", nargs="?", default="")
    parser.add_argument("--existing", action="store_true")
    arguments = parser.parse_args()
    try:
        destination = (
            select_simulator(arguments.existing)
            if arguments.target == "simulator"
            else select_device(arguments.selector)
        )
    except (ValueError, subprocess.CalledProcessError) as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1
    except KeyboardInterrupt:
        print(file=sys.stderr)
        return 130
    print(destination)
    return 0


if __name__ == "__main__":
    sys.exit(main())
