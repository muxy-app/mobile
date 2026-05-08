import * as Device from 'expo-device';
import { Platform } from 'react-native';

export function resolveDeviceName(): string {
  const fromOS = Device.deviceName?.trim();
  if (fromOS) return fromOS;

  const model = Device.modelName?.trim();
  const os = Device.osName?.trim() ?? Platform.OS;
  if (model) return `${model} (${os})`;
  return `${os} device`;
}
