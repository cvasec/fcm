import { NativeEventEmitter, NativeModules } from 'react-native';

export type TtsSettings = {
  enabled: boolean;
  rate: number;
  pitch: number;
  language: string;
};

export type ReceivedMessage = {
  id: string;
  text: string;
  title: string;
  receivedAt: number;
};

type FcmTtsNative = {
  getToken(): Promise<string>;
  speak(text: string): Promise<void>;
  stop(): void;
  getSettings(): Promise<TtsSettings>;
  setSettings(settings: Partial<TtsSettings>): Promise<TtsSettings>;
  getHistory(): Promise<ReceivedMessage[]>;
  clearHistory(): Promise<void>;
};

const native: FcmTtsNative = NativeModules.FcmTts;
const emitter = new NativeEventEmitter<{
  FcmTtsMessage: [ReceivedMessage];
  FcmTtsToken: [string];
}>(NativeModules.FcmTts);

export const FcmTts = {
  ...native,
  onMessage(listener: (message: ReceivedMessage) => void) {
    return emitter.addListener('FcmTtsMessage', listener);
  },
  onTokenRefresh(listener: (token: string) => void) {
    return emitter.addListener('FcmTtsToken', listener);
  },
};
